package com.nexusclient.modern;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nexusclient.core.online.Capes;
import com.nexusclient.core.online.CosmeticShapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the Nexus Halo and Neon Wings on players who wear them (1.21.11). The shapes come from
 * mod-core CosmeticShapes; this class only moves to the head/body and hands the corners to
 * Minecraft as "custom geometry", which it draws later in the frame together with everything else.
 */
public class NexusCosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("nexusclient", "textures/cosmetics/white.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    public NexusCosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Minecraft mc = Minecraft.getInstance();
        Entity player = state.isInvisible || mc.level == null ? null : mc.level.getEntity(state.id);
        if (player == null) {
            return;
        }
        String hat = Capes.hatOf(player.getUUID());
        String wings = Capes.wingsOf(player.getUUID());
        if (hat == null && wings == null) {
            return;
        }
        RenderType type = RenderTypes.entityTranslucentEmissive(WHITE);
        PlayerModel model = getParentModel();
        float ticks = state.ageInTicks;
        if (hat != null) {
            poseStack.pushPose();
            model.root().translateAndRotate(poseStack);
            model.head.translateAndRotate(poseStack);
            collector.submitCustomGeometry(poseStack, type, (pose, consumer) -> CosmeticShapes.halo(sink(consumer, pose), ticks));
            poseStack.popPose();
        }
        // No wings while an elytra is worn (they would sit on top of each other).
        if (wings != null && !state.chestEquipment.is(Items.ELYTRA)) {
            boolean flying = !player.onGround();
            poseStack.pushPose();
            model.root().translateAndRotate(poseStack);
            model.body.translateAndRotate(poseStack);
            collector.submitCustomGeometry(poseStack, type, (pose, consumer) -> CosmeticShapes.wings(sink(consumer, pose), ticks, flying));
            poseStack.popPose();
        }
    }

    /** Turns the CosmeticShapes corners (model units, 16 = a block) into vertices in this pose. */
    private static CosmeticShapes.Sink sink(VertexConsumer consumer, PoseStack.Pose pose) {
        Matrix4f matrix = pose.pose();
        Vector3f normal = new Matrix3f(pose.normal()).transform(new Vector3f(0, -1, 0)); // "up", so it's fully lit
        Vector3f point = new Vector3f();
        return (x, y, z, argb) -> {
            matrix.transformPosition(x / 16f, y / 16f, z / 16f, point);
            consumer.addVertex(point.x, point.y, point.z, argb, 0.5f, 0.5f, OverlayTexture.NO_OVERLAY, FULL_BRIGHT,
                    normal.x, normal.y, normal.z);
        };
    }
}
