package com.jammingdino.web_lib.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Reserved for future GameRenderer hooks.
 *
 * Note: the 1.21 {@code processBlurEffect} injection has been removed because
 * that method does not exist in Minecraft 1.20.1.  Screen-background blur is not
 * a concern on this version.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
}