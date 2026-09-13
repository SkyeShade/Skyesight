package com.skyeshade.skyesight.client.render.slice;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.*;

/** Bounded planar boundary reconstruction and union fill. Independent of Minecraft/GPU state. */
public final class SliceCapGeometry {
    private static final double WELD = 1e-4;
    private static final int LIMIT = 4096;
    public record Point(double x, double y) {}
    public record Triangle(Point a, Point b, Point c) {}
    private record Key(long x, long y) {}
    private record Directed(Key from, Key to) {}
    private final Map<Key, Point> points = new HashMap<>();
    private final Map<Key, Set<Key>> neighbors = new LinkedHashMap<>();
    private boolean overflow;
    private int connections;

    public void add(Point a, Point b) {
        if (overflow) return;
        if (!Double.isFinite(a.x+a.y+b.x+b.y)) { overflow = true; return; }
        Key ka = weldedKey(a), kb = weldedKey(b);
        if (ka.equals(kb)) return;
        points.putIfAbsent(ka,a); points.putIfAbsent(kb,b);
        if (neighbors.computeIfAbsent(ka,k -> new LinkedHashSet<>()).add(kb)) connections++;
        neighbors.computeIfAbsent(kb,k -> new LinkedHashSet<>()).add(ka);
        if (points.size() > LIMIT || connections > LIMIT) overflow = true;
    }

    private static Key key(Point p) { return new Key(Math.round(p.x/WELD),Math.round(p.y/WELD)); }

    private Key weldedKey(Point p) {
        Key cell=key(p);
        for(long x=cell.x-1;x<=cell.x+1;x++) for(long y=cell.y-1;y<=cell.y+1;y++) {
            Key nearby=new Key(x,y);Point q=points.get(nearby);
            if(q!=null && Math.hypot(p.x-q.x,p.y-q.y)<WELD*1.5) return nearby;
        }
        return cell;
    }

    /** Split collinear overlapping edges at existing endpoints before angular face walking. */
    private boolean splitJunctions() {
        var edges=new ArrayList<Directed>();
        for(var entry:neighbors.entrySet()) for(Key to:entry.getValue())
            if(entry.getKey().x<to.x || (entry.getKey().x==to.x && entry.getKey().y<to.y)) edges.add(new Directed(entry.getKey(),to));
        if((long)edges.size()*points.size()>2_000_000) return false;
        neighbors.clear();
        int count=0;
        for(var edge:edges) {
            Point a=points.get(edge.from),b=points.get(edge.to);
            double dx=b.x-a.x,dy=b.y-a.y,length=dx*dx+dy*dy;
            var cuts=new TreeMap<Double,Key>();cuts.put(0.0,edge.from);cuts.put(1.0,edge.to);
            for(var entry:points.entrySet()) {
                Point p=entry.getValue();double t=((p.x-a.x)*dx+(p.y-a.y)*dy)/length;
                if(t>1e-6 && t<1-1e-6 && Math.abs((p.x-a.x)*dy-(p.y-a.y)*dx)<WELD*Math.sqrt(length)) cuts.put(t,entry.getKey());
            }
            Key previous=null;
            for(Key k:cuts.values()) {
                if(previous!=null && !previous.equals(k)) {
                    neighbors.computeIfAbsent(previous,q -> new LinkedHashSet<>()).add(k);
                    neighbors.computeIfAbsent(k,q -> new LinkedHashSet<>()).add(previous);
                    if(++count>LIMIT*2) return false;
                }
                previous=k;
            }
        }
        return true;
    }

    /** Discards open components. Union removes overlap between solid model parts/layers. */
    public List<Triangle> triangles() {
        if (overflow) return List.of();
        var work=new SliceCapGeometry();work.points.putAll(points);
        neighbors.forEach((k,v) -> work.neighbors.put(k,new LinkedHashSet<>(v)));
        return work.build();
    }

