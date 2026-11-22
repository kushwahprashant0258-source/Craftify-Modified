package org.foranly.craftify.client.dtap;

/** The d-tap variants the mod can perform. Each one gets its own key and settings. */
public enum DTapType {
    ELYTRA("elytra", "Elytra D-tap",
            "Glide above the target: normal hit (Breach mace/axe/sword), take the elytra off, "
                    + "then Density mace slam."),
    FOLLOW_UP("follow_up", "Follow-up slam",
            "Density mace slam on the target you aim at (use right after it popped from anything)."),
    SPEAR_MACE("spear_mace", "Spear > Mace D-tap",
            "Spear hit, then swap to the mace and smash. Spear charge is not automated."),
    AERIAL_GRAPPLE("aerial_grapple", "Aerial Grapple",
            "Both of you airborne: aim at the target, throw an Ender Pearl at/below it, "
                    + "then Mace hit as you land on it."),
    FORCE_GRAPPLE("force_grapple", "Force Grapple",
            "Target on the ground: Wind Charge at their feet to launch them, Ender Pearl to meet them "
                    + "mid-air, then Mace hit."),
    GROUND_GRAPPLE("ground_grapple", "Ground Grapple",
            "Fast closing on flat ground: pearl thrown low at the target's feet, then hit "
                    + "as soon as you snap into range.");

    public final String id;
    public final String title;
    public final String description;

    public boolean isGrapple() {
        return this == AERIAL_GRAPPLE || this == FORCE_GRAPPLE || this == GROUND_GRAPPLE;
    }

    DTapType(String id, String title, String description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }
}
