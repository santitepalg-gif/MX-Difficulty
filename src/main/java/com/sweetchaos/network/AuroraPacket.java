package com.sweetchaos.network;

import com.sweetchaos.client.AuroraClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record AuroraPacket(boolean active) {

    public static void encode(AuroraPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
    }

    public static AuroraPacket decode(FriendlyByteBuf buf) {
        return new AuroraPacket(buf.readBoolean());
    }

    public static void handle(AuroraPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AuroraClient.setActive(msg.active())));
        ctx.get().setPacketHandled(true);
    }
}
