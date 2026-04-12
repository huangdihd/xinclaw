/*
 *   Copyright (C) 2026 huangdihd
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package xin.agent.pathfinding;

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
        Vector3i start = Vector3i.from((int)Math.floor(startF.x), (int)Math.floor(startF.y), (int)Math.floor(startF.z));
        Vector3i target = Vector3i.from((int)Math.floor(targetF.x), (int)Math.floor(targetF.y), (int)Math.floor(targetF.z));

        PriorityQueue<Node> open = new PriorityQueue<>();
        Set<Vector3i> closed = new HashSet<>();
        Map<Vector3i, Node> allNodes = new HashMap<>();

        Node startNode = new Node(start, null, 0, start.distance(target));
        open.add(startNode);
        allNodes.put(start, startNode);

        Node bestNode = startNode; // 记录离目标最近的节点，用于保底返回
        int maxIterations = 2000; // 性能平衡：限制迭代次数防止卡死
        int iterations = 0;

        Vector3i[] directions = {
                Vector3i.from(1, 0, 0), Vector3i.from(-1, 0, 0),
                Vector3i.from(0, 0, 1), Vector3i.from(0, 0, -1),
                Vector3i.from(1, 0, 1), Vector3i.from(-1, 0, -1),
                Vector3i.from(1, 0, -1), Vector3i.from(-1, 0, 1),
                // 垂直移动支持
                Vector3i.from(1, 1, 0), Vector3i.from(-1, 1, 0),
                Vector3i.from(0, 1, 1), Vector3i.from(0, 1, -1),
                Vector3i.from(1, -1, 0), Vector3i.from(-1, -1, 0),
                Vector3i.from(0, -1, 1), Vector3i.from(0, -1, -1)
        };

        while (!open.isEmpty() && iterations++ < maxIterations) {
            Node current = open.poll();
            closed.add(current.pos);

            if (current.hCost < bestNode.hCost) {
                bestNode = current;
            }

            if (current.pos.distanceSquared(target) <= 2) {
                return buildPath(current);
            }

            for (Vector3i dir : directions) {
                Vector3i neighborPos = current.pos.add(dir);
                
                if (start.distanceSquared(neighborPos) > maxRadius * maxRadius) continue;
                if (closed.contains(neighborPos)) continue;

                if (!isPassable(neighborPos)) continue;
                if (!isSafe(neighborPos)) continue;

                double tentativeGCost = current.gCost + current.pos.distance(neighborPos);
                if (neighborPos.getY() > current.pos.getY()) tentativeGCost += 1.0; // 上坡代价

                Node neighbor = allNodes.get(neighborPos);
                if (neighbor == null) {
                    neighbor = new Node(neighborPos, current, tentativeGCost, neighborPos.distance(target));
                    allNodes.put(neighborPos, neighbor);
                    open.add(neighbor);
                } else if (tentativeGCost < neighbor.gCost) {
                    neighbor.parent = current;
                    neighbor.gCost = tentativeGCost;
                    open.remove(neighbor);
                    open.add(neighbor);
                }
            }
        }
        
        // 如果找不到完整路径，返回通往“离目标最近点”的路径，避免原地不动
        return bestNode != startNode ? buildPath(bestNode) : null;
    }

    private static List<Vector3d> buildPath(Node node) {
        List<Vector3d> path = new ArrayList<>();
        while (node != null) {
            path.add(0, new Vector3d(node.pos.getX() + 0.5, node.pos.getY(), node.pos.getZ() + 0.5));
            node = node.parent;
        }
        if (path.size() > 1) path.remove(0); 
        return path;
    }

    private static boolean isPassable(Vector3i pos) {
        if (MovementSync.Instance == null || MovementSync.Instance.getWorld() == null) return false;
        int stateId = MovementSync.Instance.getWorld().getBlockAt(new Vector3d(pos.getX(), pos.getY(), pos.getZ()));
        String name = String.valueOf(BlockStateParser.Instance.parseStateId(stateId)).toLowerCase();
        
        return name.contains("air") || name.contains("water") || name.contains("sign") || name.contains("torch") || 
               name.contains("flower") || name.contains("grass") || name.contains("fern") || name.contains("mushroom") ||
               name.contains("button") || name.contains("pressure_plate");
    }

    private static boolean isSafe(Vector3i pos) {
        if (MovementSync.Instance == null || MovementSync.Instance.getWorld() == null) return false;
        
        // 头顶空间必须也是空的
        Vector3i headPos = pos.add(0, 1, 0);
        if (!isPassable(headPos)) return false;

        // 脚下必须有支撑（或者是落差在2格以内）
        Vector3i below = pos.add(0, -1, 0);
        int sid1 = MovementSync.Instance.getWorld().getBlockAt(new Vector3d(below.getX(), below.getY(), below.getZ()));
        String n1 = String.valueOf(BlockStateParser.Instance.parseStateId(sid1)).toLowerCase();
        boolean s1 = !n1.contains("air") && !n1.contains("water") && !n1.contains("lava");
        if (s1) return true;

        Vector3i below2 = pos.add(0, -2, 0);
        int sid2 = MovementSync.Instance.getWorld().getBlockAt(new Vector3d(below2.getX(), below2.getY(), below2.getZ()));
        String n2 = String.valueOf(BlockStateParser.Instance.parseStateId(sid2)).toLowerCase();
        return !n2.contains("air") && !n2.contains("water");
    }
}
