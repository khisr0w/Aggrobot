package abid;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import ai.abstraction.AbstractionLayerAI;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.abstraction.pathfinding.PathFinding;
import ai.core.AI;
import ai.core.ParameterSpecification;
import rts.GameState;
import rts.PhysicalGameState;
import rts.PlayerAction;
import rts.Player;
import rts.units.Unit;
import rts.units.UnitType;
import rts.units.UnitTypeTable;

public class Aggrobot extends AbstractionLayerAI{
    public class Vector2D {
        public float x;
        public float y;

        public Vector2D() { x = 0.f; y = 0.f; }
        public Vector2D(int x, int y) { this.x = x; this.y = y; }
    }
    public class UnitDesc {
        Unit unit = null;
        Vector2D distVec = null;

        public UnitDesc() {
            distVec = new Vector2D();
        }
    }

    public UnitDesc getUniClosestEnemy(Unit leader, PhysicalGameState physicalGS, Player player) {
        UnitDesc unitDesc = new UnitDesc();

        for(Unit unit : physicalGS.getUnits()) {
            if(unit.getPlayer() >= 0 && unit.getPlayer() != player.getID()) {
                int unitDistX = Math.abs(leader.getX() - unit.getX());
                int unitDistY = Math.abs(leader.getY() - unit.getY());

                int dist = unitDistX + unitDistY;

                if(unitDesc.unit == null || dist < (unitDesc.distVec.x + unitDesc.distVec.y)) {
                    unitDesc.distVec.x = unitDistX;
                    unitDesc.distVec.y = unitDistY;
                    unitDesc.unit = unit;
                }
            }
        }

        return unitDesc;
    }

    public class Cluster {
        GameState gs;
        List<Unit> units = null;
        // TODO(Abid): Make directional movement
        int direction;
        String action = "wait";
        Unit target = null;
        Unit source = null;
        UnitType makeType = null;
        int posX = 0;
        int posY = 0;

        public Cluster() {
            units = new ArrayList<>();
        }

        public void moveC(GameState gs, int x, int y) {
            for (Unit unit : units) {
                if(gs.getActionAssignment(unit) == null) { moveToVicinity(gs, unit, x, y); }
            }
            action = "move";
            posX = x;
            posY = y;
        }

        // public boolean notInVicinity(GameState gs)
        // {
        //     float averageXPos = 0.0f;
        //     float averageyPos = 0.0f;
        //     for (Unit unit : units)
        //     {
        //         unit.getX();
        //     }

        //     return false;
        // }

        public Vector2D getCentroid() {
            Vector2D centroid = new Vector2D();
            
            for (Unit unit : units) {
                centroid.x = centroid.x + unit.getX();
                centroid.y = centroid.y + unit.getY();
            }

            centroid.x = centroid.x / units.size();
            centroid.y = centroid.y / units.size();

            return centroid;
        }

        public void moveCIfNotVicinity(GameState gs, int x, int y) {
            for (Unit unit : units) {
                if(gs.getActionAssignment(unit) == null) { moveToVicinity(gs, unit, x, y); }
            }
            action = "move";
            posX = x;
            posY = y;
        }

        public void trainC(GameState gs, UnitType unit_type) {
            for (Unit unit : units) {
                if(gs.getActionAssignment(unit) == null) {
                    train(unit, unit_type);
                }
            }
            action = "train";
            makeType = unit_type;
        }

        public void buildC(GameState gs, UnitType unit_type, int x, int y) {
            for (Unit unit : units) {
                if(gs.getActionAssignment(unit) == null) {
                    // Find the closest spot
                    build(unit, unit_type, x, y);
                }
            }
            action = "build";
            makeType = unit_type;
            posX = x;
            posY = y;
        }

        public void harvestC(GameState gs, Unit target, Unit base) {
            for (Unit unit : units) {
                if(gs.getActionAssignment(unit) == null) {
                    harvest(unit, target, base);
                }
            }
            action = "harvest";
            this.target = base;
            source = target;
        }

        public void attackC(GameState gs, Unit target) {
            for (Unit unit : units) {
                if(gs.getActionAssignment(unit) == null) {
                    attack(unit, target);
                }
            }
            action = "attack";
            this.target = target;
        }

        public void idleC(GameState gs) {
            for (Unit unit : units) {
                if(gs.getActionAssignment(unit) == null) {
                    idle(unit);
                }
            }
            action = "idle";
        }

