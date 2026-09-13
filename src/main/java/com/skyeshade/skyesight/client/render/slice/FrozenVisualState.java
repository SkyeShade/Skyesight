package com.skyeshade.skyesight.client.render.slice;

import com.mojang.authlib.GameProfile;
import com.skyeshade.skyesight.mixin.common.SynchedEntityDataAccessor;
import com.skyeshade.skyesight.mixin.common.ItemEntityAnimationAccessor;
import io.netty.buffer.Unpooled;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Detached data only. Never stores an entity, level, renderer, connection or mutable model. */
public final class FrozenVisualState {
    private final EntityType<?> type;
    private final CompoundTag tag;
    private final byte[] synced;
    private final List<Value> transientValues;
    private final UUID uuid;
    private final String playerName;
    private final PlayerSkin skin;
    private final List<Equipment> equipment;
    private final float itemBob;
    private final boolean spectator;

    public FrozenVisualState(Entity source) {
        type = source.getType();
        itemBob = source instanceof net.minecraft.world.entity.item.ItemEntity item ? item.bobOffs : 0;
        spectator = source.isSpectator();
        uuid = source.getUUID();
        tag = source.saveWithoutId(new CompoundTag());
        // Passengers are gameplay relationships, never reconstructed by this visual.
        tag.remove("Passengers");
        playerName = source instanceof AbstractClientPlayer p ? p.getGameProfile().getName() : null;
        skin = source instanceof AbstractClientPlayer p ? p.getSkin() : null;
        transientValues = captureValues(source, source.level().registryAccess());
        equipment = new ArrayList<>();
        if (source instanceof LivingEntity living) for (EquipmentSlot slot : EquipmentSlot.values()) {
            equipment.add(new Equipment(slot, (CompoundTag)living.getItemBySlot(slot).saveOptional(source.level().registryAccess())));
        }
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), source.level().registryAccess());
        try {
            var items = ((SynchedEntityDataAccessor)source.getEntityData()).skyesight$getItemsById();
            buffer.writeVarInt(items.length);
            for (var item : items) item.value().write(buffer);
            synced = new byte[buffer.readableBytes()];
            buffer.readBytes(synced);
        } finally { buffer.release(); }
    }

    public Entity create(ClientLevel level) {
        Entity entity = playerName == null ? type.create(level) : new VisualPlayer(level, new GameProfile(uuid, playerName), skin, spectator);
        if (entity == null) throw new UnsupportedOperationException("Entity type cannot create a client visual: " + type);
        entity.load(tag.copy());
        entity.setUUID(uuid);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(synced), level.registryAccess());
        try {
            int count = buffer.readVarInt();
            var values = new ArrayList<SynchedEntityData.DataValue<?>>(count);
            for (int i = 0; i < count; i++) values.add(SynchedEntityData.DataValue.read(buffer, buffer.readUnsignedByte()));
            entity.getEntityData().assignValues(values);
        } finally { buffer.release(); }
        if (entity instanceof LivingEntity living) for (var item : equipment) {
            living.setItemSlot(item.slot, ItemStack.parseOptional(level.registryAccess(), item.tag.copy()));
        }
        restoreValues(entity, transientValues, level.registryAccess());
        if (entity instanceof net.minecraft.world.entity.item.ItemEntity item)
            ((ItemEntityAnimationAccessor)item).skyesight$setVisualBob(itemBob);
        return entity;
    }

    /* Minecraft 1.21.1 renders from entity fields, without a detached EntityRenderState API.
       Copy only vanilla scalar/immutable values and explicitly serialized item stacks; never
       traverse arbitrary object graphs. Nested animation objects contain only scalar clocks.
       This preserves subclass timers (sheep eating, arrow shake, item bob, etc.) without
       retaining AI goals, inventories, passengers, worlds, or mod attachments. */
    private static List<Value> captureValues(Object source, net.minecraft.core.HolderLookup.Provider registries) {
        var values = new ArrayList<Value>();
        try {
            for (Class<?> c = source.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                if (!c.getName().startsWith("net.minecraft.")) continue;
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                    Class<?> t = f.getType();
                    boolean nested = t == AnimationState.class || t == WalkAnimationState.class;
                    if (!nested && !(t.isPrimitive() || t == Vec3.class || t == ItemStack.class
                            || t == net.minecraft.world.InteractionHand.class)) continue;
                    if (Modifier.isFinal(f.getModifiers()) && !nested) continue;
                    f.setAccessible(true);
                    Object value = f.get(source);
                    if (nested) value = captureValues(value, registries);
                    else if (value instanceof ItemStack stack) value = stack.saveOptional(registries);
                    values.add(new Value(f, value, nested));
                }
            }
            return List.copyOf(values);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot capture vanilla visual fields", e); }
    }

    @SuppressWarnings("unchecked")
    private static void restoreValues(Object target, List<Value> values, net.minecraft.core.HolderLookup.Provider registries) {
        try {
            for (Value value : values) {
                // LocalPlayer-only fields do not exist on the detached RemotePlayer surrogate.
                if (!value.field.getDeclaringClass().isInstance(target)) continue;
                if (value.nested) restoreValues(value.field.get(target), (List<Value>)value.value, registries);
                else value.field.set(target, value.value instanceof CompoundTag item && value.field.getType() == ItemStack.class
                        ? ItemStack.parseOptional(registries, item.copy()) : value.value);
            }
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot restore vanilla visual fields", e); }
    }

    private record Value(Field field, Object value, boolean nested) {}
    private record Equipment(EquipmentSlot slot, CompoundTag tag) {}

    private static final class VisualPlayer extends RemotePlayer {
        private final PlayerSkin capturedSkin;
        private final boolean spectator;
        VisualPlayer(ClientLevel level, GameProfile profile, PlayerSkin skin, boolean spectator) {
            super(level, profile);
            capturedSkin = skin;
            this.spectator = spectator;
        }
        @Override public PlayerSkin getSkin() { return capturedSkin; }
        @Override public boolean isSpectator() { return spectator; }
        @Override public boolean isCreative() { return false; }
    }
}
