package org.foranly.craftify.client.dtap;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;
import org.foranly.craftify.client.CraftifyClient;
import org.foranly.craftify.client.gui.MaceDTapsScreen;

/**
 * Performs the mace d-taps. One key per type (bind them in the Mace D-taps menu or in
 * Options &gt; Controls). A press starts a short scripted sequence of slot swaps and attacks.
 *
 * <p>You still aim, glide/fall and choose the moment. Combat macros are banned on many servers.
 */
public final class DTapEngine {

    private static final String PREFIX = "[Larper🥀] ";
    private static final Map<DTapType, KeyMapping> KEYS = new EnumMap<>(DTapType.class);

    /** One scripted action, run once {@code delay} ticks have passed since the previous step. */
    private static final class Step {
        int delay;
        final Consumer<Minecraft> action;

        Step(int delay, Consumer<Minecraft> action) {
            this.delay = delay;
            this.action = action;
        }
    }

    private static final ArrayDeque<Step> QUEUE = new ArrayDeque<>();
    private static Entity target;
    private static int originalSlot;
    private static boolean restoreSlot;

    private DTapEngine() {
    }

    public static KeyMapping key(DTapType type) {
        return KEYS.get(type);
    }

    public static void register() {
        for (DTapType type : DTapType.values()) {
            KeyMapping key = new KeyMapping("key.craftify.dtap." + type.id, InputConstants.Type.KEYSYM,
                    InputConstants.UNKNOWN.getValue(), CraftifyClient.CATEGORY);
            KEYS.put(type, KeyBindingHelper.registerKeyBinding(key));
        }
        DTapConfig.get(); // load settings early
        ClientTickEvents.END_CLIENT_TICK.register(DTapEngine::tick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("larper")
                        .then(ClientCommandManager.literal("dtap")
                                .executes(ctx -> {
                                    Minecraft.getInstance().setScreen(new MaceDTapsScreen(null));
                                    return 1;
                                }))));
    }

    // ------------------------------------------------------------------ tick

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null || mc.level == null) {
            QUEUE.clear();
            return;
        }
        // Always drain key presses so they do not pile up while a sequence runs.
        DTapType pressed = null;
        for (DTapType type : DTapType.values()) {
            KeyMapping key = KEYS.get(type);
            while (key.consumeClick()) {
                if (DTapConfig.get().of(type).enabled) {
                    pressed = type;
                }
            }
        }
        if (mc.screen != null) {
            QUEUE.clear();
            return;
        }
        if (QUEUE.isEmpty()) {
            if (pressed != null) {
                start(mc, pressed);
            }
            return;
        }
        // Run every step that is due; a delay of 0 runs in the same tick as the previous step.
        while (!QUEUE.isEmpty()) {
            Step head = QUEUE.peekFirst();
            if (head.delay > 0) {
                head.delay--;
                return;
            }
            QUEUE.pollFirst();
            head.action.accept(mc);
        }
    }

    // ----------------------------------------------------------------- start

    private static void start(Minecraft mc, DTapType type) {
        DTapConfig.TypeSettings cfg = DTapConfig.get().of(type);
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity living)) {
            say(mc, "Aim at a player or mob first.");
            return;
        }
        target = living;
        originalSlot = mc.player.getInventory().getSelectedSlot();
        restoreSlot = cfg.returnToSlot;
        int gap = effectiveGap(mc, cfg);

        int density = densityMaceSlot(mc);
        switch (type) {
            case ELYTRA -> {
                int first = firstWeaponSlot(mc, cfg.firstWeapon, density);
                if (density < 0) {
                    say(mc, "No Density mace (or any mace) in your hotbar.");
                    return;
                }
                if (first < 0) {
                    say(mc, "No first-hit weapon (Breach mace / axe / sword) in your hotbar.");
                    return;
                }
                QUEUE.add(new Step(0, m -> hit(m, first)));
                QUEUE.add(new Step(0, m -> takeOffElytra(m, cfg.elytraOff)));
                QUEUE.add(new Step(gap, m -> hit(m, density)));
            }
            case FOLLOW_UP -> {
                if (density < 0) {
                    say(mc, "No mace in your hotbar.");
                    return;
                }
                QUEUE.add(new Step(0, m -> hit(m, density)));
            }
            case SPEAR_MACE -> {
                int spear = findSlot(mc, s -> s.is(ItemTags.SPEARS), -1);
                if (spear < 0) {
                    say(mc, "No spear in your hotbar.");
                    return;
                }
                if (density < 0) {
                    say(mc, "No mace in your hotbar.");
                    return;
                }
                QUEUE.add(new Step(0, m -> hit(m, spear)));
                QUEUE.add(new Step(gap, m -> hit(m, density)));
            }
        }
        QUEUE.add(new Step(1, DTapEngine::restore));
    }

    // ------------------------------------------------------------- actions

    private static void hit(Minecraft mc, int slot) {
        if (target == null || !target.isAlive() || mc.player.distanceTo(target) > 6.0F) {
            QUEUE.clear();
            restore(mc);
            return;
        }
        mc.player.getInventory().setSelectedSlot(slot);
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private static void restore(Minecraft mc) {
        if (restoreSlot && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
        }
        target = null;
    }

    /** Right-clicks a chestplate so the game swaps the elytra off. */
    private static void takeOffElytra(Minecraft mc, DTapConfig.ElytraOff mode) {
        if (mode == DTapConfig.ElytraOff.NONE) {
            return;
        }
        ItemStack offhand = mc.player.getOffhandItem();
        boolean offhandChest = isChestplate(offhand);
        if ((mode == DTapConfig.ElytraOff.AUTO || mode == DTapConfig.ElytraOff.OFFHAND_CHESTPLATE) && offhandChest) {
            mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
            return;
        }
        if (mode == DTapConfig.ElytraOff.AUTO || mode == DTapConfig.ElytraOff.HOTBAR_CHESTPLATE) {
            int slot = findSlot(mc, DTapEngine::isChestplate, -1);
            if (slot >= 0) {
                mc.player.getInventory().setSelectedSlot(slot);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                return;
            }
        }
        say(mc, "No chestplate found to take the elytra off.");
    }

    // --------------------------------------------------------------- slots

    private static boolean isChestplate(ItemStack stack) {
        return stack.is(ItemTags.CHEST_ARMOR) && !stack.is(Items.ELYTRA);
    }

    private interface StackTest {
        boolean test(ItemStack stack);
    }

    private static int findSlot(Minecraft mc, StackTest test, int exclude) {
        for (int i = 0; i < 9; i++) {
            if (i != exclude && test.test(mc.player.getInventory().getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int level(Minecraft mc, ItemStack stack, ResourceKey<Enchantment> key) {
        Holder<Enchantment> holder = mc.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
    }

    /** Mace with the highest Density level (ties: lowest slot); -1 if there is no mace. */
    private static int densityMaceSlot(Minecraft mc) {
        int best = -1;
        int bestLevel = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.MACE)) {
                int lvl = level(mc, stack, Enchantments.DENSITY);
                if (lvl > bestLevel) {
                    best = i;
                    bestLevel = lvl;
                }
            }
        }
        return best;
    }

    /** Mace with Breach (not the density mace); else any other mace; -1 if none. */
    private static int breachMaceSlot(Minecraft mc, int densitySlot) {
        int best = -1;
        int bestLevel = 0;
        int any = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (i == densitySlot || !stack.is(Items.MACE)) {
                continue;
            }
            if (any < 0) {
                any = i;
            }
            int lvl = level(mc, stack, Enchantments.BREACH);
            if (lvl > bestLevel) {
                best = i;
                bestLevel = lvl;
            }
        }
        return best >= 0 ? best : any;
    }

    private static int firstWeaponSlot(Minecraft mc, DTapConfig.FirstWeapon want, int densitySlot) {
        return switch (want) {
            case BREACH_MACE -> breachMaceSlot(mc, densitySlot);
            case AXE -> findSlot(mc, s -> s.is(ItemTags.AXES), -1);
            case SWORD -> findSlot(mc, s -> s.is(ItemTags.SWORDS), -1);
            case AUTO -> {
                int slot = breachMaceSlot(mc, densitySlot);
                if (slot < 0) {
                    slot = findSlot(mc, s -> s.is(ItemTags.AXES), -1);
                }
                if (slot < 0) {
                    slot = findSlot(mc, s -> s.is(ItemTags.SWORDS), -1);
                }
                yield slot;
            }
        };
    }

    // ------------------------------------------------------------ ping / ui

    public static int currentPing(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) {
            return 0;
        }
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    public static int effectiveGap(Minecraft mc, DTapConfig.TypeSettings cfg) {
        int gap = cfg.pingGapEnabled ? cfg.pingGaps[DTapConfig.bucketOf(currentPing(mc))] : cfg.gapTicks;
        return Math.max(0, Math.min(40, gap));
    }

    private static void say(Minecraft mc, String text) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(PREFIX + text), true);
        }
    }
}
