package com.jammingdino.web_lib.mixin;

import com.jammingdino.web_lib.screen.WebLibConfigScreen;
import com.jammingdino.web_lib.screen.WebScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the background blur shader whenever a web_lib screen is open.
 *
 * Injects at the HEAD of processBlurEffect - the dedicated method Mojang
 * added in 1.21 that runs the Gaussian blur pass behind menus. Cancelling
 * it here means no blur is ever written to the framebuffer before our GUI.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "processBlurEffect", at = @At("HEAD"), cancellable = true)
    private void weblib_cancelBlur(float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof WebLibConfigScreen || mc.screen instanceof WebScreen) {
            ci.cancel();
        }
    }
}