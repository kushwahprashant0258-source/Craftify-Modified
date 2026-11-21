package org.foranly.craftify.server;

import com.mojang.math.Transformation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A non-persistent {@link Display.TextDisplay} used as a floating text above a player.
 *
 * <p>Vanilla keeps most display-entity setters private in these mappings, so this mod uses
 * an access widener ({@code craftify.accesswidener}) to reach them. The entity overrides
 * {@link #shouldBeSaved()} so it is never written to the world (no duplicates after a
 * crash/restart — same guarantee as the Paper plugin's {@code setPersistent(false)}).
 */
public final class CraftifyHologram extends Display.TextDisplay {

    /** Background color used by the plugin holograms: ARGB 120,0,0,0. */
    private static final int BACKGROUND = 0x78000000;

    public CraftifyHologram(ServerLevel level) {
        super(EntityTypes.TEXT_DISPLAY, level);
    }

    /** Applies the visual configuration shared by all Craftify holograms. */
    public void configure(Component text, float scale) {
        setText(text);
        setBackgroundColor(BACKGROUND);
        // See-through on, shadow off, center alignment (the other flag bits unset).
        setFlags(FLAG_SEE_THROUGH);
        setBillboardConstraints(Display.BillboardConstraints.CENTER);
        setViewRange(2.0F);
        setTransformation(new Transformation(
                new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale),
                new Quaternionf()));
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
