package com.nexusclient.forgemodern;

import com.nexusclient.modern.ModernGame;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.ChannelBuilder;

import java.nio.charset.StandardCharsets;

/**
 * NexusForgeModern – where the Nexus mod starts inside Minecraft 1.20.4, 1.20.6 and 1.21.4 on Forge
 * (all three use Forge's "EventBus 6"; 1.21.11 has its own copy of this class for EventBus 7).
 *
 * The game logic is the Fabric build's for the same version (ModernGame and the mixins in
 * mod-fabric-modern); this class only plugs it into Forge's events. The one thing that differs
 * between the three versions – how to draw on the HUD – is in ForgeHud, one file per version.
 */
@Mod("nexusclient")
public class NexusForgeModern {

    private static boolean started;

    public NexusForgeModern() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(NexusForgeModern::setup);
        modBus.addListener(NexusForgeModern::registerKeys);
        // HUD: every switched-on HUD mod (and the custom crosshair), on top of everything else.
        ForgeHud.register(modBus, graphics -> {
            if (started) {
                ModernGame.drawHud(graphics);
            }
        });

        MinecraftForge.EVENT_BUS.addListener(NexusForgeModern::tick);
        MinecraftForge.EVENT_BUS.addListener(NexusForgeModern::attack);
        MinecraftForge.EVENT_BUS.addListener(NexusForgeModern::loggedIn);
        MinecraftForge.EVENT_BUS.addListener(NexusForgeModern::loggedOut);

        // Server-safe system: the server's own rules message (plain JSON text). Optional on both sides.
        ChannelBuilder.named(ForgeHud.id("rules"))
                .optional()
                .eventNetworkChannel()
                .addListener(NexusForgeModern::rulesMessage);
    }

    /** Starts mod-core once the game is set up far enough to read its folders and options. */
    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (!started) {
                ModernGame.start(FMLPaths.GAMEDIR.get().toFile());
                started = true;
            }
        });
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : ModernGame.keys()) {
            event.register(key);
        }
    }

    private static void tick(TickEvent.ClientTickEvent event) {
        if (started && event.phase == TickEvent.Phase.END) {
            ModernGame.tick(Minecraft.getInstance());
        }
    }

    /** Combo counter + PvP stats: count our hits. */
    private static void attack(AttackEntityEvent event) {
        if (started && event.getEntity().level().isClientSide()) {
            ModernGame.attacked(event.getEntity(), event.getTarget());
        }
    }

    private static void loggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (started) {
            ModernGame.joinedServer();
        }
    }

    private static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (started) {
            ModernGame.leftServer();
        }
    }

    private static void rulesMessage(CustomPayloadEvent event) {
        FriendlyByteBuf payload = event.getPayload();
        if (payload != null && started) {
            byte[] bytes = new byte[payload.readableBytes()];
            payload.readBytes(bytes);
            String json = new String(bytes, StandardCharsets.UTF_8);
            event.getSource().enqueueWork(() -> ModernGame.rulesMessage(json));
        }
        event.getSource().setPacketHandled(true);
    }
}
