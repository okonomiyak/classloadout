package uk.iwaservice.classloadout.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import uk.iwaservice.classloadout.resupply.AbstractResupplyPackEntity;

import java.util.function.Supplier;

/**
 * No 3D model - just a slowly spinning, bobbing render of the pack's own
 * item icon (the same technique squadtp's {@code RespawnBeaconRenderer}
 * uses), plus the standard nametag/hurt-flash from {@link EntityRenderer}.
 * One class shared by both pack types via the {@code iconStack} supplier -
 * only the icon differs.
 */
public class ResupplyPackRenderer<T extends AbstractResupplyPackEntity> extends EntityRenderer<T> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/misc/particles.png");

    private final Supplier<ItemStack> iconStack;

    public ResupplyPackRenderer(EntityRendererProvider.Context context, Supplier<ItemStack> iconStack) {
        super(context);
        this.iconStack = iconStack;
        this.shadowRadius = 0.3f;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return TEXTURE; // never sampled: render() below draws an item stack instead of a textured quad
    }

    @Override
    public void render(T entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        double bob = Math.sin((entity.tickCount + partialTicks) / 10.0) * 0.1;
        poseStack.translate(0.0, 0.7 + bob, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTicks) * 2.0f));
        poseStack.scale(1.25f, 1.25f, 1.25f);

        Minecraft.getInstance().getItemRenderer().renderStatic(iconStack.get(), ItemDisplayContext.GROUND,
                packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}
