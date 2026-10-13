package com.nexusclient.forge;

import com.nexusclient.fabric.NexusGame;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.ChannelBuilder;

import java.nio.charset.StandardCharsets;

/**
 * NexusForge – where the Nexus mod starts inside Minecraft 26.3 on Forge.
 *
 * Minecraft 26.3 isn't scrambled, so Forge runs it with the same names as Fabric: this mod is the
 * Fabric 26.3 mod's code (mod-fabric, incl. its mixins) with this class instead of NexusFabric. It
 * plugs NexusGame into Forge's events: keys, HUD layers, the crosshair, attacks, joining/leaving
 * servers, the server-rules channel and the game tick.
 */
@Mod("nexusclient")
public class NexusForge {

    private static final Identifier HUD = Identifier.fromNamespaceAndPath("nexusclient", "hud");
    private static final Identifier CROSSHAIR = Identifier.fromNamespaceAndPath("nexusclient", "crosshair");
    private static boolean started;

    public NexusForge(FMLJavaModLoadingContext context) {
        // Start mod-core once the game is set up far enough to read its folders and options.
        FMLClientSetupEvent.getBus(context.getModBusGroup()).addListener(event -> event.enqueueWork(NexusForge::start));

        RegisterKeyMappingsEvent.BUS.addListener(event -> {
            for (KeyMapping key : NexusGame.keys()) {
                event.register(key);
            }
        });

        AddGuiOverlayLayersEvent.BUS.addListener(event -> {
            ForgeLayeredDraw draw = event.getLayeredDraw();
            // HUD: every switched-on HUD mod, on top of the normal game HUD.
            draw.add(HUD, (graphics, delta) -> {
                if (started) {
                    NexusGame.drawHud(graphics);
                }
            });
            // Custom crosshair: Minecraft's own one is hidden while the Nexus one is drawn.
            draw.locateStack(ForgeLayeredDraw.CROSSHAIR).ifPresent(stack -> {
                stack.addConditionTo(ForgeLayeredDraw.CROSSHAIR, () -> !started || !NexusGame.customCrosshair());
                stack.addAbove(CROSSHAIR, ForgeLayeredDraw.CROSSHAIR, (graphics, delta) -> {
                    if (started && NexusGame.customCrosshair()) {
                        NexusGame.drawCrosshair(graphics);
                    }
                });
            });
        });

        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> {
            if (started) {
                NexusGame.tick(Minecraft.getInstance());
            }
        });

        // Combo counter + PvP stats: count our hits (false = don't cancel the attack).
        AttackEntityEvent.BUS.addListener(event -> {
            if (started && event.getEntity().level().isClientSide()) {
                NexusGame.attacked(event.getEntity(), event.getTarget());
            }
            return false;
        });

        // Server-safe system: which server we're on, and the server's own rules message.
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(event -> {
            if (started) {
                NexusGame.joinedServer();
            }
        });
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> {
            if (started) {
                NexusGame.leftServer();
            }
        });
        ChannelBuilder.named(Identifier.fromNamespaceAndPath("nexusclient", "rules"))
                .optional() // servers without the Nexus rules channel are fine
                .eventNetworkChannel()
                .addListener(event -> {
                    FriendlyByteBuf payload = event.getPayload();
                    if (payload != null && started) {
                        byte[] bytes = new byte[payload.readableBytes()];
                        payload.readBytes(bytes);
                        String json = new String(bytes, StandardCharsets.UTF_8);
                        event.getSource().enqueueWork(() -> NexusGame.rulesMessage(json));
                    }
                    event.getSource().setPacketHandled(true);
                });
    }

    private static void start() {
        if (!started) {
            NexusGame.start(FMLPaths.GAMEDIR.get().toFile());
            started = true;
        }
    }
}
