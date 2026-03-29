package com.github.bunnyi116.bedrockminer.util;

import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import org.jetbrains.annotations.Nullable;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.networkHandler;
import static com.github.bunnyi116.bedrockminer.BedrockMiner.world;

public class NetworkUtils {

    public static void sendPacket(Packet<?> packet) {
        networkHandler.send(packet);
    }

    public static void sendPacket(Packet<?> packet, @Nullable Runnable beforeSending, @Nullable Runnable afterSending) {
        if (beforeSending != null) beforeSending.run();
        NetworkUtils.sendPacket(packet);
        if (afterSending != null) afterSending.run();
    }

    public static void sendSequencedPacket(PredictiveAction packetCreator, @Nullable Runnable beforeSending, @Nullable Runnable afterSending) {
        try (BlockStatePredictionHandler pendingUpdateManager = world.getBlockStatePredictionHandler()) {
            int i = pendingUpdateManager.currentSequence();
            Packet<ServerGamePacketListener> packet = packetCreator.predict(i);
            NetworkUtils.sendPacket(packet, beforeSending, afterSending);
        }
    }

    public static void sendSequencedPacket(PredictiveAction packetCreator) {
        try (BlockStatePredictionHandler pendingUpdateManager = world.getBlockStatePredictionHandler()) {
            int i = pendingUpdateManager.currentSequence();
            Packet<ServerGamePacketListener> packet = packetCreator.predict(i);
            NetworkUtils.sendPacket(packet);
        }
    }
}