        protected void moveToVicinity(GameState gs, Unit unit, int x, int y) {
            assert(gs.getActionAssignment(unit) == null);
            boolean[][] isGridFree = gs.getAllFree();

            if(isGridFree[x][y]) {
                move(unit, x, y);
                return;
            }

            int xAllowed = 0;
            if(x > gs.getPhysicalGameState().getWidth() - x) { xAllowed = x; }
            else { xAllowed = gs.getPhysicalGameState().getWidth() - x; }

            int yAllowed = 0;
            if(y > gs.getPhysicalGameState().getHeight() - y) { yAllowed = y; }
            else { yAllowed = gs.getPhysicalGameState().getHeight() - y; }

            int xAlternate = 1;
            int yAlternate = 1;
            for (int xStride = 1; xStride < xAllowed; xStride++) {
                for (int yStride = 1; yStride < yAllowed; yStride++) {
                    int potentialX = x + xStride*xAlternate;
                    int potentialY = y + yStride*yAlternate;
                    if(potentialX >= gs.getPhysicalGameState().getWidth() || potentialX < 0) { continue; }
                    if(potentialY >= gs.getPhysicalGameState().getHeight() || potentialY < 0) { continue; }

                    if (isGridFree[potentialX][potentialY]) {
                        move(unit, potentialX, potentialY);
                        return;
                    }
                    yAlternate = yAlternate*-1;
                }
                xAlternate = xAlternate*-1;
            }
        }

        protected void buildInVicinity(GameState gs, Player p, UnitType unitType) {
            if(this.size() == 0) return;

            Unit unit = this.get(0);
            // for (Unit unit2 : units)
            // {
            //     if(gs.getActionAssignment(unit2) == null) { unit = unit2; }
            // }

            // if(unit == null) { return; }

            Unit closestEnemy = this.getClosestEnemy(gs.getPhysicalGameState(), p).unit;
            boolean[][] isGridFree = gs.getAllFree();

            int x = unit.getX();
            int y = unit.getY();

            int xAllowed = 0;
            if(x > gs.getPhysicalGameState().getWidth() - x) { xAllowed = x; }
            else { xAllowed = gs.getPhysicalGameState().getWidth() - x; }

            int yAllowed = 0;
            if(y > gs.getPhysicalGameState().getHeight() - y) { yAllowed = y; }
            else { yAllowed = gs.getPhysicalGameState().getHeight() - y; }

            int xAlternate = 1;
            int yAlternate = 1;

            xAllowed = 2;
            yAllowed = 2;

            int prevDistanceToEnemy = gs.getPhysicalGameState().getHeight() + gs.getPhysicalGameState().getWidth();
            Vector2D selectedPos = new Vector2D(0, 0);;
            List<Integer> reservedPositions = new LinkedList<>();

            int xStride = 0;
            while(xStride < xAllowed) {
                int potentialX = x + xStride*xAlternate;
                if(potentialX < gs.getPhysicalGameState().getWidth() && potentialX >= 0) { 
                    int yStride = 0;
                    while(yStride < yAllowed) {
                        int potentialY = y + yStride*yAlternate;
                        if (potentialY < gs.getPhysicalGameState().getHeight() && potentialY >= 0 &&
                            isGridFree[potentialX][potentialY]) {
                            if(closestEnemy != null) {
                                int potentialDist = manhattanDist(potentialX, potentialY, closestEnemy.getX(),
                                        closestEnemy.getY());
                                if (potentialDist < prevDistanceToEnemy) {
                                    selectedPos.x = potentialX;
                                    selectedPos.y = potentialY;
                                    prevDistanceToEnemy = potentialDist;
                                } else if (potentialDist == prevDistanceToEnemy && potentialX >= x && potentialY >= y) {
                                    selectedPos.x = potentialX;
                                    selectedPos.y = potentialY;
                                    prevDistanceToEnemy = potentialDist;
                                }
                            } else {
                                // If no enemy then we are done
                                return;
                            }

                            // build(unit, unitType, potentialX, potentialY);
                            // action = "produce";
                            // makeType = unitType;
                            // return;
                        }
                        if(yAlternate == -1) yStride++;
                        yAlternate = yAlternate*-1;
                    }
                }

                if(xAlternate == -1) xStride++;
                xAlternate = xAlternate*-1;
            }

            buildIfNotAlreadyBuilding(unit, unitType, (int)selectedPos.x, (int)selectedPos.y,reservedPositions,p,gs.getPhysicalGameState());
        }

        public int size() { return units.size(); }

        public Unit get(int idx) { return units.get(idx); }

        public Unit remove(int idx) { return units.remove(idx); }

        public boolean isIdle() { return action.equals("wait"); }

        public List<Unit> getUnits() { return units; }

        public UnitDesc getClosestEnemy(PhysicalGameState physicalGS, Player player) {
            Unit leader = this.units.get(0);

            return Aggrobot.this.getUniClosestEnemy(leader, physicalGS, player);
        }

