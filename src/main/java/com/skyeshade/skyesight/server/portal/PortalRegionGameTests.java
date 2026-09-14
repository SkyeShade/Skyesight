package com.skyeshade.skyesight.server.portal;

import com.skyeshade.skyesight.api.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaternionf;

/** Headless runtime coverage; camera pixels and real input still require the seamtest client fixture. */
@GameTestHolder("skyesight")
@PrefixGameTestTemplate(false)
public final class PortalRegionGameTests {
    @GameTest(template="empty", timeoutTicks=200)
    public static void eyeSeamRoundTrip(GameTestHelper helper) {
        var server=helper.getLevel().getServer();var id=ResourceLocation.parse("skyesight:gametest_seam");
        // Keep vanilla teleport behavior, but sink packets without unrelated mod login handshakes.
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"seam-test-player"),false);
        var player=new net.minecraft.server.level.ServerPlayer(server,helper.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        var channel=new io.netty.channel.embedded.EmbeddedChannel(connection);
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(server,connection,player,cookie) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener) {}
        };
        var p=helper.absolutePos(new BlockPos(3,3,3));
        double sourceY=p.getY()+.5,targetY=200.5;
        var q=new Quaternionf().rotateX((float)Math.PI/2);
        var d=new PortalRegionDefinition(id,
                new PortalRegionFrame(helper.getLevel().dimension(),new Vec3(p.getX()+.5,sourceY,p.getZ()+.5),q),
                new PortalRegionFrame(Level.NETHER,new Vec3(p.getX()+.5,targetY,p.getZ()+.5),new Quaternionf(q).mul(new Quaternionf(0,1,0,0))),
                PortalRegionShape.rectangle(-8,-8,8,8),PortalSidedness.BACK_ONLY,true,64,8,0,0,
                PortalBehavior.TRAVERSABLE,PortalRegionWindowSettings.DEFAULT);
        try {
            SkyesightPortalRegionApi.register(server,d);
            for(var pose:new Pose[]{Pose.STANDING,Pose.CROUCHING,Pose.SWIMMING}) {
                player.teleportTo(helper.getLevel(),p.getX()+.5,sourceY+2,p.getZ()+.5,java.util.Set.of(),37,-12);
                player.setPos(p.getX()+.5,sourceY+2,p.getZ()+.5);player.setYRot(37);player.setXRot(-12);
                player.hasChangedDimension();player.setPose(pose);PortalRegionTraversal.reset(player);
                PortalRegionTraversal.movementAccepted(player);
                double height=player.getEyeHeight();
                player.setPos(player.getX(),sourceY+.05-height,player.getZ());PortalRegionTraversal.movementAccepted(player);
                helper.assertTrue(player.level()==helper.getLevel(),"Root crossing must not transfer before eyes");
                var eye=player.getEyePosition().add(0,-.1,0);
                player.setPos(player.getX(),player.getY()-.1,player.getZ());PortalRegionTraversal.movementAccepted(player);
                helper.assertTrue(player.level().dimension()==Level.NETHER,"Eye downward crossing must transfer");
                helper.assertTrue(player.getEyePosition().distanceTo(d.mapPosition(eye))<1e-5,"Eye/root continuity downward");
                helper.assertTrue(Math.abs(player.getYRot()-37)<1e-4 && Math.abs(player.getXRot()+12)<1e-4,"Orientation preserved");
                for(int i=0;i<3;i++)PortalRegionTraversal.movementAccepted(player);
                helper.assertTrue(player.level().dimension()==Level.NETHER,"Arrival must not ping-pong");
                // Clear the full body guard, then advance on a real block pillar in the destination.
                player.hasChangedDimension();player.setPos(player.getX(),targetY-5.5,player.getZ());
                PortalRegionTraversal.movementAccepted(player);
                var destination=server.getLevel(Level.NETHER);
                for(int y=(int)Math.floor(targetY)-5;y<=(int)Math.floor(targetY)+1;y++) {
                    destination.setBlockAndUpdate(new BlockPos(p.getX(),y,p.getZ()),Blocks.STONE.defaultBlockState());
                    player.setPos(player.getX(),y+1,player.getZ());
                    var beforeEye=player.getEyePosition();PortalRegionTraversal.movementAccepted(player);
                    if(player.level()==helper.getLevel()) {
                        helper.assertTrue(player.getEyePosition().distanceTo(d.reversed().mapPosition(beforeEye))<1e-5,"Eye/root continuity upward");
                        break;
                    }
                }
                helper.assertTrue(player.level()==helper.getLevel(),"Pillar eye sweep must transfer upward");
                helper.assertTrue(Math.abs(player.getYRot()-37)<1e-4,"Pillar preserves orientation");
                for(int y=(int)Math.floor(targetY)-5;y<=(int)Math.floor(targetY)+1;y++)
                    destination.setBlockAndUpdate(new BlockPos(p.getX(),y,p.getZ()),Blocks.AIR.defaultBlockState());
            }
            helper.succeed();
        } finally {
            SkyesightPortalRegionApi.remove(server,id);PortalRegionTraversal.reset(player);
            server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
        }
    }
}
