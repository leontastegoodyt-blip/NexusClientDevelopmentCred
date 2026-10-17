package com.nexusclient.forgemodern.mixin;

import com.nexusclient.forgemodern.ForgeHud;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge 1.20.6 only: draws the Nexus HUD after Minecraft has drawn its own (see ForgeHud). */
@Mixin(Gui.class)
public class ForgeHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void nexus$drawHud(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        ForgeHud.draw.accept(graphics);
    }
}
