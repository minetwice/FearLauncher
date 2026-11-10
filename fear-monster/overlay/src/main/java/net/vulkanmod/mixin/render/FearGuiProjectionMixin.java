package net.vulkanmod.mixin.render;

import net.vulkanmod.vulkan.FearPreRotation;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FEAR: the GUI's projection, turned for pre-rotation.
 *
 * <p>This is the one that matters - the title screen, its buttons and the HUD are what a player
 * cannot use when the image is a quarter turn out.
 *
 * <p>Both the class and the method are named by their intermediary names, on purpose. The mixin
 * config ships without a refMap, so nothing translates the readable names to the ones the game
 * actually loads, and the first attempt failed exactly there:
 *
 *   could not find any targets matching 'getMatrix' in net/minecraft/class_11278
 *
 * <p>net.minecraft.class_11278 is ProjectionMatrix2 and method_71094 is its getMatrix(float,
 * float) - the point where the ortho matrix is built before it is packed into a GPU buffer, and
 * so the last place a Matrix4f exists. Both guiProjectionMatrix and itemsProjectionMatrix are
 * ProjectionMatrix2, so one hook covers both.
 */
@Mixin(targets = "net.minecraft.class_11278")
public abstract class FearGuiProjectionMixin {

    @Inject(method = "method_71094", at = @At("RETURN"), cancellable = true)
    private void fearRotateGui(float width, float height, CallbackInfoReturnable<Matrix4f> cir) {
        cir.setReturnValue(FearPreRotation.apply(cir.getReturnValue()));
    }
}
