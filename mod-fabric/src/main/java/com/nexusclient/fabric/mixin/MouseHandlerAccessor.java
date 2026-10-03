package com.nexusclient.fabric.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets DFR read the mouse movement that has arrived but hasn't turned the camera yet. */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {

    @Accessor("accumulatedDX")
    double nexus$accumulatedDX();

    @Accessor("accumulatedDY")
    double nexus$accumulatedDY();
}
