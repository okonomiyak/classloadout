package uk.iwaservice.classloadout.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;

/**
 * Renders the pack's own item model - same technique as squadtp's
 * {@code RespawnBeaconRenderer} (draw the item's registered model via
 * {@link net.minecraft.client.renderer.entity.ItemRenderer}), plus the
 * standard nametag/hurt-flash from {@link EntityRenderer}. The item's model
 * is a full Blockbench box (see {@code models/item/health_pack.json} /
 * {@code ammo_pack.json}), not a flat icon, so this renders as a real 3D
 * prop sitting on the ground rather than a floating icon. Generic over any
 * placed entity that should render this way - shared by the resupply packs
 * and {@code CoverEntity} alike; only the item differs.
 *
 * <p>{@code ItemRenderer.render(...)} unconditionally re-translates by
 * (-0.5,-0.5,-0.5) to center a [0,1]-space item/block model on the render
 * origin. That alone centers X/Z correctly (the entity origin is already
 * horizontally centered), but sinks the model half a block into the ground
 * on Y, so only Y is pre-compensated here, back up to 0.
 */
public class ResupplyPackRenderer<T extends Entity> extends EntityRenderer<T> {

    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/misc/particles.png");

    private final RegistryObject<Item> item;

    public ResupplyPackRenderer(EntityRendererProvider.Context context, RegistryObject<Item> item) {
        super(context);
        this.item = item;
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
        poseStack.translate(0.0, 0.5, 0.0);

        ItemStack stack = new ItemStack(item.get());
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED,
                packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }
}
