package com.skyeshade.skyesight.client.debug;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.skyeshade.skyesight.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Opt-in integration checks using real renderers and unregistered client fixtures, without GPU batches. */
public final class SkyesightSliceVerifier {
    private SkyesightSliceVerifier() {}

    public static String verify() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) throw new IllegalStateException("Join a client world first");
        List<String> results = new ArrayList<>();
        verifyCosmeticCaps();
        results.add("cosmetic shells clip without changing body caps");
        // Real local pose is captured but never changed or removed.
        try (var visual = SkyesightEntitySliceApi.capture(mc.player, .5f)) { verifyPlanes(visual); }
        results.add("local player");
        var fixtures = new ArrayList<Entity>();
        fixtures.add(new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "SliceFixture")));
        var originalSkin = mc.player.getSkin();
        var wingTexture = ResourceLocation.withDefaultNamespace("textures/entity/elytra.png");
        var featureSkin = new PlayerSkin(originalSkin.texture(), originalSkin.textureUrl(), wingTexture,
                wingTexture, originalSkin.model(), originalSkin.secure());
        var cape = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "SliceCape")) {
            @Override public PlayerSkin getSkin() { return featureSkin; }
        };
        var wings = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "SliceWings")) {
            @Override public PlayerSkin getSkin() { return featureSkin; }
        };
        var playerData = mc.player.getEntityData().getNonDefaultValues();
        if (playerData != null) { cape.getEntityData().assignValues(playerData); wings.getEntityData().assignValues(playerData); }
        fixtures.add(cape);
        fixtures.add(wings);
        var blockItem=new ItemEntity(mc.level,0,0,0,new ItemStack(Items.STONE));
        fixtures.add(blockItem);
        for (var type : List.of(EntityType.COW, EntityType.ZOMBIE, EntityType.SHEEP, EntityType.ITEM,
                EntityType.ARMOR_STAND, EntityType.ARROW, EntityType.MINECART)) fixtures.add(type.create(mc.level));
        for (Entity source : fixtures) {
            if (source == null) throw new AssertionError("Fixture type could not be created");
            source.setPos(mc.player.position());
            source.xOld = source.getX(); source.yOld = source.getY(); source.zOld = source.getZ();
            source.tickCount = 53;
            source.setYRot(35); source.yRotO = 20;
            source.setXRot(15); source.xRotO = 10;
            if (source instanceof LivingEntity living) {
                living.yBodyRot = 25; living.yBodyRotO = 10;
                living.yHeadRot = 60; living.yHeadRotO = 40;
                living.walkAnimation.update(.7f, 1);
                living.attackAnim = .4f; living.oAttackAnim = .2f;
                living.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
                living.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
                living.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                if (source instanceof RemotePlayer) { living.setShiftKeyDown(true); living.setPose(Pose.CROUCHING); }
                if (source == wings) living.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
            }
            if (source instanceof ItemEntity item) item.setItem(new ItemStack(source==blockItem?Items.STONE:Items.DIAMOND));
            try (var visual = SkyesightEntitySliceApi.capture(source, .5f)) {
                var before = draw(visual, null, ClipSide.NONE, new Matrix4f());
                require(before.vertices > 0, "No base vertices: " + visual.entityType());
                source.setYRot(-120);
                if (source instanceof LivingEntity living) living.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                // This object was never registered in a level; no gameplay entity is removed.
                source.remove(Entity.RemovalReason.DISCARDED);
                var after = draw(visual, null, ClipSide.NONE, new Matrix4f());
                require(before.hash == after.hash && before.vertices == after.vertices,
                        "Pose changed after source removal: " + visual.entityType());
                verifyPlanes(visual,source==blockItem);
                if (source == cape) require(before.featureVertices > 0, "Cape layer was not exercised; enable cape in skin customization");
                if (source == wings) require(before.featureVertices > 0, "Elytra layer was not exercised");
                results.add(visual.entityType() + (source == cape ? " cape" : source == wings ? " elytra" : "")
                        + "=" + before.vertices + " vertices, removal stable");
            }
        }
        return String.join("; ", results);
    }

    private static void verifyPlanes(FrozenEntityVisual visual) {
        verifyPlanes(visual,false);
    }

    /** Fixture-only synced customization change; the live player is never changed. */
    @SuppressWarnings("unchecked")
    private static void setCosmetics(RemotePlayer player, int mask) {
        try {
            var field=net.minecraft.world.entity.player.Player.class.getDeclaredField("DATA_PLAYER_MODE_CUSTOMISATION");
            field.setAccessible(true);
            player.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor<Byte>)field.get(null),(byte)mask);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static void verifyCosmeticCaps() {
        var mc=Minecraft.getInstance();
        var originalSkin=mc.player.getSkin();
        var skin=new PlayerSkin[]{new PlayerSkin(originalSkin.texture(),originalSkin.textureUrl(),null,null,originalSkin.model(),originalSkin.secure())};
        var player=new RemotePlayer(mc.level,new GameProfile(UUID.nameUUIDFromBytes("SliceLayers".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"SliceLayers")) {
            @Override public PlayerSkin getSkin() { return skin[0]; }
        };
        player.setPos(mc.player.position());
        player.xOld=player.getX();player.yOld=player.getY();player.zOld=player.getZ();
        player.tickCount=40;
        var plane=new SkyesightClipPlane(player.position().add(0,1,0),new Vec3(1,1,1));
        setCosmetics(player,0);
        Recording base;
        try(var visual=SkyesightEntitySliceApi.capture(player,.5f)) {base=draw(visual,plane,ClipSide.POSITIVE,new Matrix4f());}
        setCosmetics(player,127);
        Recording outer;
        try(var visual=SkyesightEntitySliceApi.capture(player,.5f)) {outer=draw(visual,plane,ClipSide.POSITIVE,new Matrix4f());}
        require(base.capVertices>0,"Base body did not generate a cap");
        require(base.capHash==outer.capHash,"Cosmetic shells changed cap: base="+base.capVertices+" outer="+outer.capVertices);
        require(outer.vertices>base.vertices,"Cosmetics were hidden instead of clipped");
        require(outer.outside==0,"Cosmetics bypassed clipping");
        for(boolean crouching:new boolean[]{false,true}) {
            player.setShiftKeyDown(crouching);player.setPose(crouching?Pose.CROUCHING:Pose.STANDING);
            for(Vec3 n:List.of(new Vec3(1,0,0),new Vec3(0,0,1),new Vec3(0,1,0),new Vec3(1,1,1))) {
                var cut=new SkyesightClipPlane(player.position().add(0,.95,0),n);
                for(var side:List.of(ClipSide.POSITIVE,ClipSide.NEGATIVE)) {
                    setCosmetics(player,0);var body=captureDraw(player,cut,side);
                    require(body.capVertices>0,"Body seam hollow");
                    for(var part:net.minecraft.world.entity.player.PlayerModelPart.values()) {
                        if(part==net.minecraft.world.entity.player.PlayerModelPart.CAPE) continue;
                        setCosmetics(player,part.getMask());var shell=captureDraw(player,cut,side);
                        require(body.capHash==shell.capHash,"Cosmetic cap changed for "+part+" crouching="+crouching);
                        require(shell.outside==0,"Unclipped cosmetic "+part);
                    }
                }
            }
        }
        // Keep the same item arm pose while varying solid/cosmetic feature geometry.
        setCosmetics(player,127);player.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));
        var cut=new SkyesightClipPlane(player.position().add(0,.95,0),new Vec3(1,1,1));
        var body=captureDraw(player,cut,ClipSide.POSITIVE);
        var slots=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);
        var armor=List.of(Items.DIAMOND_HELMET,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_LEGGINGS,Items.DIAMOND_BOOTS);
        for(int i=0;i<slots.size();i++) {
            player.setItemSlot(slots.get(i),new ItemStack(armor.get(i)));
            require(captureDraw(player,cut,ClipSide.POSITIVE).capHash==body.capHash,"Armor added body cap: "+slots.get(i));
        }
        player.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.STICK));
        require(captureDraw(player,cut,ClipSide.POSITIVE).capHash==body.capHash,"Held item changed body cap");
        var texture=ResourceLocation.withDefaultNamespace("textures/entity/elytra.png");
        skin[0]=new PlayerSkin(originalSkin.texture(),originalSkin.textureUrl(),texture,texture,originalSkin.model(),originalSkin.secure());
        require(captureDraw(player,cut,ClipSide.POSITIVE).capHash==body.capHash,"Cape changed body cap");
        player.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.ELYTRA));
        require(captureDraw(player,cut,ClipSide.POSITIVE).capHash==body.capHash,"Elytra changed body cap");
    }

    private static Recording captureDraw(RemotePlayer player,SkyesightClipPlane plane,ClipSide side) {
        try(var visual=SkyesightEntitySliceApi.capture(player,.5f)) {return draw(visual,plane,side,new Matrix4f());}
    }

    private static void verifyPlanes(FrozenEntityVisual visual, boolean solidItem) {
        var geometry = draw(visual, null, ClipSide.NONE, new Matrix4f());
        require(geometry.vertices > 0, "No visible geometry: " + visual.entityType());
        Vec3 center = visual.position().add((geometry.minX + geometry.maxX) / 2,
                (geometry.minY + geometry.maxY) / 2, (geometry.minZ + geometry.maxZ) / 2);
        for (Vec3 normal : List.of(new Vec3(1,0,0),new Vec3(0,0,1),new Vec3(0,1,0),new Vec3(1,1,1))) {
            var plane = new SkyesightClipPlane(center,normal);
            for (var side : List.of(ClipSide.POSITIVE,ClipSide.NEGATIVE)) {
                var root = new Matrix4f().translation(.4f,.2f,-.1f).rotateY(.6f);
                var output = draw(visual,plane,side,root);
                require(output.vertices > 0,"Empty half: " + visual.entityType() + " " + side + " " + normal);
                require(output.outside == 0,"Unclipped vertices: " + visual.entityType() + " " + output.outside);
                require(output.badCaps == 0,"Cap off plane or facing wrong direction: " + visual.entityType());
                if (solidItem || (!visual.entityType().getPath().equals("arrow") && !visual.entityType().getPath().equals("item")))
                    require(output.capVertices > 0,"No cap generated: " + visual.entityType() + " " + side + " " + normal);
                var repeated = draw(visual,plane,side,root);
                require(output.hash == repeated.hash,"Frozen render changed across draws: " + visual.entityType());
                if (output.capVertices>0) {
                    var moved=draw(visual,plane,side,new Matrix4f().translation(-2,1,3).rotateY(2.1f).rotateX(.4f));
                    require(moved.capVertices>0 && moved.badCaps==0,"Cap detached under destination-style transform");
                    require(Math.abs(output.minU-moved.minU)<.002 && Math.abs(output.maxU-moved.maxU)<.002
                            && Math.abs(output.minV-moved.minV)<.002 && Math.abs(output.maxV-moved.maxV)<.002,"Planar UVs changed with root transform");
                }
            }
        }
    }

    private static Recording draw(FrozenEntityVisual visual, SkyesightClipPlane plane, ClipSide side, Matrix4f root) {
        var recording = new Recording(plane == null ? null : visual.transformedPlane(plane,root),side,visual.position());
        MultiBufferSource buffer = type -> {
            recording.feature = type.toString().contains("textures/entity/elytra.png");
            recording.cap = type.toString().contains("textures/entity/energy_cut.png");
            return recording;
        };
        visual.render(new PoseStack(),buffer,0xF000F0,visual.position(),plane,side,root,SliceRenderOptions.ENERGY_CAP);
        return recording;
    }
    private static void require(boolean valid,String message) { if (!valid) throw new AssertionError(message); }

    private static final class Recording implements VertexConsumer {
        private final SkyesightClipPlane plane;
        private final ClipSide side;
        private final Vec3 origin;
        private int vertices, outside;
        private int featureVertices;
        private boolean feature;
        private boolean cap;
        private int capVertices, badCaps;
        private float minU=Float.POSITIVE_INFINITY,maxU=Float.NEGATIVE_INFINITY,minV=Float.POSITIVE_INFINITY,maxV=Float.NEGATIVE_INFINITY;
        private long hash = 1;
        private long capHash = 1;
        private double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY,minZ=Double.POSITIVE_INFINITY;
        private double maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
        Recording(SkyesightClipPlane plane,ClipSide side,Vec3 origin) { this.plane=plane;this.side=side;this.origin=origin; }
        private void hash(float value) { hash = hash * 31 + Float.floatToIntBits(value); if(cap) capHash=capHash*31+Float.floatToIntBits(value); }
        public VertexConsumer addVertex(float x,float y,float z) {
            vertices++;hash(x);hash(y);hash(z);
            if (feature) featureVertices++;
            if (cap) {
                capVertices++;
                if (plane==null || Math.abs(plane.signedDistance(origin.add(x,y,z)))>1e-4) badCaps++;
            }
            minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);
            maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);
            if (plane != null && plane.signedDistance(origin.add(x,y,z)) * (side == ClipSide.POSITIVE ? 1 : -1) < -1e-4) outside++;
            return this;
        }
        public VertexConsumer setColor(int r,int g,int b,int a) { hash(r);hash(g);hash(b);hash(a);return this; }
        public VertexConsumer setUv(float u,float v) {
            hash(u);hash(v);
            if(cap) {minU=Math.min(minU,u);maxU=Math.max(maxU,u);minV=Math.min(minV,v);maxV=Math.max(maxV,v);}
            return this;
        }
        public VertexConsumer setUv1(int u,int v) { hash(u);hash(v);return this; }
        public VertexConsumer setUv2(int u,int v) { hash(u);hash(v);return this; }
        public VertexConsumer setNormal(float x,float y,float z) {
            hash(x);hash(y);hash(z);
            if(cap && plane.normal().dot(new Vec3(x,y,z))*(side==ClipSide.POSITIVE?-1:1)<.99) badCaps++;
            return this;
        }
    }
}
