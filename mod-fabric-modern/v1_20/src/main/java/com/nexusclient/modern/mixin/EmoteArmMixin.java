package com.nexusclient.modern.mixin;

import com.nexusclient.core.online.Emotes;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emotes on 1.20 – 1.20.6: while a player waves (mod-core Emotes), their right arm is posed after
 * Minecraft has set up the normal pose (the sleeve copies the arm right after this).
 */
@Mixin(HumanoidModel.class)
public class EmoteArmMixin {

    @Shadow @Final public ModelPart rightArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void nexus$emote(LivingEntity entity, float limbSwing, float limbSwingAmount, float age,
                             float headYaw, float headPitch, CallbackInfo ci) {
        if (!Emotes.anyPlaying() || !(entity instanceof Player)) {
            return;
        }
        float[] arm = Emotes.rightArm(entity.getUUID(), rightArm.xRot, rightArm.zRot);
        if (arm != null) {
            rightArm.xRot = arm[0];
            rightArm.zRot = arm[1];
        }
    }
}
