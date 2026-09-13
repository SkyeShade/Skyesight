package com.skyeshade.skyesight.portal;

import com.skyeshade.skyesight.api.PortalAperture;
import com.skyeshade.skyesight.api.PortalEndpoint;
import com.skyeshade.skyesight.api.SkyesightPortalRaycast;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;

/**
 * Exact AABB mapping for upright cardinal portals. Other rotations retain vanilla collision.
 */
public final class PortalCollisionMath {
    private PortalCollisionMath() {
    }

    public static boolean supported(PortalEndpoint endpoint) {
        Vec3 n = PortalTraversalMath.normal(endpoint);
        Vec3 up = PortalTraversalMath.rotate(new Vec3(0, 1, 0), endpoint.rotation());
        return Math.abs(up.y - 1) < 1e-6 && Math.abs(n.y) < 1e-6 && Math.max(Math.abs(n.x), Math.abs(n.z)) > 1 - 1e-6;
    }

    public static AABB map(AABB box, Function<Vec3, Vec3> transform) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX, maxX = -minX, maxY = -minX, maxZ = -minX;
        for (double x : new double[]{box.minX, box.maxX})
            for (double y : new double[]{box.minY, box.maxY})
                for (double z : new double[]{box.minZ, box.maxZ}) {
                    Vec3 p = transform.apply(new Vec3(x, y, z));
                    minX = Math.min(minX, p.x);
                    minY = Math.min(minY, p.y);
                    minZ = Math.min(minZ, p.z);
                    maxX = Math.max(maxX, p.x);
                    maxY = Math.max(maxY, p.y);
                    maxZ = Math.max(maxZ, p.z);
                }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static AABB transform(AABB box, PortalEndpoint source, PortalEndpoint target) {
        return map(box, p -> PortalTraversalMath.position(source, target, p));
    }

    public static VoxelShape transform(VoxelShape shape, PortalEndpoint source, PortalEndpoint target) {
        VoxelShape result = Shapes.empty();
        for (AABB box : shape.toAabbs())
            result = Shapes.joinUnoptimized(result, Shapes.create(transform(box, source, target)), BooleanOp.OR);
        return result;
    }

    /**
     * Only the far half of the actual rectangle is removed; frame and approach-side shapes remain.
     */
    public static VoxelShape apertureHalf(PortalEndpoint source, int side, double depth) {
        AABB local = new AABB(-source.width() / 2, -source.height() / 2, side > 0 ? -depth : 0,
                source.width() / 2, source.height() / 2, side > 0 ? 0 : depth);
        return Shapes.create(map(local, p -> source.center().add(PortalTraversalMath.rotate(p, source.rotation()))));
    }
    public static VoxelShape apertureHalf(SkyesightPortalRaycast.Link link, int side, double depth) {
        if (!(link.aperture() instanceof PortalAperture.Bound bound)) return apertureHalf(link.source(),side,depth);
        var source=link.source(); var rows=bound.shape().rows(); int columns=rows.getFirst().length();
        VoxelShape result=Shapes.empty(); double w=source.width(),h=source.height();
        for(int y=0;y<rows.size();y++) for(int x=0;x<columns;x++) {
            if(rows.get(y).charAt(x)!='#') continue;
            var local=new AABB(-w/2+x*w/columns,h/2-(y+1)*h/rows.size(),side>0?-depth:0,
                    -w/2+(x+1)*w/columns,h/2-y*h/rows.size(),side>0?0:depth);
            result=Shapes.joinUnoptimized(result,Shapes.create(map(local,p -> source.center().add(PortalTraversalMath.rotate(p,source.rotation())))),BooleanOp.OR);
        }
        return result.optimize();
    }
}
