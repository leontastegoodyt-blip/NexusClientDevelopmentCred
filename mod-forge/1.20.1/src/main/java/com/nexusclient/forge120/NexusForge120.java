package com.nexusclient.forge120;

import com.nexusclient.modern.ModernGame;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;

import java.nio.charset.StandardCharsets;

/**
 * NexusForge120 – where the Nexus mod starts inside Minecraft 1.20.1 on Forge.
 *
 * The game logic is the Fabric 1.20.1 build's (ModernGame and the mixins in mod-fabric-modern);
 * this class only plugs it into Forge's events. Forge 1.20.1 runs the game with scrambled method
 * and field names, so the finished jar is renamed from Mojang's names to Forge's ("SRG") names –
 * see build.gradle.kts next to this folder.
 */
@Mod("nexusclient")
public class NexusForge120 {

    private static boolean started;

    public NexusForge120() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(NexusForge120::setup);
        modBus.addListener(NexusForge120::registerKeys);
        modBus.addListener(NexusForge120::registerHud);

        MinecraftForge.EVENT_BUS.addListener(NexusForge120::tick);
        MinecraftForge.EVENT_BUS.addListener(NexusForge120::attack);
        MinecraftForge.EVENT_BUS.addListener(NexusForge120::loggedIn);
        MinecraftForge.EVENT_BUS.addListener(NexusForge120::loggedOut);

        // Server-safe system: the server's own rules message (plain JSON text). Optional on both sides.
        NetworkRegistry.newEventChannel(new ResourceLocation("nexusclient", "rules"), () -> "1",
                        version -> true, version -> true)
                .addListener(NexusForge120::rulesMessage);
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

    /** HUD: the custom crosshair and every switched-on HUD mod, on top of everything else. */
    private static void registerHud(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("hud", (gui, graphics, partialTick, width, height) -> {
            if (started) {
                ModernGame.drawHud(graphics);
            }
        });
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

    private static void rulesMessage(NetworkEvent.ServerCustomPayloadEvent event) {
        FriendlyByteBuf payload = event.getPayload();
        if (payload != null && started) {
            byte[] bytes = new byte[payload.readableBytes()];
            payload.readBytes(bytes);
            String json = new String(bytes, StandardCharsets.UTF_8);
            event.getSource().get().enqueueWork(() -> ModernGame.rulesMessage(json));
        }
        event.getSource().get().setPacketHandled(true);
    }
}
