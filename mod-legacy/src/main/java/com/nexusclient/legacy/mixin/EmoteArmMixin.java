package com.nexusclient.legacy.mixin;

import com.nexusclient.core.online.Emotes;
import net.minecraft.client.render.entity.model.BiPedModel;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emotes on 1.8.9: while a player waves (mod-core Emotes), their right arm is posed after Minecraft
 * has set up the normal pose (the sleeve copies the arm right after this). In these names, posX/posZ
 * are the part's rotation angles.
 */
@Mixin(BiPedModel.class)
public class EmoteArmMixin {

    @Shadow public ModelPart rightArm;

    @Inject(method = "setAngles(FFFFFFLnet/minecraft/entity/Entity;)V", at = @At("TAIL"))
    private void nexus$emote(float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch,
                             float scale, Entity entity, CallbackInfo ci) {
        if (!Emotes.anyPlaying() || !(entity instanceof PlayerEntity)) {
            return;
        }
        float[] arm = Emotes.rightArm(entity.getUuid(), rightArm.posX, rightArm.posZ);
        if (arm != null) {
            rightArm.posX = arm[0];
            rightArm.posZ = arm[1];
        }
    }
}