        public UnitDesc getClosestFightingAlly(PhysicalGameState physicalGS, Player player) {
            UnitDesc unitDesc = new UnitDesc();

            Unit leader = this.units.get(0);

            for(Unit unit : physicalGS.getUnits()) {
                if(unit.getPlayer() >= 0 && unit.getPlayer() == player.getID() &&
                   !(this.units.contains(leader)) && unit.getType().canAttack) {
                    int unitDistX = Math.abs(leader.getX() - unit.getX());
                    int unitDistY = Math.abs(leader.getY() - unit.getY());

                    int dist = unitDistX + unitDistY;

                    if(unitDesc.unit == null || dist < (unitDesc.distVec.x + unitDesc.distVec.y)) {
                        unitDesc.distVec.x = unitDistX;
                        unitDesc.distVec.y = unitDistY;
                        unitDesc.unit = unit;
                    }
                }
            }

            return unitDesc;
        }

        public boolean withinVicinity(Unit unit, int margin) {
            Vector2D centroid = getCentroid();

            int dist = (int)(Math.abs(centroid.x - unit.getX()) + Math.abs(centroid.y - unit.getY()));

            if(dist <= margin) return true;
            else return false;
        }

        public void updateMembers(GameState gs, Player player, List<Unit> workers, int numberNeeded) {
            for(int idx = 0; idx < units.size(); idx++) {
                Unit unit = units.get(idx);
                if (unit.getHitPoints() == 0) {
                    units.remove(unit);
                    idx--;
                }
            }
            int missingWorkers = numberNeeded - units.size();
            assert(missingWorkers >= 0);

            workers.removeIf(element -> units.contains(element));

            for (int idx = 0; idx < workers.size(); idx++) {
                if(missingWorkers > 0) {
                    units.add(workers.get(idx));
                    workers.remove(idx);
                    missingWorkers--;
                    idx--;
                }
            }
        }
    }

    UnitTypeTable m_utt = null;

    protected UnitType workerType;
    protected UnitType baseType;
    protected UnitType barracksType;
    protected UnitType lightType;
    protected UnitType rangedType;
    protected UnitType heavyType;

    int numAllyWorkers = 0;
    int numAllyLight = 0;
    int numAllyHeavy = 0;
    int numAllyRanged = 0;
    int numAllyBases = 0;
    int numAllyBarracks = 0;

    int numEnemyWorkers = 0;
    int numEnemyLight = 0;
    int numEnemyHeavy = 0;
    int numEnemyRanged = 0;

    int maxNumPawn = 4;
    int maxNumHome = 2;

    int maxBarracksNum = 2;

    int idlePosition = 0;

    Cluster pawnCluster = new Cluster();
    Cluster homeCluster = new Cluster();

    boolean canInitStrategize;
    boolean initialWaitForEnemy;

    public Aggrobot(UnitTypeTable utt) { this(utt, new AStarPathFinding()); }

    public Aggrobot(UnitTypeTable utt, PathFinding pf) {
        super(pf);
        reset(utt);
    }

    @Override
    public AI clone() {
        return new Aggrobot(m_utt, pf);
    }

