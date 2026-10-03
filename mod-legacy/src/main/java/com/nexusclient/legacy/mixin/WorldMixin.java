package com.nexusclient.legacy.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Weather changer: shows clear skies, rain or a thunderstorm – only on your screen. */
@Mixin(World.class)
public class WorldMixin {

    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    private void nexus$rain(float offset, CallbackInfoReturnable<Float> cir) {
        String weather = chosenWeather();
        if (weather != null) {
            cir.setReturnValue(weather.equals("Clear") ? 0.0F : 1.0F);
        }
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
    private void nexus$thunder(float offset, CallbackInfoReturnable<Float> cir) {
        String weather = chosenWeather();
        if (weather != null) {
            cir.setReturnValue(weather.equals("Thunder") ? 1.0F : 0.0F);
        }
    }

    /** The weather picked in the mod, or null to leave the real weather alone. */
    private String chosenWeather() {
        // Only change what the player sees, never the singleplayer world's real weather.
        if (!((Object) this instanceof ClientWorld)) {
            return null;
        }
        Mods.WeatherChanger changer = NexusCore.get().get(Mods.WeatherChanger.class);
        return changer.isActive() ? changer.weather.get() : null;
    }
}
