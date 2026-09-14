package com.skyeshade.skyesight.command;

import com.skyeshade.skyesight.Skyesight;
import com.skyeshade.skyesight.api.*;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Explicit operator fixture: clears two 17x17 chambers. Use only in a disposable test world. */
@EventBusSubscriber(modid=Skyesight.MODID)
public final class PortalRegionSeamFixture {
    private PortalRegionSeamFixture() {}
    @SubscribeEvent public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("skyesightregion").requires(s->s.hasPermission(2))
                .then(Commands.literal("seamtest")
                        .then(Commands.argument("destination",DimensionArgument.dimension())
                                .then(Commands.argument("sourceY",DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("destinationY",DoubleArgumentType.doubleArg()).executes(c->{
                                            var source=c.getSource();var destination=DimensionArgument.getDimension(c,"destination");
                                            double aY=DoubleArgumentType.getDouble(c,"sourceY"),bY=DoubleArgumentType.getDouble(c,"destinationY");
                                            if(!fits(source.getLevel(),aY) || !fits(destination,bY)
                                                    || source.getLevel()==destination && Math.abs(aY-bY)<16) {
                                                source.sendFailure(Component.literal("Use chambers within build height, at least 16 blocks apart in the same dimension"));return 0;
                                            }
                                            var p=BlockPos.containing(source.getPosition());
                                            chamber(source.getLevel(),p.getX(),(int)Math.floor(aY),p.getZ());
                                            chamber(destination,p.getX(),(int)Math.floor(bY),p.getZ());
                                            var q=new Quaternionf().rotateX((float)Math.PI/2);
                                            SkyesightPortalRegionApi.register(source.getServer(),new PortalRegionDefinition(ResourceLocation.parse("skyesight:debug_region"),
                                                    new PortalRegionFrame(source.getLevel().dimension(),new Vec3(p.getX()+.5,aY,p.getZ()+.5),q),
                                                    new PortalRegionFrame(destination.dimension(),new Vec3(p.getX()+.5,bY,p.getZ()+.5),new Quaternionf(q).mul(new Quaternionf(0,1,0,0))),
                                                    PortalRegionShape.rectangle(-1000,-24,1000,24),PortalSidedness.BACK_ONLY,true,64,8,4,0,
                                                    PortalBehavior.TRAVERSABLE,PortalRegionWindowSettings.DEFAULT));
                                            source.sendSuccess(()->Component.literal("Seam fixture ready at Y="+aY+" / "+bY+". Fall through the central 5x5 shaft; pillar back from the destination floor. "
                                                    +"Glass, stained glass, water and leaves are east, west, south and north."),false);return 1;
                                        }))))));
    }
    private static boolean fits(ServerLevel level,double y) {
        return Double.isFinite(y) && y>=level.getMinBuildHeight()+7 && y<level.getMaxBuildHeight()-8;
    }
    private static void chamber(ServerLevel level,int x,int y,int z) {
        for(var pos:BlockPos.betweenClosed(x-8,y-6,z-8,x+8,y+7,z+8)) {
            var block=pos.getY()==y-6?Blocks.SMOOTH_STONE:pos.getY()==y
                    && (Math.abs(pos.getX()-x)>2 || Math.abs(pos.getZ()-z)>2)?Blocks.BEDROCK:Blocks.AIR;
            level.setBlockAndUpdate(pos,block.defaultBlockState());
        }
        var fixtures=java.util.List.of(Blocks.GLASS,Blocks.RED_STAINED_GLASS,Blocks.WATER,Blocks.OAK_LEAVES);
        int[][] offsets={{5,0},{-5,0},{0,5},{0,-5}};
        for(int i=0;i<offsets.length;i++) for(int u=-1;u<=1;u++) for(int v=-1;v<=1;v++) {
            int bx=x+offsets[i][0]+u,bz=z+offsets[i][1]+v;
            level.setBlockAndUpdate(new BlockPos(bx,y,bz),Blocks.AIR.defaultBlockState());
            var state=fixtures.get(i).defaultBlockState();
            if(fixtures.get(i)==Blocks.OAK_LEAVES) state=state.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT,true);
            level.setBlock(new BlockPos(bx,y+2,bz),state,2);
        }
    }
}