    private List<Triangle> build() {
        Area union = new Area();
        // Preserve independent closed solid loops before resolving connected junctions.
        // Otherwise splitting overlapping disconnected walls would invent a face in their void.
        Set<Key> seen=new HashSet<>(),simple=new HashSet<>();
        for(Key start:neighbors.keySet()) {
            if(!seen.add(start)) continue;
            var component=new ArrayList<Key>();var queue=new ArrayDeque<Key>();queue.add(start);
            boolean ordinary=true;
            while(!queue.isEmpty()) {
                Key k=queue.remove();component.add(k);ordinary &= neighbors.get(k).size()==2;
                for(Key q:neighbors.get(k)) if(seen.add(q)) queue.add(q);
            }
            if(!ordinary) continue;
            var path=new Path2D.Double();Point p=points.get(start);path.moveTo(p.x,p.y);
            Key previous=null,current=start;
            do {
                Key next=null;
                for(Key candidate:neighbors.get(current)) if(!candidate.equals(previous)) {next=candidate;break;}
                previous=current;current=next;p=points.get(current);path.lineTo(p.x,p.y);
            } while(!current.equals(start));
            path.closePath();union.add(new Area(path));simple.addAll(component);
        }
        simple.forEach(k -> {neighbors.remove(k);points.remove(k);});
        if(!splitJunctions()) return List.of();
        Set<Key> visited = new HashSet<>();
        for (Key start : neighbors.keySet()) {
            if (visited.contains(start)) continue;
            var component = new ArrayList<Key>();
            var pending = new ArrayDeque<Key>(); pending.add(start); visited.add(start);
            boolean closed = true;
            while (!pending.isEmpty()) {
                Key k = pending.remove(); component.add(k);
                int degree=neighbors.get(k).size();
                if (degree < 2) closed = false;
                for (Key next : neighbors.get(k)) if (visited.add(next)) pending.add(next);
            }
            if (!closed || component.size() < 3) continue;
            // Parts can touch at a vertex (minecart floor/walls). Walk planar faces using
            // angularly ordered half-edges rather than rejecting those even-degree junctions.
            Map<Key,List<Key>> ordered=new HashMap<>();
            for(Key k:component) {
                Point p=points.get(k);var next=new ArrayList<>(neighbors.get(k));
                next.sort(Comparator.comparingDouble(q -> Math.atan2(points.get(q).y-p.y,points.get(q).x-p.x)));
                ordered.put(k,next);
            }
            Set<Directed> used=new HashSet<>();
            for(Key from:component) for(Key to:ordered.get(from)) {
                Directed firstEdge=new Directed(from,to);
                if(used.contains(firstEdge)) continue;
                var polygon=new ArrayList<Point>();
                Directed edge=firstEdge;
                do {
                    if(!used.add(edge)) return List.of();
                    polygon.add(points.get(edge.from));
                    var around=ordered.get(edge.to);
                    int index=around.indexOf(edge.from);
                    edge=new Directed(edge.to,around.get((index+around.size()-1)%around.size()));
                } while(!edge.equals(firstEdge));
                double signedArea=0;
                for(int i=0;i<polygon.size();i++) {
                    Point a=polygon.get(i),b=polygon.get((i+1)%polygon.size());signedArea+=a.x*b.y-a.y*b.x;
                }
                if(signedArea<=1e-10) continue; // Exterior/unbounded face or zero-area backtrack.
                Path2D.Double path=new Path2D.Double();
                Point p=polygon.getFirst();path.moveTo(p.x,p.y);
                for(int i=1;i<polygon.size();i++) { p=polygon.get(i);path.lineTo(p.x,p.y); }
                path.closePath();union.add(new Area(path));
            }
        }
        // A sweep between every union vertex Y yields exact trapezoids, including concavities
        // and holes in the union. No centroid fan that could bridge unrelated limbs.
        var edges = new ArrayList<Point[]>();
        var levels = new TreeSet<Double>();
        double[] coords = new double[6];
        Point first=null, previous=null;
        for (PathIterator it=union.getPathIterator(null);!it.isDone();it.next()) {
            int kind=it.currentSegment(coords);
            if (kind==PathIterator.SEG_MOVETO) { first=previous=new Point(coords[0],coords[1]);levels.add(previous.y); }
            else if (kind==PathIterator.SEG_LINETO) {
                Point next=new Point(coords[0],coords[1]); edges.add(new Point[]{previous,next});levels.add(next.y);previous=next;
            } else if (kind==PathIterator.SEG_CLOSE) edges.add(new Point[]{previous,first});
            else return List.of();
            if (edges.size()>LIMIT) return List.of();
        }
        var result = new ArrayList<Triangle>();
        var ys = new ArrayList<>(levels);
        for(int i=1;i<ys.size();i++) {
            double low=ys.get(i-1), high=ys.get(i), mid=(low+high)/2;
            if(high-low<1e-9) continue;
            var active=new ArrayList<Point[]>();
            for(var edge:edges) if(Math.min(edge[0].y,edge[1].y)<mid && Math.max(edge[0].y,edge[1].y)>mid) active.add(edge);
            active.sort(Comparator.comparingDouble(e -> xAt(e,mid)));
            if(active.size()%2!=0) return List.of();
            for(int j=0;j<active.size();j+=2) {
                var left=active.get(j);var right=active.get(j+1);
                Point a=new Point(xAt(left,low),low), b=new Point(xAt(right,low),low);
                Point c=new Point(xAt(right,high),high), d=new Point(xAt(left,high),high);
                addTriangle(result,a,b,c);addTriangle(result,a,c,d);
                if(result.size()>LIMIT*2) return List.of();
            }
        }
        return result;
    }
    private static double xAt(Point[] e,double y) { return e[0].x+(y-e[0].y)*(e[1].x-e[0].x)/(e[1].y-e[0].y); }
    private static void addTriangle(List<Triangle> out,Point a,Point b,Point c) {
        if ((b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x)>1e-10) out.add(new Triangle(a,b,c));
    }
}
