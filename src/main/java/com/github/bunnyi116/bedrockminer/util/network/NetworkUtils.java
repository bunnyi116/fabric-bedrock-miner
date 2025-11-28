package com.github.bunnyi116.bedrockminer.util.network;

import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.Nullable;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.networkHandler;
import static com.github.bunnyi116.bedrockminer.BedrockMiner.world;

public class NetworkUtils {
    public static void sendSequencedPacket(ClientWorld world, SequencedPacketCreator packetCreator, @Nullable Runnable packetSending, @Nullable Runnable packetSent) {
        //#if MC >= 11900
        try (net.minecraft.client.network.PendingUpdateManager pendingUpdateManager = world.getPendingUpdateManager().incrementSequence()) {
            int i = pendingUpdateManager.getSequence();
            Packet<ServerPlayPacketListener> packet = packetCreator.predict(i);
            sendPacket(packet, packetSending, packetSent);
        }
        //#else
        //$$ Packet<ServerPlayPacketListener> packet = packetCreator.predict(0);
        //$$ sendPacket(packet, packetSending, packetSent);
        //#endif
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
        sendPacket(packet);
        if (packetSent != null) {
            packetSent.run();
        }
    }

    public static void sendPacket(Packet<?> packet) {
        networkHandler.sendPacket(packet);
    }
}
