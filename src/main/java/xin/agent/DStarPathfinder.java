package xin.agent;

import org.cloudburstmc.math.vector.Vector3i;
import org.joml.Vector3d;
import xin.bbtt.Block.BlockStateParser;
import xin.bbtt.MovementSync;

import java.util.*;

public class DStarPathfinder {

    private static class Node implements Comparable<Node> {
        Vector3i pos;
        Node parent;
        double gCost, hCost;

        Node(Vector3i pos, Node parent, double gCost, double hCost) {
            this.pos = pos;
            this.parent = parent;
            this.gCost = gCost;
            this.hCost = hCost;
        }

        double fCost() { return gCost + hCost; }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.fCost(), other.fCost());
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Node)) return false;
            return pos.equals(((Node) obj).pos);
        }

        @Override
        public int hashCode() {
            return pos.hashCode();
        }
    }

    public static List<Vector3d> findPath(Vector3d startF, Vector3d targetF, int maxRadius) {
        Vector3i start = Vector3i.from(Math.floor(startF.x), Math.floor(startF.y), Math.floor(startF.z));
        Vector3i target = Vector3i.from(Math.floor(targetF.x), Math.floor(targetF.y), Math.floor(targetF.z));

        PriorityQueue<Node> open = new PriorityQueue<>();
        Set<Vector3i> closed = new HashSet<>();
        Map<Vector3i, Node> allNodes = new HashMap<>();

        Node startNode = new Node(start, null, 0, start.distance(target));
        open.add(startNode);
        allNodes.put(start, startNode);

        int maxIterations = 5000;
        int iterations = 0;

        Vector3i[] directions = {
                Vector3i.from(1, 0, 0), Vector3i.from(-1, 0, 0),
                Vector3i.from(0, 0, 1), Vector3i.from(0, 0, -1),
                Vector3i.from(1, 0, 1), Vector3i.from(-1, 0, -1),
                Vector3i.from(1, 0, -1), Vector3i.from(-1, 0, 1),
                // Up/Down (Jump/Fall)
                Vector3i.from(1, 1, 0), Vector3i.from(-1, 1, 0),
                Vector3i.from(0, 1, 1), Vector3i.from(0, 1, -1),
                Vector3i.from(1, -1, 0), Vector3i.from(-1, -1, 0),
                Vector3i.from(0, -1, 1), Vector3i.from(0, -1, -1)
        };

        while (!open.isEmpty() && iterations++ < maxIterations) {
            Node current = open.poll();
            closed.add(current.pos);

            if (current.pos.distanceSquared(target) <= 2) { // Close enough
                return buildPath(current);
            }

            for (Vector3i dir : directions) {
                Vector3i neighborPos = current.pos.add(dir);
                
                if (start.distanceSquared(neighborPos) > maxRadius * maxRadius) continue;
                if (closed.contains(neighborPos)) continue;

                if (!isPassable(neighborPos)) continue;
                if (!isSafe(neighborPos)) continue;

                double tentativeGCost = current.gCost + current.pos.distance(neighborPos);
                // Extra penalty for jumping
                if (neighborPos.getY() > current.pos.getY()) tentativeGCost += 1.5; 

                Node neighbor = allNodes.getOrDefault(neighborPos, new Node(neighborPos, null, Double.MAX_VALUE, neighborPos.distance(target)));

                if (tentativeGCost < neighbor.gCost) {
                    neighbor.parent = current;
                    neighbor.gCost = tentativeGCost;

                    if (!open.contains(neighbor)) {
                        open.add(neighbor);
                        allNodes.put(neighborPos, neighbor);
                    } else {
                        open.remove(neighbor);
                        open.add(neighbor);
                    }
                }
            }
        }
        return null; // No path found
    }

    private static List<Vector3d> buildPath(Node node) {
        List<Vector3d> path = new ArrayList<>();
        while (node != null) {
            // center of the block
            path.add(0, new Vector3d(node.pos.getX() + 0.5, node.pos.getY(), node.pos.getZ() + 0.5));
            node = node.parent;
        }
        // remove start point to avoid walking in place
        if (path.size() > 1) path.remove(0); 
        return path;
    }

    private static boolean isPassable(Vector3i pos) {
        if (MovementSync.Instance == null || MovementSync.Instance.getWorld() == null) return false;
        int stateId = MovementSync.Instance.getWorld().getBlockAt(new Vector3d(pos.getX(), pos.getY(), pos.getZ()));
        String name = String.valueOf(BlockStateParser.Instance.parseStateId(stateId)).toLowerCase();
        
        return name.contains("air") || name.contains("water") || name.contains("sign") || name.contains("torch") || 
               name.contains("flower") || name.contains("grass") || name.contains("fern") || name.contains("mushroom");
    }

    private static boolean isSafe(Vector3i pos) {
        // block below must be solid, or block 2 below must be solid (falling 1 block)
        if (MovementSync.Instance == null || MovementSync.Instance.getWorld() == null) return false;
        
        // Also check if head is passable
        Vector3i headPos = pos.add(0, 1, 0);
        if (!isPassable(headPos)) return false;

        Vector3i below = pos.add(0, -1, 0);
        int stateIdBelow = MovementSync.Instance.getWorld().getBlockAt(new Vector3d(below.getX(), below.getY(), below.getZ()));
        String nameBelow = String.valueOf(BlockStateParser.Instance.parseStateId(stateIdBelow)).toLowerCase();

        boolean belowSolid = !nameBelow.contains("air") && !nameBelow.contains("water") && !nameBelow.contains("lava") && !nameBelow.contains("fire");
        
        if (belowSolid) return true;

        Vector3i below2 = pos.add(0, -2, 0);
        int stateIdBelow2 = MovementSync.Instance.getWorld().getBlockAt(new Vector3d(below2.getX(), below2.getY(), below2.getZ()));
        String nameBelow2 = String.valueOf(BlockStateParser.Instance.parseStateId(stateIdBelow2)).toLowerCase();
        boolean below2Solid = !nameBelow2.contains("air") && !nameBelow2.contains("water") && !nameBelow2.contains("lava") && !nameBelow2.contains("fire");

        return below2Solid;
    }
}
