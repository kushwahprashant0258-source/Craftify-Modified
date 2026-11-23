package org.foranly.craftify.client.dtap;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Floating live health display above players (like the "Health Indicator" mods).
 * Drawn on the HUD by projecting the entity's head position onto the screen, so it is
 * always visible (through walls too), updates every frame, and does not depend on the
 * vanilla name tag.
 */
public final class HealthHud {

    private static final double MAX_DISTANCE = 64.0;

    private HealthHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("craftify", "health_hud"),
                (extractor, deltaTracker) -> {
                    DTapEngine.pump(Minecraft.getInstance());
                    try {
                        render(extractor, deltaTracker.getGameTimeDeltaPartialTick(false));
                    } catch (Throwable ignored) {
                        // never crash the HUD
                    }
                });
    }

    private static void render(net.minecraft.client.gui.GuiGraphics g, float partial) {
        Minecraft mc = Minecraft.getInstance();
        DTapConfig.HealthBar cfg = DTapConfig.get().healthBar;
        if (!cfg.enabled || mc.level == null || mc.player == null || mc.options.hideGui) {
            return;
        }
        Player me = mc.player;
        Vec3 eye = me.getEyePosition(partial);
        double yaw = Math.toRadians(me.getViewYRot(partial));
        double pitch = Math.toRadians(me.getViewXRot(partial));
        double cp = Math.cos(pitch);
        Vec3 fwd = new Vec3(-Math.sin(yaw) * cp, -Math.sin(pitch), Math.cos(yaw) * cp);
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 up = right.cross(fwd);

        int w = g.guiWidth();
        int h = g.guiHeight();
        double tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        double aspect = (double) w / h;
        Font font = mc.font;

        for (Player p : mc.level.players()) {
            if (p == me && !cfg.showSelf) {
                continue;
            }
            if (p.isSpectator() || p.isRemoved()) {
                continue;
            }
            Component bar = HealthBar.build(p);
            if (bar == null) {
                continue;
            }
            Vec3 pos = new Vec3(Mth.lerp(partial, p.xOld, p.getX()),
                    Mth.lerp(partial, p.yOld, p.getY()) + p.getBbHeight() + 0.75,
                    Mth.lerp(partial, p.zOld, p.getZ()));
            Vec3 d = pos.subtract(eye);
            if (d.lengthSqr() > MAX_DISTANCE * MAX_DISTANCE) {
                continue;
            }
            double z = d.dot(fwd);
            if (z < 0.1) {
                continue;
            }
            double sx = w / 2.0 + d.dot(right) / (z * tanHalf * aspect) * (w / 2.0);
            double sy = h / 2.0 - d.dot(up) / (z * tanHalf) * (h / 2.0);
            if (sx < -100 || sx > w + 100 || sy < -50 || sy > h + 50) {
                continue;
            }
            int width = font.width(bar);
            g.drawString(font, bar, (int) sx - width / 2, (int) sy, 0xFFFFFFFF);
        }
    }
}
