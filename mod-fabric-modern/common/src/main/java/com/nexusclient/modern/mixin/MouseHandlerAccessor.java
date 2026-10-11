package com.nexusclient.modern.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets DFR's Low lag mode read the mouse movement that hasn't turned the camera yet. */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {

    @Accessor("accumulatedDX")
    double nexus$accumulatedDX();

    @Accessor("accumulatedDY")
    double nexus$accumulatedDY();
}
