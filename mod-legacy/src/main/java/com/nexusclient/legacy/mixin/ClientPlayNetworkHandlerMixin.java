package com.nexusclient.legacy.mixin;

import com.nexusclient.core.NexusCore;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.util.PacketByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.charset.Charset;

/**
 * Server-safe system: a server can send its rules (which Nexus mods it doesn't allow) on the
 * "nexusclient:rules" channel as plain JSON text – see core/server/ServerRules and docs/server-rules.md.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    // Runs after Minecraft has moved the message onto the game thread.
    @Inject(method = "onCustomPayload", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/Packet;"
                    + "Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/ThreadExecutor;)V"))
    private void nexus$rules(CustomPayloadS2CPacket packet, CallbackInfo ci) {
        if ("nexusclient:rules".equals(packet.getChannel())) {
            PacketByteBuf buf = packet.getPayload();
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes); // read without using it up
            NexusCore.get().serverRulesMessage(new String(bytes, Charset.forName("UTF-8")));
        }
    }
}
