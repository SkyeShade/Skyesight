package com.skyeshade.skyesight.portal;

import com.skyeshade.skyesight.api.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Smooth visible clipping rectangle and independent, hysteretic warm-cell ownership. */
public record PortalRegionWindow(double centerU, double centerV, int visibleSize,
        List<PortalAperture.Rectangle> rectangles, int warmU, int warmV, Set<Long> warmCells, int activeCells) {
    public static final int CELL=16;
    public PortalRegionWindow { rectangles=List.copyOf(rectangles);warmCells=Set.copyOf(warmCells); }
    public static PortalRegionWindow at(PortalRegionDefinition d,Vec3 observer){return at(d,observer,Vec3.ZERO,null);}
    public static PortalRegionWindow at(PortalRegionDefinition d,Vec3 observer,Vec3 velocity,PortalRegionWindow previous) {
        var p=d.source().local(observer);var settings=d.windowSettings();
        if(Math.abs(p.z)>d.activationDistance() || !d.sidedness().allows(p.z) && Math.abs(p.z)>4) return null;
        double size=settings.visibleSize(),half=size/2;
        var rectangles=new ArrayList<PortalAperture.Rectangle>();
        for(var s:d.shape().strips()) {
            double left=Math.max(s.minU(),p.x-half),right=Math.min(s.maxU(),p.x+half);
            double bottom=Math.max(s.minV(),p.y-half),top=Math.min(s.maxV(),p.y+half);
            if(left<right && bottom<top) rectangles.add(new PortalAperture.Rectangle((left-p.x+half)/size,
                    (p.y+half-top)/size,(right-p.x+half)/size,(p.y+half-bottom)/size));
        }
        if(rectangles.isEmpty())return null;
        var localVelocity=PortalTraversalMath.rotate(velocity,d.source().rotation().conjugate());
        double time=localVelocity.z*p.z<0 ? Math.min(20,Math.abs(p.z/localVelocity.z)) : 10;
        var look=new Vec3(localVelocity.x*time,localVelocity.y*time,0);
        if(look.length()>settings.lookAhead())look=look.normalize().scale(settings.lookAhead());
        int u=follow(previous==null?null:previous.warmU,p.x+look.x,settings.hysteresis());
        int v=follow(previous==null?null:previous.warmV,p.y+look.y,settings.hysteresis());
        Set<Long> warm=previous!=null && previous.warmU==u && previous.warmV==v ? previous.warmCells
                : cells(d.shape(),u-half-settings.warmMargin(),v-half-settings.warmMargin(),u+half+settings.warmMargin(),v+half+settings.warmMargin());
        return new PortalRegionWindow(p.x,p.y,settings.visibleSize(),rectangles,u,v,warm,
                cells(d.shape(),p.x-half,p.y-half,p.x+half,p.y+half).size());
    }
    static int follow(Integer previous,double wanted,int hysteresis) {
        if(previous!=null && Math.abs(wanted-previous)<=CELL/2.0+hysteresis)return previous;
        return (int)Math.floor(wanted/CELL+.5)*CELL;
    }
    private static Set<Long> cells(PortalRegionShape shape,double minU,double minV,double maxU,double maxV) {
        var cells=new HashSet<Long>();
        // Intersect strips first: empty space and total strip length never produce cell scans.
        for(var s:shape.strips()) {
            double left=Math.max(minU,s.minU()),right=Math.min(maxU,s.maxU());
            double bottom=Math.max(minV,s.minV()),top=Math.min(maxV,s.maxV());
            if(left>=right || bottom>=top) continue;
            for(int u=(int)Math.floor(left/CELL);u<Math.ceil(right/CELL);u++)
                for(int v=(int)Math.floor(bottom/CELL);v<Math.ceil(top/CELL);v++)
                    cells.add(((long)u<<32) ^ (v&0xffffffffL));
        }
        return cells;
    }
    public Vec3 center(PortalRegionDefinition d){return d.source().world(centerU,centerV,0);}
    public Vec3 mappedWarmCenter(PortalRegionDefinition d,Vec3 observer) {
        return d.mapPosition(d.source().world(warmU,warmV,d.source().local(observer).z));
    }
}
