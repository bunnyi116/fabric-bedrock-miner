package com.github.bunnyi116.bedrockminer.util.network;

import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.Nullable;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.*;

public class NetworkUtils {
    public static void sendSequencedPacket(ClientWorld world, SequencedPacketCreator packetCreator, @Nullable Runnable packetSending, @Nullable Runnable packetSent) {
        try (PendingUpdateManager pendingUpdateManager = world.getPendingUpdateManager().incrementSequence()) {
            int i = pendingUpdateManager.getSequence();
            Packet<ServerPlayPacketListener> packet = packetCreator.predict(i);
            sendPacket(packet, packetSending, packetSent);
        }
    }

    public static void sendSequencedPacket(ClientWorld world, SequencedPacketCreator packetCreator) {
        NetworkUtils.sendSequencedPacket(world, packetCreator, null, null);
    }

    public static void sendSequencedPacket(SequencedPacketCreator packetCreator, @Nullable Runnable packetSending, @Nullable Runnable packetSent) {
        sendSequencedPacket(world, packetCreator, packetSending, packetSent);
    }

    public static void sendSequencedPacket(SequencedPacketCreator packetCreator) {
        NetworkUtils.sendSequencedPacket(packetCreator, null, null);
    }

    public static void sendPacket(Packet<?> packet, @Nullable Runnable packetSending, @Nullable Runnable packetSent) {
        if (packetSending != null) {
            packetSending.run();
        }
        networkHandler.sendPacket(packet);
        if (packetSent != null) {
            packetSent.run();
        }
    }
}
