package com.nexusclient.legacy.mixin;

import com.nexusclient.legacy.LegacyKeys;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

/**
 * Adds Nexus's keys (Right Shift = menu, C = zoom, F8 = clip) to Minecraft's key list, just before
 * options.txt is read – so players can change them in Controls and their choice is saved.
 */
@Mixin(GameOptions.class)
public class GameOptionsMixin {

    @Shadow
    public KeyBinding[] allKeys;

    @Inject(method = "<init>(Lnet/minecraft/client/MinecraftClient;Ljava/io/File;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/GameOptions;load()V"))
    private void nexus$addKeys(MinecraftClient client, File folder, CallbackInfo ci) {
        allKeys = ArrayUtils.addAll(allKeys, LegacyKeys.create());
    }
}
