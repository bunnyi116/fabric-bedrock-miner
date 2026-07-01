package com.github.bunnyi116.bedrockminer.util;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;

public class NetworkUtils {
    public static void sendPacket(Packet<?> packet) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection != null) {
            connection.send(packet);
        }
    }

    public static void sendPacket(PredictiveAction packetCreator) {
        if (level instanceof SequenceExtension sequenceExtension) {
            int currentSequence = sequenceExtension.fabric_bedrock_miner$getSequence();
            Packet<ServerGamePacketListener> packet = packetCreator.predict(currentSequence);
            NetworkUtils.sendPacket(packet);
        }
    }

    public interface SequenceExtension {
        default int fabric_bedrock_miner$getSequence() {
            return 0;
        }
    }
}
