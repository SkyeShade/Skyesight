package com.skyeshade.skyesight.api;

import com.skyeshade.skyesight.portal.PortalCollisionMath;
import com.skyeshade.skyesight.portal.PortalTraversalMath;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.HashMap;
import java.util.List;

/**
 * Immutable binary grid. Rows run top to bottom; columns run source-local left to right.
 * '#' is open, '.' is closed. Adjacent open cells have no internal collision boundary.
 */
public record PortalAperture(List<String> rows) {
    public record Bound(PortalAperture shape, PortalEndpoint endpoint) implements Predicate<Vec3> {
        @Override
        public boolean test(Vec3 local) {
            return shape.contains(endpoint, local);
        }
    }

    public Bound bind(PortalEndpoint endpoint) {
        return new Bound(this, endpoint);
    }

    /**
     * Retains the established frameless rectangular player crossing policy; grids require full body fit.
     */
    public boolean fitsPlayer(PortalEndpoint endpoint, Vec3 feet, double width, double height) {
        if (rows.stream().noneMatch(row -> row.contains("."))) {
            var local = PortalTraversalMath.local(endpoint, feet);
            return Math.abs(local.x) < endpoint.width() / 2 && local.y < endpoint.height() / 2 && local.y + height > -endpoint.height() / 2;
        }
        return fits(endpoint, new AABB(feet.x - width / 2, feet.y, feet.z - width / 2, feet.x + width / 2, feet.y + height, feet.z + width / 2));
    }

    public static boolean fits(SkyesightPortalRaycast.Link link, AABB bounds) {
        return link.aperture() instanceof Bound b ? b.shape.fits(link.source(), bounds)
                : PortalTraversalMath.fitsBody(link.source(), bounds);
    }

    public static final PortalAperture RECTANGLE = new PortalAperture(List.of("#"));

    public PortalAperture {
        rows = List.copyOf(rows);
        if (rows.isEmpty() || rows.size() > 64 || rows.getFirst().isEmpty() || rows.getFirst().length() > 64)
            throw new IllegalArgumentException("Aperture must be a 1..64 by 1..64 grid");
        int width = rows.getFirst().length();
        if (rows.stream().anyMatch(r -> r.length() != width || !r.matches("[.#]+"))
                || rows.stream().noneMatch(r -> r.contains("#")))
            throw new IllegalArgumentException("Aperture requires equal rows of '#' and '.', with an open cell");
    }

    public static PortalAperture grid(String... rows) {
        return new PortalAperture(List.of(rows));
    }

    /**
     * Nonoverlapping open rectangles in normalized grid coordinates (top-left origin).
     */
    public record Rectangle(double left, double top, double right, double bottom) {
    }

    public List<Rectangle> rectangles() {
        int width = rows.getFirst().length(), height = rows.size();
        var result = new ArrayList<Rectangle>();
        var previous = new HashMap<Long, Integer>();
        for (int y = 0; y < height; y++) {
            var current = new HashMap<Long, Integer>();
            for (int x = 0; x < width; ) {
                if (rows.get(y).charAt(x) != '#') {
                    x++;
                    continue;
                }
                int start = x;
                while (x < width && rows.get(y).charAt(x) == '#') x++;
                long key = ((long) start << 32) | x;
                Integer index = previous.get(key);
                if (index == null) {
                    index = result.size();
                    result.add(new Rectangle((double) start / width, (double) y / height, (double) x / width, (double) (y + 1) / height));
                } else {
                    var r = result.get(index);
                    result.set(index, new Rectangle(r.left, r.top, r.right, (double) (y + 1) / height));
                }
                current.put(key, index);
            }
            previous = current;
        }
        return List.copyOf(result);
    }

    public boolean contains(PortalEndpoint endpoint, Vec3 local) {
        double u = local.x / endpoint.width() + .5, v = .5 - local.y / endpoint.height();
        return u >= 0 && u < 1 && v >= 0 && v < 1
                && rows.get((int) (v * rows.size())).charAt((int) (u * rows.getFirst().length())) == '#';
    }

    /**
     * Exact coverage of the projected AABB, including closed cells entirely inside it.
     */
    public boolean fits(PortalEndpoint endpoint, AABB worldBounds) {
        AABB local = PortalCollisionMath.map(worldBounds, p -> PortalTraversalMath.local(endpoint, p));
        double w = endpoint.width(), h = endpoint.height();
        if (local.minX < -w / 2 - 1e-6 || local.maxX > w / 2 + 1e-6 || local.minY < -h / 2 - 1e-6 || local.maxY > h / 2 + 1e-6)
            return false;
        int columns = rows.getFirst().length();
        for (int y = 0; y < rows.size(); y++)
            for (int x = 0; x < columns; x++) {
                if (rows.get(y).charAt(x) == '#') continue;
                double left = -w / 2 + x * w / columns, top = h / 2 - y * h / rows.size();
                if (local.maxX > left + 1e-6 && local.minX < left + w / columns - 1e-6
                        && local.maxY > top - h / rows.size() + 1e-6 && local.minY < top - 1e-6) return false;
            }
        return true;
    }
}
