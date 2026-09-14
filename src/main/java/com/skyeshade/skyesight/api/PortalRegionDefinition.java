package com.skyeshade.skyesight.api;

import com.skyeshade.skyesight.portal.PortalTraversalMath;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import java.util.Objects;

/** Large planar surface; endpoint frames specify the same canonical through-portal transform as finite portals. */
public record PortalRegionDefinition(ResourceLocation id, PortalRegionFrame source, PortalRegionFrame destination,
        PortalRegionShape shape, PortalSidedness sidedness, boolean bidirectional,
        double activationDistance, int renderRadiusChunks, int entityRadiusChunks, int simulationRadiusChunks,
        PortalBehavior behavior, PortalRegionWindowSettings windowSettings) {
    public PortalRegionDefinition(ResourceLocation id, PortalRegionFrame source, PortalRegionFrame destination,
            PortalRegionShape shape, PortalSidedness sidedness, boolean bidirectional,
            double activationDistance, int renderRadiusChunks, int entityRadiusChunks, int simulationRadiusChunks) {
        this(id,source,destination,shape,sidedness,bidirectional,activationDistance,renderRadiusChunks,entityRadiusChunks,
                simulationRadiusChunks,PortalBehavior.VISUAL_ONLY,PortalRegionWindowSettings.DEFAULT);
    }
    public PortalRegionDefinition(ResourceLocation id, PortalRegionFrame source, PortalRegionFrame destination,
            PortalRegionShape shape, PortalSidedness sidedness, boolean bidirectional,
            double activationDistance, int renderRadiusChunks, int entityRadiusChunks) {
        this(id,source,destination,shape,sidedness,bidirectional,activationDistance,renderRadiusChunks,entityRadiusChunks,0);
    }
    public PortalRegionDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(source); Objects.requireNonNull(destination);
        Objects.requireNonNull(shape); Objects.requireNonNull(sidedness);
        Objects.requireNonNull(behavior); Objects.requireNonNull(windowSettings);
        if (!Double.isFinite(activationDistance) || activationDistance <= 0 || activationDistance > 256
                || renderRadiusChunks < 1 || renderRadiusChunks > 16 || entityRadiusChunks < 0
                || entityRadiusChunks > renderRadiusChunks || simulationRadiusChunks < 0
                || simulationRadiusChunks > entityRadiusChunks) throw new IllegalArgumentException("Invalid local region radii");
        if(renderRadiusChunks<windowSettings.minimumChunkRadius()) throw new IllegalArgumentException("Terrain radius must contain the warm window");
        if(renderRadiusChunks<projectedRadius(destination,windowSettings) || bidirectional && renderRadiusChunks<projectedRadius(source,windowSettings))
            throw new IllegalArgumentException("Terrain radius must contain the oriented destination warm window");
    }
    public Quaternionf mappingRotation() { return PortalTraversalMath.rotation(source.rotation(),destination.rotation()); }
    public Vec3 mapPosition(Vec3 p) { return destination.origin().add(mapVelocity(p.subtract(source.origin()))); }
    public Vec3 mapVelocity(Vec3 v) { return PortalTraversalMath.rotate(v,mappingRotation()); }
    public Vec3 inversePosition(Vec3 p) {
        return source.origin().add(PortalTraversalMath.rotate(p.subtract(destination.origin()),mappingRotation().conjugate()));
    }
    /** Intersection query, independent of aperture size and upright player dimensions. Null means no crossing. */
    public Vec3 crossing(Vec3 previous, Vec3 current) {
        var a = source.local(previous); var b = source.local(current);
        if (Math.abs(a.z) < 1e-8 || a.z*b.z > 0 || !sidedness.allows(a.z)) return null;
        var hit = a.lerp(b,a.z/(a.z-b.z));
        return shape.contains(hit.x,hit.y) ? source.world(hit.x,hit.y,0) : null;
    }
    /** Source UV -> destination UV is (-U,V), due to the canonical half-turn. */
    public PortalRegionDefinition reversed() {
        var reversedShape = new PortalRegionShape(shape.strips().stream()
                .map(s -> new PortalRegionShape.Strip(-s.maxU(),s.minV(),-s.minU(),s.maxV())).toList());
        return new PortalRegionDefinition(id,destination,source,reversedShape,sidedness,bidirectional,
                activationDistance,renderRadiusChunks,entityRadiusChunks,simulationRadiusChunks,behavior,windowSettings);
    }
    public PortalRegionDefinition withBehavior(PortalBehavior value) {
        return new PortalRegionDefinition(id,source,destination,shape,sidedness,bidirectional,activationDistance,
                renderRadiusChunks,entityRadiusChunks,simulationRadiusChunks,value,windowSettings);
    }
    public PortalRegionDefinition withWindowSettings(PortalRegionWindowSettings value) {
        return new PortalRegionDefinition(id,source,destination,shape,sidedness,bidirectional,activationDistance,
                renderRadiusChunks,entityRadiusChunks,simulationRadiusChunks,behavior,value);
    }
    public boolean containsWorld(Vec3 point) { var p=source.local(point);return shape.contains(p.x,p.y); }
    private static int projectedRadius(PortalRegionFrame frame,PortalRegionWindowSettings settings) {
        var u=frame.tangentU();var v=frame.tangentV();double half=settings.visibleSize()*.5+settings.warmMargin();
        return (int)Math.ceil(half*Math.max(Math.abs(u.x)+Math.abs(v.x),Math.abs(u.z)+Math.abs(v.z))/16)+1;
    }
}
