package com.nexusclient.fabric.mixin;

import com.nexusclient.core.online.Emotes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes on 26.3: while a player waves (mod-core Emotes), their right arm is posed after the normal pose. */
@Mixin(PlayerModel.class)
public class EmoteArmMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void nexus$emote(AvatarRenderState state, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!Emotes.anyPlaying() || mc.level == null) {
            return;
        }
        Entity player = mc.level.getEntity(state.id);
        ModelPart rightArm = ((HumanoidModel<?>) (Object) this).rightArm;
        float[] arm = player == null ? null : Emotes.rightArm(player.getUUID(), rightArm.xRot, rightArm.zRot);
        if (arm != null) {
            rightArm.xRot = arm[0];
            rightArm.zRot = arm[1];
        }
    }
}
