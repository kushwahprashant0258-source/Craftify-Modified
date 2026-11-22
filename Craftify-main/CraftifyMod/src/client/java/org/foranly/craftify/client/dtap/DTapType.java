package org.foranly.craftify.client.dtap;

/** The d-tap variants the mod can perform. Each one gets its own key and settings. */
public enum DTapType {
    ELYTRA("elytra", "Elytra D-tap",
            "Glide above the target: normal hit (Breach mace/axe/sword), take the elytra off, "
                    + "then Density mace slam."),
    FOLLOW_UP("follow_up", "Follow-up slam",
            "Density mace slam on the target you aim at (use right after it popped from anything)."),
    SPEAR_MACE("spear_mace", "Spear > Mace D-tap",
            "Spear hit, then swap to the mace and smash. Spear charge is not automated.");

    public final String id;
    public final String title;
    public final String description;

    DTapType(String id, String title, String description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }
}