    @Override
    public PlayerAction getAction(int p, GameState gameState) throws Exception {
        PhysicalGameState physicalGS = gameState.getPhysicalGameState();
        Player player = gameState.getPlayer(p);

        // Ideas going forward: - Make it so that if we have more than 3-4 lights, then send the heavy one towards the base of barracks
        //                      - In cases where once place do not have enough resources, it makes sense to send the workers elsewhere.

        // NOTE(Abid): Here how the AI works:
        //             - If we do not have a barracks, then build a barracks
        //             - TODO(Abid): FIX THIS: In between building units, if we get enough
        //                           resources for another barracks, then build the second one.
        //             - If we do not have enough homeCluster then create workers for it.
        //             - Once we have a barracks, then the following happens (TODO: Change this so that its more smart than this):
        //               - Build light units until we have 3 light units
        //               - Build heavy units until we have 2 light units
        //               - Build Ranged units for all the later stuff

        int prevNumAllyWorkers = numAllyWorkers;
        int prevNumAllyLight   = numAllyLight;
        int prevNumAllyHeavy   = numAllyHeavy ;
        int prevNumAllyRanged  = numAllyRanged;

        int prevNumEnemyWorkers = numEnemyWorkers;
        int prevNumEnemyLight   = numEnemyLight;
        int prevNumEnemyHeavy   = numEnemyHeavy;
        int prevNumEnemyRanged  = numEnemyRanged;

        numAllyWorkers = 0;
        numAllyLight = 0;
        numAllyHeavy = 0;
        numAllyRanged = 0;
        numAllyBases = 0;
        numAllyBarracks = 0;

        numEnemyWorkers = 0;
        numEnemyLight = 0;
        numEnemyHeavy = 0;
        numEnemyRanged = 0;

        Unit initialBase = null;

        List<Unit> workers = new ArrayList<>();
        for (Unit unit : physicalGS.getUnits()) {
            if(unit.getPlayer() == player.getID()) {
                // Player units
                if(unit.getType() == workerType) {
                    workers.add(unit);
                    numAllyWorkers++;
                } else if(unit.getType() == lightType)    ++numAllyLight;
                else if(unit.getType() == heavyType)    ++numAllyHeavy;
                else if(unit.getType() == rangedType)   ++numAllyRanged;
                else if(unit.getType() == baseType) {
                    if(initialBase == null) {
                        initialBase = unit;
                    }
                    ++numAllyBases;
                }
                else if(unit.getType() == barracksType) ++numAllyBarracks;
            } else if (unit.getPlayer() < 0) {
            } else {
                // Enemy units
                if(unit.getType() == workerType)      ++numEnemyWorkers;
                else if(unit.getType() == lightType)  ++numEnemyLight;
                else if(unit.getType() == heavyType)  ++numEnemyHeavy;
                else if(unit.getType() == rangedType) ++numEnemyRanged;
            }

            int newEnemyCount = prevNumEnemyHeavy+prevNumEnemyLight+prevNumEnemyRanged+prevNumEnemyWorkers;
            int oldEnemyCount = numEnemyHeavy+numEnemyLight+numEnemyRanged+numEnemyWorkers;

            int newAllyCount = prevNumAllyHeavy+prevNumAllyLight+prevNumAllyRanged+prevNumAllyWorkers;
            int oldAllyCount = numAllyHeavy+numAllyLight+numAllyRanged+numAllyWorkers;

            int denominator = newAllyCount-oldAllyCount;
            int numerator = newEnemyCount-oldEnemyCount;
            // if (denominator != 0) killRatio = numerator/denominator;
            // else killRatio = numerator;
            // System.out.println("Kill ratio: " + killRatio);
        }

        /* TODO(Abid): Check if the size of the map and the movement of the enemy will allow us to strategize
           For example, if we have enough pawns that are fighting and the train/kill ratio is above a certain threshold
           then we can go ahead and train a certain number of attack units.
        */

        // On game Init
        if(gameState.getTime() == 0) {
            initialWaitForEnemy = true;
            Unit candidateUnit = null;
            Unit initialEnemy = null;
            // Priority would be light units if present, otherwise workers.
            UnitType priorityEnemyType = workerType;
            int enemyDist = physicalGS.getHeight() + physicalGS.getWidth();
            for(Unit unit : physicalGS.getUnits()) {
                if(unit.getPlayer() >= 0  && unit.getPlayer() == player.getID()) {
                    candidateUnit = unit;
                    if(unit.getType().canAttack) {
                        for(Unit unit2 : physicalGS.getUnits()) {
                            if(unit2.getPlayer() >= 0 && unit2.getPlayer() != player.getID()) {
                                initialEnemy = unit2;
                                if(unit2.getType().canAttack) {
                                    int tempDist = manhattanDist(unit.getX(), unit.getY(), unit2.getX(), unit2.getY());
                                    if(tempDist < enemyDist) {
                                        enemyDist = tempDist;
                                    }
                                    if(unit2.getType() == lightType) {
                                        priorityEnemyType = lightType;
                                    }
                                }
                            }
                        }
                    }   
                }
            }

            int closestDistance = 0;
            Unit closestRes = null;

            if(candidateUnit != null) {
                for (Unit unit : physicalGS.getUnits()) {
                    if(unit.getPlayer() < 0 && unit.getType().isResource) {
                        int manhDist = manhattanDist(candidateUnit.getX(), candidateUnit.getY(), unit.getX(), unit.getY());
                        if(closestRes == null || closestDistance > manhDist) {
                            closestDistance = manhDist;
                            closestRes = unit;
                        }
                    }
                }
            }

            // Do we have time to strategize by the time the fastest units of enemy gets here?
            // Fastest determined by what type of units they have. They could have light or
            int timeToBuildLightUnit = ((numAllyBarracks == 0) ? barracksType.produceTime : 0) + lightType.produceTime;

            // Time needed for the light units to move some steps in case we do not have enough resources
            int paddingTime  = lightType.moveTime*2;
            if(player.getResources() < barracksType.cost+lightType.cost) {
                if(numAllyWorkers > 0) {
                    Unit leader = workers.get(0);

                    int closestResDist = 0;
                    Unit closestResUnit = null;

                    for (Unit unit : physicalGS.getUnits()) {
                        if (unit.getType().isResource) {
                            int dist = Math.abs(leader.getX() - unit.getX()) + Math.abs(leader.getY() - unit.getY());
                            if (closestResUnit == null || dist < closestResDist) {
                                closestResDist = dist;
                                closestResUnit = unit;
                            }
                        }
                    }

                    paddingTime = closestResDist*workerType.moveTime*2*(barracksType.cost+lightType.cost+1 - player.getResources());
                } else { paddingTime += baseType.cost + 2*workerType.cost; }
            }

            // If enemy will reach us before we can prepare, then we swarm them initially.
            if(timeToBuildLightUnit + paddingTime > enemyDist*priorityEnemyType.moveTime) {
                canInitStrategize = false;
                // Do we have time to make a second home cluster member
                if (workerType.produceTime*4 < enemyDist*priorityEnemyType.moveTime) {
                    maxNumHome = 2;
                } else maxNumHome = 1;
            } else {
                // In case we are strategizing, it is always better to have 2 home cluster member
                canInitStrategize = true;
                maxNumHome = 2;
                // if(closestRes.getResources() < 6*lightType.cost) maxNumHome = 2;
            }


            // Code to wait for another unit to come and then go with it.
            int timeToGatherRes = (player.getResources() >= lightType.cost) ? lightType.cost : lightType.cost+workerType.moveTime*6; // 6 is magic number here
            int dLight = (enemyDist*priorityEnemyType.moveTime - timeToBuildLightUnit - paddingTime - timeToGatherRes) / (4*lightType.moveTime);
            if(initialBase != null && initialEnemy != null) {
                idlePosition = initialBase.getX() + initialBase.getY() + dLight;
                if(initialBase.getX() + initialBase.getY() > initialEnemy.getX() + initialEnemy.getY()) {
                    idlePosition = (initialBase.getX()) - (physicalGS.getHeight() - initialBase.getY()) - dLight;
                }
            } else idlePosition = Math.min(physicalGS.getHeight(), physicalGS.getWidth())/4;
        }

        // See if we can strategize now, then let's do that
        if(canInitStrategize == false && gameState.getTime() > workerType.produceTime*2) {
            // System.out.println("Cluster: " + pawnCluster.size());
            if (pawnCluster.size() >= (numEnemyWorkers + numEnemyHeavy + numEnemyRanged + numEnemyLight) &&
                player.getResources() >= lightType.cost*1.5 && homeCluster.size() > 1) {
                canInitStrategize = true;
                initialWaitForEnemy = false;
            }
            // If we got of these by any change then we can go strategizing again
            else if (numAllyHeavy + numAllyLight >= 2) {
                canInitStrategize = true;
                initialWaitForEnemy = false;
            }
        }

        // Update the members
        if(canInitStrategize) {
            homeCluster.updateMembers(gameState, player, workers, maxNumHome);
            pawnCluster.units.clear();
            pawnCluster.updateMembers(gameState, player, workers, maxNumPawn);
        } else {
            int maxLen = Math.max(physicalGS.getHeight(), physicalGS.getWidth());
            // homeCluster.updateMembers(gameState, player, workers, ((gameState.getTime() > workerType.moveTime*3*maxLen/4 ? 2 : 1)));
            homeCluster.updateMembers(gameState, player, workers, maxNumHome);
            pawnCluster.updateMembers(gameState, player, workers, workers.size());
        }

        

        // Home Cluster
        homeClusterUpdate(gameState, player, canInitStrategize);

        // Bases
        basesUpdate(gameState, player, canInitStrategize);

        // Light
        updateLightUnits(gameState, player, idlePosition, 3);

        // Ranged
        updateRangedUnits(gameState, player);

        // Heavy
        updateHeavyUnits(gameState, player);

        // Pawn Cluster
        pawnClusterUpdate(gameState, player);

        // Barracks
        barracksUpdate(gameState, player, canInitStrategize);//canTrainLight);

        return translateActions(p, gameState);
    }

