package org.foranly.craftify.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.foranly.craftify.client.dtap.HealthBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Appends a heart bar to the name tag text of living entities (configurable in the
 * Mace D-taps menu). The name tag is stored in the render state while it is extracted.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityNameTagMixin<T extends Entity, S extends EntityRenderState> {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void larper$appendHealthBar(T entity, S state, float partialTick, CallbackInfo ci) {
        if (state.nameTag == null || !(entity instanceof LivingEntity living)) {
            return;
        }
        Component bar = HealthBar.build(living);
        if (bar != null) {
            state.nameTag = Component.empty().append(state.nameTag).append(Component.literal(" ")).append(bar);
        }
    }
}
