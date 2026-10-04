package com.nexusclient.legacy.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Copy Chat (1.8.9): right-clicking a chat line while chat is open copies it. Finds the line with
 * the same maths 1.8.9 uses for clicking links (ChatHud.getTextAt), but takes the whole line.
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void nexus$copyChat(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (button != 1 || !NexusCore.get().get(Mods.CopyChat.class).isActive()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        ChatHud chat = mc.inGameHud.getChatHud();
        ChatHudAccessor access = (ChatHudAccessor) chat;
        List<ChatHudLine> lines = access.nexus$lines();

        // Mouse position in chat units (1.8.9 counts real pixels from the bottom of the window).
        int scaleFactor = new Window(mc).getScaleFactor();
        float chatScale = chat.getChatScale();
        int x = (int) Math.floor((Mouse.getX() / scaleFactor - 3) / chatScale);
        int y = (int) Math.floor((Mouse.getY() / scaleFactor - 27) / chatScale);
        int fontHeight = mc.textRenderer.fontHeight;
        int visible = Math.min(chat.getVisibleLineCount(), lines.size());
        if (x < 0 || y < 0 || x > Math.floor(chat.getWidth() / chatScale) || y >= fontHeight * visible + visible) {
            return; // not over a chat line
        }
        int index = y / fontHeight + access.nexus$scroll();
        if (index < 0 || index >= lines.size()) {
            return;
        }
        Screen.setClipboard(lines.get(index).getText().asUnformattedString());
        mc.inGameHud.setOverlayMessage("Copied chat message", false);
        ci.cancel();
    }
}