    public void basesUpdate(GameState gameState, Player player, boolean canStrategize) {
        PhysicalGameState physicalGS = gameState.getPhysicalGameState();

        for (Unit unit : physicalGS.getUnits()) {
            if(unit.getType() == baseType &&
               unit.getPlayer() == player.getID() &&
               gameState.getActionAssignment(unit) == null) {
                if(numAllyWorkers == 0 && player.getResources() >= workerType.cost) {
                    train(unit, workerType);
                    return;
                }
                // If we are strategizing, then only make your home cluster and be done with it.
                if(canStrategize) {
                    // We will not create the second home worker until we have a light unit, in case of strategy
                    if(homeCluster.size() > 0 && numAllyLight == 0) return;
                    if(homeCluster.size() < maxNumHome && player.getResources() >= workerType.cost) {
                        train(unit, workerType);
                    }
                }
                // We are just doing all the workers until we have enough in the field and then we make light units and more.
                else {
                    if (/* pawnCluster.size() < maxNumPawn && */ player.getResources() >= workerType.cost) {
                            train(unit, workerType);
                    }
                }
                
            }
        }
    }

    class UnitTypeWithCount {
        UnitType unitType = null;
        int maxCount = 0;
        int currentCount = 0;

        public UnitTypeWithCount(UnitType unitType, int maxCount, int currentCount) {
            this.unitType = unitType;
            this.maxCount = maxCount;
            this.currentCount = currentCount;
        }
    }

