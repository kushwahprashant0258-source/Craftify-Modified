package org.foranly.craftify.client.dtap;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Builds the heart bar text appended to name tags. */
public final class HealthBar {

    private HealthBar() {
    }

    /** @return the bar to append after the name, or {@code null} if nothing should be shown. */
    public static Component build(LivingEntity entity) {
        DTapConfig.HealthBar cfg = DTapConfig.get().healthBar;
        if (!cfg.enabled) {
            return null;
        }
        if (cfg.playersOnly && !(entity instanceof Player)) {
            return null;
        }
        if (entity == Minecraft.getInstance().player && !cfg.showSelf) {
            return null;
        }
        float max = Math.max(1.0F, entity.getMaxHealth());
        float health = Mth.clamp(entity.getHealth(), 0.0F, max);
        float absorption = cfg.includeAbsorption ? entity.getAbsorptionAmount() : 0.0F;

        MutableComponent bar = Component.empty();
        if (cfg.style == DTapConfig.HealthStyle.HEARTS || cfg.style == DTapConfig.HealthStyle.BOTH) {
            int full = Mth.clamp(Math.round(health / max * 10.0F), health > 0 ? 1 : 0, 10);
            bar.append(Component.literal("♥".repeat(full)).withStyle(ChatFormatting.RED));
            bar.append(Component.literal("♥".repeat(10 - full)).withStyle(ChatFormatting.DARK_GRAY));
            if (absorption > 0.0F) {
                int gold = Mth.clamp((int) Math.ceil(absorption / 2.0F), 1, 10);
                bar.append(Component.literal("♥".repeat(gold)).withStyle(ChatFormatting.GOLD));
            }
        }
        if (cfg.style == DTapConfig.HealthStyle.NUMBER || cfg.style == DTapConfig.HealthStyle.BOTH) {
            String text = " " + format(health) + (absorption > 0.0F ? "+" + format(absorption) : "");
            bar.append(Component.literal(text).withStyle(ChatFormatting.WHITE));
        }
        return bar;
    }

    private static String format(float value) {
        return value == Math.floor(value) ? Integer.toString((int) value) : String.format("%.1f", value);
    }
}
