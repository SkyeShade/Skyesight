package com.skyeshade.skyesight.command;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.api.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Operator-only fixture. Creates no terrain or entities; traversal is explicitly opt-in. */
@EventBusSubscriber(modid=Skyesight.MODID)
public final class PortalRegionCommands {
    private static final ResourceLocation ID=ResourceLocation.parse("skyesight:debug_region");
    private PortalRegionCommands() {}
    @SubscribeEvent public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("skyesightregion").requires(s->s.hasPermission(2))
                .then(Commands.literal("remove").executes(c->{SkyesightPortalRegionApi.remove(c.getSource().getServer(),ID);return 1;}))
                .then(Commands.literal("configure")
                        .then(Commands.argument("visibleSize",IntegerArgumentType.integer(1))
                                .then(Commands.argument("warmMargin",IntegerArgumentType.integer(8))
                                        .then(Commands.argument("traversable",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c->{
                                            var server=c.getSource().getServer();
                                            var d=SkyesightPortalRegionApi.get(server,ID).orElse(null);
                                            if(d==null){c.getSource().sendFailure(Component.literal("Create a debug region first"));return 0;}
                                            int margin=IntegerArgumentType.getInteger(c,"warmMargin");
                                            try {
                                            var windows=new PortalRegionWindowSettings(IntegerArgumentType.getInteger(c,"visibleSize"),margin,Math.min(16,Math.max(0,margin-16)),Math.min(8,margin-8));
                                            var behavior=com.mojang.brigadier.arguments.BoolArgumentType.getBool(c,"traversable")?PortalBehavior.TRAVERSABLE:PortalBehavior.VISUAL_ONLY;
                                            SkyesightPortalRegionApi.update(server,new PortalRegionDefinition(d.id(),d.source(),d.destination(),d.shape(),d.sidedness(),
                                                    d.bidirectional(),d.activationDistance(),Math.max(d.renderRadiusChunks(),windows.minimumChunkRadius()),d.entityRadiusChunks(),
                                                    d.simulationRadiusChunks(),behavior,windows));
                                            c.getSource().sendSuccess(()->Component.literal("Region window="+windows+" behavior="+behavior),false);return 1;
                                            } catch(IllegalArgumentException failure) {c.getSource().sendFailure(Component.literal(failure.getMessage()));return 0;}
                                        })))))
                .then(Commands.literal("horizontal")
                        .then(Commands.argument("length",IntegerArgumentType.integer(1,60_000_000))
                                .then(Commands.argument("destination",DimensionArgument.dimension())
                                        .then(Commands.argument("destinationY",DoubleArgumentType.doubleArg()).executes(c->{
                                            var source=c.getSource(); var origin=source.getPosition().add(0,-4,0);
                                            int length=IntegerArgumentType.getInteger(c,"length");
                                            // U=X, V=Z, normal=-Y. Opposed destination frame cancels the canonical half-turn.
                                            var rotation=new Quaternionf().rotateX((float)Math.PI/2);
                                            var a=new PortalRegionFrame(source.getLevel().dimension(),origin,rotation);
                                            var b=new PortalRegionFrame(DimensionArgument.getDimension(c,"destination").dimension(),
                                                    new Vec3(origin.x,DoubleArgumentType.getDouble(c,"destinationY"),origin.z),
                                                    new Quaternionf(rotation).mul(new Quaternionf(0,1,0,0)));
                                            SkyesightPortalRegionApi.register(source.getServer(),new PortalRegionDefinition(ID,a,b,
                                                    PortalRegionShape.rectangle(-length/2,-24,(length+1)/2,24),PortalSidedness.BACK_ONLY,
                                                    true,64,8,4));
                                            source.sendSuccess(()->Component.literal("Horizontal region: "+length+" x 48, source Y="+origin.y
                                                    +", visible aperture 128 x 128; use configure to enable traversal."),false); return 1;
                                        }))))));
    }
}