    public void barracksUpdate(GameState gameState, Player player, boolean isAllowedToTrain) {
        PhysicalGameState physicalGS = gameState.getPhysicalGameState();

        List<UnitTypeWithCount> priorityList = new ArrayList<>();
        priorityList.add(new UnitTypeWithCount(lightType, 4, numAllyLight));
        priorityList.add(new UnitTypeWithCount(heavyType, 2, numAllyHeavy));
        priorityList.add(new UnitTypeWithCount(rangedType, 3, numAllyRanged));

        for (Unit unit : physicalGS.getUnits()) {
            if (unit.getType() == barracksType &&
                unit.getPlayer() == player.getID() &&
                gameState.getActionAssignment(unit) == null) {
                for(int idx = 0; idx <priorityList.size(); idx++) {
                    UnitTypeWithCount unitTypeWithCount = priorityList.get(idx);
                    if(unitTypeWithCount.currentCount < unitTypeWithCount.maxCount) {
                        train(unit, unitTypeWithCount.unitType);
                        return;
                    }
                }
            }
        }
    }

    public void pawnClusterUpdate(GameState gameState, Player player) {
        PhysicalGameState physicalGS = gameState.getPhysicalGameState();

        if(pawnCluster.size() > 0) {
            // Keep moving if we are in safe zone, hoever, start attacking the moment the enemy reaches
            // half-way through.
            // Once we have, light and ranged units, attack!

            for(int idx = 0; idx < pawnCluster.size(); idx++) {
                Unit unit = pawnCluster.get(idx);
                UnitDesc closestEnemy = getUniClosestEnemy(unit, physicalGS, player);
                attack(unit, closestEnemy.unit);
            }
        }
    }

    public void homeClusterUpdate(GameState gameState, Player player, boolean canStrategize) {
        PhysicalGameState physicalGS = gameState.getPhysicalGameState();

        if(homeCluster.size() > 0)
        {
            if (numAllyBases == 0) {
                homeCluster.buildInVicinity(gameState, player, baseType);
                return;
            }
            // If we got no one, then might as well just go for it! Only if we have an enemy worker
            // if(numAllyHeavy+numAllyLight+numAllyRanged == 0 && numEnemyWorkers == 1 &&
            //    numEnemyHeavy+numEnemyLight+numAllyRanged == 0 && gameState.getPlayer(Math.abs(1-player.getID())).getResources() < 2
            //    && gameState.getPlayer(Math.abs(1-player.getID())).getResources() < 1)
            // {
            //     for(Unit unit : homeCluster.units)
            //     {
            //         Unit closestEnemy = getUniClosestEnemy(unit, physicalGS, player).unit;
            //         attack(unit, closestEnemy);
            //         return;
            //     }
            // }
            int closestResDist = 0;
            Unit closestResUnit = null;

            int closestStockDist = 0;
            Unit closestStockUnit = null;

            Unit leader = homeCluster.get(0);
            for (Unit unit : physicalGS.getUnits()) {
                if (unit.getType().isResource) {
                    int dist =  Math.abs(leader.getX() - unit.getX()) +
                                Math.abs(leader.getY() - unit.getY());
                    if(closestResUnit == null || dist < closestResDist) {
                        closestResDist = dist;
                        closestResUnit = unit;
                    }
                } else if(unit.getType().isStockpile && unit.getPlayer() == player.getID()) {
                    int dist =  Math.abs(leader.getX() - unit.getX()) +
                                Math.abs(leader.getY() - unit.getY());
                    if(closestStockUnit == null || dist < closestStockDist) {
                        closestStockDist = dist;
                        closestStockUnit = unit;
                    }
                }
            }
            // TODO(Abid): Check here that our closest ally isn't a base. Also check for when we don't have fighting ally in the beginning
            UnitDesc closestEnemy = homeCluster.getClosestEnemy(physicalGS, player);
            UnitDesc closestAlly = homeCluster.getClosestFightingAlly(physicalGS, player);

            // NOTE(Abid): If enemy is close
            if(closestEnemy.distVec.x + closestEnemy.distVec.y < closestAlly.distVec.x + closestAlly.distVec.y) {
                if(closestAlly.unit.getType() == workerType) {
                    // NOTE(Abid): Then we have pawn cluster around
                    for (Unit unit : pawnCluster.units) {
                        // if(gameState.getActionAssignment(unit) == null)
                        attack(unit, closestEnemy.unit);
                    }
                } else {
                    // NOTE(Abid): Singular units
                    attack(closestAlly.unit, closestEnemy.unit);
                }
            }
            if(closestResUnit != null && closestStockUnit != null) {
                // NOTE(Abid): If we have enough resources to make a barracks

                // TODO(Abid): In here we never get the opportunity to build another barracks because we keep using resources
                // It is nice, therefore, to have a better conditioning predicated on the amount of attack unit in deployment.
                // The code for this behavior should be inside the barracks, since they use the resources that we need.
                if(canStrategize && player.getResources() >= barracksType.cost && numAllyBarracks < maxBarracksNum) {
                    // NOTE(Abid): Build barracks
                    homeCluster.buildInVicinity(gameState, player, barracksType);
                }
                // NOTE(Abid): If we still got a free worker left, then gather resources
                else {
                    for (int idx = 0; idx < homeCluster.size(); idx++) {
                        Unit unit = homeCluster.units.get(idx);

                        int closestEnemyDist = manhattanDist((int)closestEnemy.distVec.x, (int)closestEnemy.distVec.y,
                                                             (int)closestAlly.distVec.x, (int)closestAlly.distVec.y);

                        if(idx == 0 && player.getResources() >= barracksType.cost + 5*workerType.cost)// &&
                           // closestEnemyDist*((numEnemyLight == 0) ? workerType.moveTime : lightType.moveTime) > barracksType.produceTime)
                        {
                            buildUnitInVicinity(unit, gameState, player, barracksType);
                        } else if (gameState.getActionAssignment(unit) == null) {
                            harvest(unit, closestResUnit, closestStockUnit);
                        }
                    }
                }
            } else if(closestResUnit == null) {
                if(pawnCluster.size() == 0) {
                    for (Unit unit : homeCluster.units) {
                        UnitDesc closestLocalEnemy = getUniClosestEnemy(unit, physicalGS, player);
                        attack(unit, closestLocalEnemy.unit);
                    }
                }
            } else if(closestStockUnit == null) {
                homeCluster.buildInVicinity(gameState, player, baseType);
            }
        }
    }
    
