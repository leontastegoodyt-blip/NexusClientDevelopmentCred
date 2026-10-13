package com.nexusclient.modern;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nexusclient.core.online.Capes;
import com.nexusclient.core.online.CosmeticShapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the Nexus Halo and Neon Wings on players who wear them (1.21.4). The shapes come from
 * mod-core CosmeticShapes; this class only moves to the head/body and turns corners into vertices.
 * They glow (full brightness) and are see-through, using a plain white texture tinted per corner.
 */
public class NexusCosmeticsLayer extends RenderLayer<PlayerRenderState, PlayerModel> {

    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("nexusclient", "textures/cosmetics/white.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    public NexusCosmeticsLayer(RenderLayerParent<PlayerRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, PlayerRenderState state, float yRot, float xRot) {
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
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        PlayerModel model = getParentModel();
        if (hat != null) {
            poseStack.pushPose();
            model.root().translateAndRotate(poseStack);
            model.head.translateAndRotate(poseStack);
            CosmeticShapes.halo(sink(consumer, poseStack.last()), state.ageInTicks);
            poseStack.popPose();
        }
        // No wings while an elytra is worn (they would sit on top of each other).
        if (wings != null && !state.chestEquipment.is(Items.ELYTRA)) {
            poseStack.pushPose();
            model.root().translateAndRotate(poseStack);
            model.body.translateAndRotate(poseStack);
            CosmeticShapes.wings(sink(consumer, poseStack.last()), state.ageInTicks, !player.onGround());
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
