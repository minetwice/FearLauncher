package net.vulkanmod.mixin.render;

import net.vulkanmod.vulkan.FearPreRotation;

import net.minecraft.client.renderer.GameRenderer;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FEAR: the world's projection, turned for pre-rotation.
 *
 * <p>getBasicProjectionMatrix is public and still returns a Matrix4f in 1.21.11 - unlike
 * RenderSystem.setProjectionMatrix, which now takes a packed GpuBufferSlice and has no
 * Matrix4f to modify.
 *
 * <p>Package note: the engine builds against Mojang mappings, not yarn. GameRenderer lives at
 * net.minecraft.client.renderer, which is what VulkanMod's own GameRendererMixin imports.
 */
@Mixin(GameRenderer.class)
public abstract class FearWorldProjectionMixin {

    @Inject(method = "getBasicProjectionMatrix", at = @At("RETURN"), cancellable = true)
    private void fearRotateWorld(float fovDegrees, CallbackInfoReturnable<Matrix4f> cir) {
        cir.setReturnValue(FearPreRotation.apply(cir.getReturnValue()));
    }
}
