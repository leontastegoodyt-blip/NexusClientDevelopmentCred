package com.nexusclient.modern;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.nio.charset.StandardCharsets;

/**
 * RulesPayload – the message a server sends on the "nexusclient:rules" channel to switch off Nexus
 * mods it doesn't allow. The message is just JSON text (UTF-8), e.g. {"block": ["minimap"]}, so any
 * server plugin can send it as a plain plugin message. See core/server/ServerRules.
 * (1.21.4 and 1.21.11 have the same file; only the channel id is made differently – see VersionHooks.)
 */
public record RulesPayload(String json) implements CustomPacketPayload {

    public static final Type<RulesPayload> TYPE = new Type<>(VersionHooks.rulesChannel());

    /** Reads the whole message as text (and could write it back, for completeness). */
    public static final StreamCodec<FriendlyByteBuf, RulesPayload> CODEC = CustomPacketPayload.codec(
            (payload, buf) -> buf.writeBytes(payload.json.getBytes(StandardCharsets.UTF_8)),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return new RulesPayload(new String(bytes, StandardCharsets.UTF_8));
            });

    @Override
    public Type<RulesPayload> type() {
        return TYPE;
    }
}
