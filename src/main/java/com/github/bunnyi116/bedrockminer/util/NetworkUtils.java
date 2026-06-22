package com.github.bunnyi116.bedrockminer.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class NetworkUtils {
    private static final Minecraft mc = Minecraft.getInstance();

    public static void sendPacket(Packet<?> packet) {
        ClientPacketListener connection = mc.getConnection();
        if (connection != null) {
            connection.send(packet);
        }
    }

    public static void sendPacket(PredictiveAction packetCreator) {
        if (mc.level instanceof SequenceExtension sequenceExtension) {
            int currentSequence = sequenceExtension.fabric_bedrock_miner$getSequence();
            Packet<ServerGamePacketListener> packet = packetCreator.predict(currentSequence);
            NetworkUtils.sendPacket(packet);
        }
    }

    public static void sendLookPacket(LocalPlayer playerEntity, float lookYaw, float lookPitch) {
        playerEntity.connection.send(new ServerboundMovePlayerPacket.Rot(
                lookYaw,
                lookPitch,
                playerEntity.onGround()
                //#if MC > 12101
                , playerEntity.horizontalCollision
                //#endif
        ));
    }

    public static void sendLookPacket(LocalPlayer playerEntity, Look look) {
        sendLookPacket(playerEntity, look.getYaw(), look.getPitch());
    }

    public interface SequenceExtension {
        default int fabric_bedrock_miner$getSequence() {
            return 0;
        }
    }
}