    public void updateLightUnits(GameState gameState, Player player, int idleDest, int attackDistance) {
        PhysicalGameState physicalGS = gameState.getPhysicalGameState();

        for (Unit unit : physicalGS.getUnits()) {
            if(unit.getType() == lightType &&
               unit.getPlayer() == player.getID() &&
               gameState.getActionAssignment(unit) == null) {
                // if(pawnCluster.size() > 0)
                // {
                //     if(pawnCluster.action.equals("move"))
                //     {
                //         move(unit, pawnCluster.posX, pawnCluster.posY);
                //     }
                //     else if(pawnCluster.action.equals("attack"))
                //     {
                //         if(pawnCluster.target != null && pawnCluster.target.getHitPoints() != 0)
                //         {
                //             attack(unit, pawnCluster.target);
                //         }
                //         else
                //         {
                //             UnitDesc closestEnemy = getUniClosestEnemy(unit, physicalGS, player);
                //             attack(unit, closestEnemy.unit);
                //         }
                //     }
                // }
                UnitDesc closestEnemy = getUniClosestEnemy(unit, physicalGS, player);
                if(initialWaitForEnemy && closestEnemy.distVec.x + closestEnemy.distVec.y > attackDistance) {
                    Vector2D avilPos = getAvailPosInVicinity(gameState, player, idleDest, idleDest);
                    move(unit, (int)avilPos.x, (int)avilPos.y);
                } else {
                    attack(unit, closestEnemy.unit);
                    initialWaitForEnemy = false;
                }
            }
        }

    }

    public void updateRangedUnits(GameState gameState, Player player) {
        PhysicalGameState physicalGS = gameState.getPhysicalGameState();
        for (Unit unit : physicalGS.getUnits()) {
            if(unit.getType() == rangedType &&
               unit.getPlayer() == player.getID() &&
               gameState.getActionAssignment(unit) == null) {
                // if(pawnCluster.size() > 0)
                // {
                //     if(pawnCluster.action.equals("move"))
                //     {
                //         move(unit, pawnCluster.posX, pawnCluster.posY);
                //     }
                //     else if(pawnCluster.action.equals("attack"))
                //     {
                //         if(pawnCluster.target != null && pawnCluster.target.getHitPoints() != 0)
                //         {
                //             attack(unit, pawnCluster.target);
                //         }
                //         else
                //         {
                //             UnitDesc closestEnemy = getUniClosestEnemy(unit, physicalGS, player);
                //             attack(unit, closestEnemy.unit);
                //         }
                //     }
                // }
                // else
                // {
                    UnitDesc closestEnemy = getUniClosestEnemy(unit, physicalGS, player);
                    attack(unit, closestEnemy.unit);
                // }
            }
        }
    }

    public void updateHeavyUnits(GameState gameState, Player player) {
        PhysicalGameState physicalGS = gameState.getPhysicalGameState();

        // Heavy
        for (Unit unit : physicalGS.getUnits()) {
            if(unit.getType() == heavyType &&
               unit.getPlayer() == player.getID() &&
               gameState.getActionAssignment(unit) == null) {
                // Make workers if conditions allow
                // if(pawnCluster.size() > 0)
                // {
                //     if(pawnCluster.action.equals("move"))
                //     {
                //         move(unit, pawnCluster.posX, pawnCluster.posY);
                //     }
                //     else if(pawnCluster.action.equals("attack"))
                //     {
                //         if(pawnCluster.target != null && pawnCluster.target.getHitPoints() != 0)
                //         {
                //             attack(unit, pawnCluster.target);
                //         }
                //         else
                //         {
                //             UnitDesc closestEnemy = getUniClosestEnemy(unit, physicalGS, player);
                //             attack(unit, closestEnemy.unit);
                //         }
                //     }
                // }
                // else
                // {
                    UnitDesc closestEnemy = getUniClosestEnemy(unit, physicalGS, player);
                    attack(unit, closestEnemy.unit);
                // }
            }
        }
    }


