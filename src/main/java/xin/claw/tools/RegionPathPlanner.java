package xin.claw.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import xin.bbtt.Block.BlockState;
import xin.bbtt.pathfinding.Node;

final class RegionPathPlanner {
    static final long MAX_VOLUME = 262_144L;

    record Bounds(
        int minX,
        int minY,
        int minZ,
        int maxXExclusive,
        int maxYExclusive,
        int maxZExclusive
    ) {
        static Bounds fromArrays(int[] min, int[] maxExclusive) {
            if (min == null || maxExclusive == null || min.length != 3 || maxExclusive.length != 3) {
                throw new IllegalArgumentException("min 和 max_exclusive 必须各包含3个整数");
            }
            return new Bounds(
                min[0], min[1], min[2],
                maxExclusive[0], maxExclusive[1], maxExclusive[2]
            );
        }

        Bounds {
            long sizeX = (long) maxXExclusive - minX;
            long sizeY = (long) maxYExclusive - minY;
            long sizeZ = (long) maxZExclusive - minZ;
            if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
                throw new IllegalArgumentException("each maxExclusive must be greater than min");
            }
            if (sizeX > MAX_VOLUME || sizeY > MAX_VOLUME || sizeZ > MAX_VOLUME
                || sizeX * sizeY > MAX_VOLUME
                || sizeX * sizeY * sizeZ > MAX_VOLUME) {
                throw new IllegalArgumentException("bounds volume must not exceed 262144 blocks");
            }
        }
    }

    record Result(Node target, int pathLength, int standableCandidates, int probedCandidates) {}

    @FunctionalInterface
    interface BlockLookup {
        BlockState stateAt(int x, int y, int z);
    }

    @FunctionalInterface
    interface PathProbe {
        int pathLength(Node start, Node goal);
    }

    private record Candidate(Node node, long squaredDistance) {}

    private RegionPathPlanner() {}

    static Optional<Result> findNearestReachable(
        Node start,
        Bounds bounds,
        BlockLookup blocks,
        PathProbe paths,
        int maxCandidatesToProbe
    ) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(paths, "paths");

        List<Candidate> candidates = new ArrayList<>();
        for (int x = bounds.minX(); x < bounds.maxXExclusive(); x++) {
            for (int y = bounds.minY(); y < bounds.maxYExclusive(); y++) {
                for (int z = bounds.minZ(); z < bounds.maxZExclusive(); z++) {
                    BlockState feet = blocks.stateAt(x, y, z);
                    BlockState head = blocks.stateAt(x, y + 1, z);
                    BlockState ground = blocks.stateAt(x, y - 1, z);
                    if (!isStandable(feet, head, ground)) continue;
                    long dx = (long) x - start.x;
                    long dy = (long) y - start.y;
                    long dz = (long) z - start.z;
                    candidates.add(new Candidate(new Node(x, y, z), dx * dx + dy * dy + dz * dz));
                }
            }
        }
        candidates.sort(Comparator.comparingLong(Candidate::squaredDistance));

        int probeLimit = Math.max(1, Math.min(512, maxCandidatesToProbe));
        int probed = 0;
        for (Candidate candidate : candidates) {
            if (probed >= probeLimit) break;
            probed++;
            int pathLength = paths.pathLength(start, candidate.node());
            if (pathLength > 0) {
                return Optional.of(new Result(candidate.node(), pathLength, candidates.size(), probed));
            }
        }
        return Optional.empty();
    }

    private static boolean isStandable(BlockState feet, BlockState head, BlockState ground) {
        return feet != null
            && head != null
            && ground != null
            && feet.isPassable()
            && !feet.isLiquid()
            && head.isPassable()
            && !head.isLiquid()
            && ground.isSolid();
    }
}
