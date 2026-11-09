package net.vulkanmod.mixin.render;

import net.vulkanmod.vulkan.FearPreRotation;

import net.minecraft.client.render.ProjectionMatrix2;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FEAR: the GUI's projection, turned for pre-rotation.
 *
 * <p>This is the one that matters. The world was always going to be fixable; the title screen,
 * its buttons and the whole HUD are what a player actually cannot use when the image is a
 * quarter turn out.
 *
 * <p>getMatrix is where the ortho matrix is built before ProjectionMatrix2 packs it into a
 * GPU buffer, so it is the last point at which a Matrix4f exists. It is private, which a mixin
 * can still reach.
 *
 * <p>Both guiProjectionMatrix and itemsProjectionMatrix are ProjectionMatrix2, so one hook
 * covers both.
 */
@Mixin(ProjectionMatrix2.class)
public abstract class FearGuiProjectionMixin {

    @Inject(method = "getMatrix", at = @At("RETURN"), cancellable = true)
    private void fearRotateGui(float width, float height, CallbackInfoReturnable<Matrix4f> cir) {
        cir.setReturnValue(FearPreRotation.apply(cir.getReturnValue()));
    }
}
