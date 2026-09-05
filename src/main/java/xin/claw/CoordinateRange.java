package xin.claw;

import java.util.Optional;

/** Inclusive 3D coordinate range used to protect locations from public disclosure. */
public record CoordinateRange(
        double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ) {

    public CoordinateRange {
        if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)) {
            throw new IllegalArgumentException("coordinate range values must be finite");
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("coordinate range minimums must not exceed maximums");
        }
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    public boolean containsHorizontal(double x, double z) {
        return x >= minX && x <= maxX
            && z >= minZ && z <= maxZ;
    }

    public static Optional<CoordinateRange> parse(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String[] parts = value.trim().split("\\s*,\\s*");
        if (parts.length != 6) {
            throw new IllegalArgumentException(
                "expected minX,minY,minZ,maxX,maxY,maxZ");
        }
        try {
            return Optional.of(new CoordinateRange(
                Double.parseDouble(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]),
                Double.parseDouble(parts[4]),
                Double.parseDouble(parts[5])
            ));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("coordinate range contains a non-number", error);
        }
    }
}