    public int manhattanDist(int unit1X, int unit1Y, int unit2X, int unit2Y) {
        return (int)(Math.abs(unit1X - unit2X) + Math.abs(unit1Y - unit2Y));
    }

    public void buildUnitInVicinity(Unit unit, GameState gs, Player p, UnitType unitType) {
        // for (Unit unit2 : units)
        // {
        //     if(gs.getActionAssignment(unit2) == null) { unit = unit2; }
        // }

        // if(unit == null) { return; }

        boolean[][] isGridFree = gs.getAllFree();

        int x = unit.getX();
        int y = unit.getY();

        int xAllowed = 0;
        if(x > gs.getPhysicalGameState().getWidth() - x) { xAllowed = x; }
        else { xAllowed = gs.getPhysicalGameState().getWidth() - x; }

        int yAllowed = 0;
        if(y > gs.getPhysicalGameState().getHeight() - y) { yAllowed = y; }
        else { yAllowed = gs.getPhysicalGameState().getHeight() - y; }

        int xAlternate = 1;
        int yAlternate = 1;
        List<Integer> reservedPositions = new LinkedList<>();
        for (int xStride = 2; xStride < xAllowed; xStride += 2) {
            for (int yStride = 1; yStride < yAllowed; yStride++) {
                int potentialX = x + xStride*xAlternate;
                int potentialY = y + yStride*yAlternate;
                if(potentialX >= gs.getPhysicalGameState().getWidth() || potentialX < 0) { continue; }
                if(potentialY >= gs.getPhysicalGameState().getHeight() || potentialY < 0) { continue; }

                if (isGridFree[potentialX][potentialY]) {
                    // build(unit, unitType, potentialX, potentialY);
                    buildIfNotAlreadyBuilding(unit, unitType, potentialX, potentialY,reservedPositions,p,gs.getPhysicalGameState());
                    return;
                }
                yAlternate = yAlternate*-1;
            }
            xAlternate = xAlternate*-1;
        }
    }

    protected Vector2D getAvailPosInVicinity(GameState gs, Player p, int x, int y) {
        boolean[][] isGridFree = gs.getAllFree();

        int xAllowed = 0;
        if(x > gs.getPhysicalGameState().getWidth() - x) { xAllowed = x; }
        else { xAllowed = gs.getPhysicalGameState().getWidth() - x; }

        int yAllowed = 0;
        if(y > gs.getPhysicalGameState().getHeight() - y) { yAllowed = y; }
        else { yAllowed = gs.getPhysicalGameState().getHeight() - y; }

        int xAlternate = 1;
        int yAlternate = 1;

        Vector2D selectedPos = new Vector2D(-1, -1);;

        int xStride = 0;
        while(xStride < xAllowed) {
            int potentialX = x + xStride*xAlternate;
            if(potentialX < gs.getPhysicalGameState().getWidth() && potentialX >= 0) { 
                int yStride = 0;
                while(yStride < yAllowed) {
                    int potentialY = y + yStride*yAlternate;
                    if (potentialY < gs.getPhysicalGameState().getHeight() && potentialY >= 0 &&
                        isGridFree[potentialX][potentialY]) {
                        selectedPos.x = potentialX; selectedPos.y = potentialY;
                        return selectedPos;
                    }
                    if(yAlternate == -1) yStride++;
                    yAlternate = yAlternate*-1;
                }
            }

            if(xAlternate == -1) xStride++;
            xAlternate = xAlternate*-1;
        }

        return selectedPos;
    }

    @Override
    public List<ParameterSpecification> getParameters() {
        return new ArrayList<>();
    }

    public void reset() { super.reset(); }

    @Override
    public void reset(UnitTypeTable utt) {
        m_utt = utt;
        workerType = utt.getUnitType("Worker");
        baseType = utt.getUnitType("Base");
        barracksType = utt.getUnitType("Barracks");
        lightType  = utt.getUnitType("Light");
        rangedType = utt.getUnitType("Ranged");
        heavyType  = utt.getUnitType("Heavy");

        pawnCluster = new Cluster();
        homeCluster = new Cluster();

        numAllyWorkers = 0;
        numAllyLight = 0;
        numAllyHeavy = 0;
        numAllyRanged = 0;
        numAllyBases = 0;
        numAllyBarracks = 0;

        numEnemyWorkers = 0;
        numEnemyLight = 0;
        numEnemyHeavy = 0;
        numEnemyRanged = 0;
        initialWaitForEnemy = true;
        idlePosition = 0;
    }

}
