package com.skyeshade.skyesight.client.render.entity;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector4f;

import java.util.*;

/**
 * Scoped Sutherland-Hodgman clipping of entity QUADS. Attributes are interpolated at the cut.
 * Leaves shaders, render types and batch ownership unchanged; direct GPU/custom primitive draws are unsupported.
 */
public final class PortalEntityClipBuffer implements MultiBufferSource, AutoCloseable {
    private final MultiBufferSource output;
    private final List<List<Vector4f>> regions;
    private final boolean union;
    private final Map<RenderType, Consumer> consumers = new LinkedHashMap<>();

    public PortalEntityClipBuffer(MultiBufferSource output, List<Vector4f> planes) {
        this(output, List.of(planes), true);
    }

    public PortalEntityClipBuffer(MultiBufferSource output, List<List<Vector4f>> regions, boolean union) {
        this.output = output;
        this.union = union;
        this.regions = regions.stream().map(p -> p.stream().map(Vector4f::new).toList()).toList();
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        // Finish pending vertices before another RenderType can flush its shared BufferBuilder.
        consumers.values().forEach(Consumer::finish);
        if (type.mode() != VertexFormat.Mode.QUADS) return output.getBuffer(type);
        return consumers.computeIfAbsent(type, Consumer::new);
    }

    @Override
    public void close() {
        consumers.values().forEach(Consumer::finish);
    }

    private final class Consumer implements VertexConsumer {
        private final RenderType type;
        private final List<float[]> vertices = new ArrayList<>(4);
        private float[] current;

        Consumer(RenderType type) {
            this.type = type;
        }

        private void finishVertex() {
            if (current == null) return;
            vertices.add(current);
            current = null;
            if (vertices.size() == 4) {
                emit();
                vertices.clear();
            }
        }

        void finish() {
            finishVertex();
        }

        private void emit() {
            if (union) {
                for (var planes : regions) {
                    List<float[]> polygon = new ArrayList<>(vertices);
                    for (var plane : planes) polygon = clip(polygon, plane);
                    emitPolygon(polygon);
                }
            } else {
                List<List<float[]>> remaining = List.of(new ArrayList<>(vertices));
                for (var region : regions) {
                    List<List<float[]>> next = new ArrayList<>();
                    for (var polygon : remaining) next.addAll(subtract(polygon, region));
                    remaining = next;
                }
                remaining.forEach(this::emitPolygon);
            }
        }

        private void emitPolygon(List<float[]> polygon) {
            if (polygon.size() < 3) return;
            VertexConsumer target = output.getBuffer(type);
            if (polygon.size() == 4) {
                polygon.forEach(v -> write(target, v));
                return;
            }
            for (int i = 1; i < polygon.size() - 1; i++) {
                write(target, polygon.getFirst());
                write(target, polygon.get(i));
                write(target, polygon.get(i + 1));
                write(target, polygon.get(i + 1));
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            finishVertex();
            current = new float[16];
            current[0] = x;
            current[1] = y;
            current[2] = z;
            Arrays.fill(current, 3, 7, 255);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            current[3] = r;
            current[4] = g;
            current[5] = b;
            current[6] = a;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            current[7] = u;
            current[8] = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            current[9] = u;
            current[10] = v;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            current[11] = u;
            current[12] = v;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            current[13] = x;
            current[14] = y;
            current[15] = z;
            return this;
        }
    }

    /** Disjoint remainder outside a convex region; preserves source geometry beside the aperture. */
    static List<List<float[]>> subtract(List<float[]> polygon, List<Vector4f> region) {
        List<List<float[]>> outside = new ArrayList<>();
        for (var plane : region) {
            var piece = clip(polygon, new Vector4f(plane).negate());
            if (piece.size() >= 3) outside.add(piece);
            polygon = clip(polygon, plane);
            if (polygon.size() < 3) break;
        }
        return outside;
    }

    static List<float[]> clip(List<float[]> input, Vector4f p) {
        List<float[]> out = new ArrayList<>();
        if (input.isEmpty()) return out;
        float[] previous = input.getLast();
        float before = distance(previous, p);
        for (float[] v : input) {
            float d = distance(v, p);
            if ((before >= 0) != (d >= 0)) {
                float t = before / (before - d);
                float[] cut = new float[v.length];
                for (int i = 0; i < cut.length; i++) cut[i] = previous[i] + t * (v[i] - previous[i]);
                out.add(cut);
            }
            if (d >= 0) out.add(v);
            previous = v;
            before = d;
        }
        return out;
    }

    private static float distance(float[] v, Vector4f p) {
        return p.x * v[0] + p.y * v[1] + p.z * v[2] + p.w;
    }

    private static void write(VertexConsumer out, float[] v) {
        out.addVertex(v[0], v[1], v[2]).setColor(Math.round(v[3]), Math.round(v[4]), Math.round(v[5]), Math.round(v[6]))
                .setUv(v[7], v[8]).setUv1(Math.round(v[9]), Math.round(v[10])).setUv2(Math.round(v[11]), Math.round(v[12]))
                .setNormal(v[13], v[14], v[15]);
    }
}
