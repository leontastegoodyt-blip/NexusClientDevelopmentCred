package com.nexusclient.legacy.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.nexusclient.core.online.Capes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Nexus capes on 1.8.9: players wearing one get it instead of their normal cape (only on Nexus
 * players' screens). The picture comes from mod-core Capes; the first time it's needed it's turned
 * into a texture.
 */
@Mixin(AbstractClientPlayerEntity.class)
public class CapeMixin {

    private static final Map<String, Identifier> nexus$textures = new HashMap<String, Identifier>();

    @ModifyReturnValue(method = "getCapeId", at = @At("RETURN"))
    private Identifier nexus$cape(Identifier original) {
        String id = Capes.capeOf(((AbstractClientPlayerEntity) (Object) this).getUuid());
        if (id == null) {
            return original;
        }
        Identifier done = nexus$textures.get(id);
        if (done != null) {
            return done;
        }
        byte[] png = Capes.picture(id);
        if (png == null) {
            return original;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            Identifier location = new Identifier("nexusclient", "cosmetics/" + id);
            MinecraftClient.getInstance().getTextureManager().loadTexture(location, new NativeImageBackedTexture(image));
            nexus$textures.put(id, location);
            return location;
        } catch (Exception e) {
            return original;
        }
    }
}
