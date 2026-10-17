package com.nexusclient.forgemodern;

import com.nexusclient.modern.ModernGame;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.ChannelBuilder;

import java.nio.charset.StandardCharsets;

/**
 * NexusForgeModern (Forge 1.21.11) – where the Nexus mod starts inside Minecraft 1.21.11 on Forge.
 *
 * Replaces the shared copy in mod-forge/modern: Forge for 1.21.11 uses "EventBus 7", where each event
 * has its own BUS (like Forge 26.x). The game logic is the Fabric 1.21.11 build's (ModernGame and
 * the mixins in mod-fabric-modern); the custom crosshair is handled there too (GuiMixin + drawHud).
 */
@Mod("nexusclient")
public class NexusForgeModern {

    private static boolean started;

    public NexusForgeModern(FMLJavaModLoadingContext context) {
        // Start mod-core once the game is set up far enough to read its folders and options.
        FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(event -> event.enqueueWork(NexusForgeModern::start));

        RegisterKeyMappingsEvent.BUS.addListener(event -> {
            for (KeyMapping key : ModernGame.keys()) {
                event.register(key);
            }
        });

        // HUD: every switched-on HUD mod (and the custom crosshair), on top of the normal game HUD.
        AddGuiOverlayLayersEvent.BUS.addListener(event -> event.getLayeredDraw().add(id("hud"), (graphics, delta) -> {
            if (started) {
                ModernGame.drawHud(graphics);
            }
        }));

        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> {
            if (started) {
                ModernGame.tick(Minecraft.getInstance());
            }
        });

        // Combo counter + PvP stats: count our hits (false = don't cancel the attack).
        AttackEntityEvent.BUS.addListener(event -> {
            if (started && event.getEntity().level().isClientSide()) {
                ModernGame.attacked(event.getEntity(), event.getTarget());
            }
            return false;
        });

        // Server-safe system: which server we're on, and the server's own rules message.
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(event -> {
            if (started) {
                ModernGame.joinedServer();
            }
        });
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> {
            if (started) {
                ModernGame.leftServer();
            }
        });
        ChannelBuilder.named(id("rules"))
                .optional() // servers without the Nexus rules channel are fine
                .eventNetworkChannel()
                .addListener(event -> {
                    FriendlyByteBuf payload = event.getPayload();
                    if (payload != null && started) {
                        byte[] bytes = new byte[payload.readableBytes()];
                        payload.readBytes(bytes);
                        String json = new String(bytes, StandardCharsets.UTF_8);
                        event.getSource().enqueueWork(() -> ModernGame.rulesMessage(json));
                    }
                    event.getSource().setPacketHandled(true);
                });
    }

    private static void start() {
        if (!started) {
            ModernGame.start(FMLPaths.GAMEDIR.get().toFile());
            started = true;
        }
    }

    /** "nexusclient:<path>" */
    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("nexusclient", path);
    }
}
