package com.github.bunnyi116.bedrockminer.util.network;

import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.Nullable;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;

public class NetworkUtils {
    public static void sendSequencedPacket(SequencedPacketCreator packetCreator, @Nullable Runnable beforeSending, @Nullable Runnable afterSending) {
        try (PendingUpdateManager pendingUpdateManager = world.getPendingUpdateManager().incrementSequence()) {
            int i = pendingUpdateManager.getSequence();
            Packet<ServerPlayPacketListener> packet = packetCreator.predict(i);
            sendPacket(packet, beforeSending, afterSending);
        }
    }

    public static void sendSequencedPacket(SequencedPacketCreator packetCreator) {
        NetworkUtils.sendSequencedPacket(packetCreator, null, null);
    }

    public static void sendPacket(Packet<?> packet, @Nullable Runnable beforeSending, @Nullable Runnable afterSending) {
        if (beforeSending != null) {
            beforeSending.run();
        }
        networkHandler.sendPacket(packet);
        if (afterSending != null) {
            afterSending.run();
        }
    }
}
