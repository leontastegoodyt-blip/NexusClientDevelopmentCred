package com.nexusclient.legacy.mixin;

import com.nexusclient.core.NexusCore;
import com.nexusclient.core.module.Mods;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import net.minecraft.world.level.LevelProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Weather and time changers: change the sky – only on your screen. */
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

    /**
     * Time changer: the sun/moon position, moon phase and sky darkness all read the time here.
     * Only the player's own (client) world is changed – never the real world in singleplayer.
     */
    @Redirect(method = {"getSkyAngle", "getMoonPhase", "getMoonSize"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelProperties;getTimeOfDay()J"))
    private long nexus$timeChanger(LevelProperties properties) {
        long realTime = properties.getTimeOfDay();
        if (!((Object) this instanceof ClientWorld)) {
            return realTime;
        }
        return NexusCore.get().get(Mods.TimeChanger.class).apply(realTime);
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
