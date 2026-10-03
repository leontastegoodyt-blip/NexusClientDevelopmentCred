package com.nexusclient.legacy.mixin;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Toggle Sprint/Sneak "hold down" a key (1.8.9 has no public way to do that). */
@Mixin(KeyBinding.class)
public interface KeyBindingAccessor {

    @Accessor("pressed")
    void nexus$setPressed(boolean pressed);
}
