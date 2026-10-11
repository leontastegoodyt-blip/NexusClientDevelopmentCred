package com.nexusclient.legacy.mixin;

import com.nexusclient.legacy.LegacyDfr;
import net.minecraft.client.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DFR's Low lag mode reads the mouse movement a moment early (to slide its extra frame); this hands
 * that movement back to the game when it next reads the mouse, so turning stays exactly the same.
 */
@Mixin(MouseInput.class)
public class MouseInputMixin {

    @Shadow public int x;
    @Shadow public int y;

    @Inject(method = "updateMouse", at = @At("TAIL"))
    private void nexus$addBorrowed(CallbackInfo ci) {
        int[] borrowed = LegacyDfr.takeBorrowedMouse();
        x += borrowed[0];
        y += borrowed[1];
    }
}
