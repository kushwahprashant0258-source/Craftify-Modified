package org.foranly.craftify.client.dtap;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
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
        final long delayNanos;
        final Consumer<Minecraft> action;
        /** Optional: wait (up to {@code timeout} ticks) until this is true before running. */
        Predicate<Minecraft> until;
        long timeoutNanos;

        Step(long delayNanos, Consumer<Minecraft> action) {
            this.delayNanos = delayNanos;
            this.action = action;
        }

        Step waitUntil(Predicate<Minecraft> condition, int timeoutTicks) {
            this.until = condition;
            this.timeoutNanos = timeoutTicks * 50_000_000L;
            return this;
        }
    }

    /** When the previous step ran, and when the head step started waiting for its condition. */
    private static long stepClock;
    private static long waitStarted;

    private static final ArrayDeque<Step> QUEUE = new ArrayDeque<>();
    private static Entity target;
    private static DTapType currentType = DTapType.ELYTRA;
    private static int originalSlot;
    private static boolean restoreSlot;

    /** Toggle key for Auto D-tap. */
    private static KeyMapping autoKey;
    private static boolean autoArmed;
    private static int autoCooldown;
    private static boolean warnedNoTarget;

    private DTapEngine() {
    }

    public static KeyMapping key(DTapType type) {
        return KEYS.get(type);
    }

    public static KeyMapping autoKey() {
        return autoKey;
    }

    public static boolean isAutoArmed() {
        return autoArmed;
    }

    public static void setAutoArmed(boolean armed) {
        autoArmed = armed;
        warnedNoTarget = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            say(mc, "Auto D-tap " + (armed ? "ON" : "OFF"));
        }
    }

    public static void register() {
        for (DTapType type : DTapType.values()) {
            KeyMapping key = new KeyMapping("key.craftify.dtap." + type.id, InputConstants.Type.KEYSYM,
                    InputConstants.UNKNOWN.getValue(), CraftifyClient.CATEGORY);
            KEYS.put(type, KeyBindingHelper.registerKeyBinding(key));
        }
        autoKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.craftify.dtap.auto",
                InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CraftifyClient.CATEGORY));
        DTapConfig.get(); // load settings early
        HealthHud.register();
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
        if (pressed == DTapType.SPEAR_MACE) {
            pressed = null;
            if (mc.screen == null) {
                setSpearArmed(mc, !spearArmed);
            }
        }
        boolean toggled = false;
        while (autoKey.consumeClick()) {
            toggled = !toggled;
        }
        if (toggled && mc.screen == null) {
            setAutoArmed(!autoArmed);
        }
        if (mc.screen != null) {
            QUEUE.clear();
            return;
        }
        if (autoCooldown > 0) {
            autoCooldown--;
        }
        if (QUEUE.isEmpty()) {
            spearTick(mc);
        }
        if (QUEUE.isEmpty()) {
            if (pressed != null) {
                start(mc, pressed);
            } else if (autoArmed && autoCooldown == 0) {
                autoTick(mc);
            }
            return;
        }
        pump(mc);
    }

    /**
     * Runs every step that is due. Called every client tick and also every rendered frame,
     * so gaps shorter than one tick (50 ms) are honoured as closely as the frame rate allows.
     */
    public static void pump(Minecraft mc) {
        if (QUEUE.isEmpty() || mc.player == null || mc.gameMode == null || mc.level == null) {
            return;
        }
        if (mc.screen != null) {
            QUEUE.clear();
            return;
        }
        while (!QUEUE.isEmpty()) {
            Step head = QUEUE.peekFirst();
            long now = System.nanoTime();
            if (now - stepClock < head.delayNanos) {
                return;
            }
            if (head.until != null && !head.until.test(mc)) {
                if (waitStarted == 0) {
                    waitStarted = now;
                }
                if (now - waitStarted < head.timeoutNanos) {
                    return;
                }
            }
            QUEUE.pollFirst();
            waitStarted = 0;
            head.action.accept(mc);
            stepClock = System.nanoTime();
        }
    }

    // ------------------------------------------------------------ spear mode

    private enum SpearPhase { IDLE, CHARGING }

    private static boolean spearArmed;
    private static SpearPhase spearPhase = SpearPhase.IDLE;
    private static int spearTicks;
    private static int spearCooldown;
    private static LivingEntity spearVictim;

    public static boolean isSpearArmed() {
        return spearArmed;
    }

    public static void setSpearArmed(Minecraft mc, boolean armed) {
        spearArmed = armed;
        if (!armed) {
            endSpear(mc, true);
        }
        say(mc, "Spear > Mace D-tap " + (armed ? "ON (falls, spears, then maces)" : "OFF"));
    }

    private static LivingEntity pickAutoVictim(Minecraft mc) {
        String name = DTapConfig.get().auto.targetName.trim();
        LivingEntity best = null;
        double bestDist = 64.0D * 64.0D;
        for (AbstractClientPlayer other : mc.level.players()) {
            if (other == mc.player || !other.isAlive() || other.isSpectator()) {
                continue;
            }
            if (!name.isEmpty() && !other.getGameProfile().name().equalsIgnoreCase(name)) {
                continue;
            }
            double d = other.distanceToSqr(mc.player);
            if (d < bestDist) {
                bestDist = d;
                best = other;
            }
        }
        return best;
    }

    private static void endSpear(Minecraft mc, boolean restoreSlotNow) {
        if (spearPhase == SpearPhase.CHARGING && mc.player != null) {
            mc.options.keyUse.setDown(false);
            if (mc.gameMode != null) {
                mc.gameMode.releaseUsingItem(mc.player);
            }
            if (restoreSlotNow && DTapConfig.get().of(DTapType.SPEAR_MACE).returnToSlot) {
                mc.player.getInventory().setSelectedSlot(originalSlot);
            }
        }
        spearPhase = SpearPhase.IDLE;
        spearVictim = null;
    }

    /**
     * Spear &gt; Mace d-tap (toggle). While armed: when you fall far enough it holds the spear and
     * charges it at the target while aiming; the instant the target is hurt it releases, swaps to
     * the mace and smashes as soon as the target is in reach.
     */
    private static void spearTick(Minecraft mc) {
        if (!spearArmed) {
            return;
        }
        if (spearCooldown > 0) {
            spearCooldown--;
        }
        DTapConfig.TypeSettings cfg = DTapConfig.get().of(DTapType.SPEAR_MACE);
        LocalPlayer player = mc.player;
        if (spearPhase == SpearPhase.IDLE) {
            if (spearCooldown > 0 || player.onGround() || player.isFallFlying()
                    || player.fallDistance <= DTapConfig.get().auto.minFallBlocks) {
                return;
            }
            LivingEntity victim = pickAutoVictim(mc);
            if (victim == null) {
                return;
            }
            int spear = findSlot(mc, s -> s.is(ItemTags.SPEARS), -1);
            if (spear < 0) {
                say(mc, "No spear in your hotbar.");
                spearCooldown = 60;
                return;
            }
            if (densityMaceSlot(mc) < 0) {
                say(mc, "No mace in your hotbar.");
                spearCooldown = 60;
                return;
            }
            originalSlot = player.getInventory().getSelectedSlot();
            player.getInventory().setSelectedSlot(spear);
            spearVictim = victim;
            spearPhase = SpearPhase.CHARGING;
            spearTicks = 0;
            if (cfg.autoAim) {
                aimAt(player, victim, 100);
            }
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            mc.options.keyUse.setDown(true);
            return;
        }
        // CHARGING
        spearTicks++;
        LivingEntity victim = spearVictim;
        if (victim == null || !victim.isAlive() || player.onGround() || spearTicks > 160) {
            endSpear(mc, true);
            spearCooldown = 40;
            return;
        }
        if (cfg.autoAim) {
            aimAt(player, victim, 100);
        }
        if (!player.isUsingItem()) {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        }
        mc.options.keyUse.setDown(true);
        if (victim.hurtTime > 0 && player.distanceTo(victim) < 8.0F) {
            // The spear connected: release, swap to the mace and finish it.
            mc.options.keyUse.setDown(false);
            mc.gameMode.releaseUsingItem(player);
            spearPhase = SpearPhase.IDLE;
            spearVictim = null;
            spearCooldown = 40;
            int mace = densityMaceSlot(mc);
            target = victim;
            currentType = DTapType.SPEAR_MACE;
            restoreSlot = cfg.returnToSlot;
            long gap = Math.round(effectiveGapSeconds(mc, cfg) * 1.0E9D);
            stepClock = System.nanoTime();
            waitStarted = 0;
            QUEUE.add(new Step(gap, m -> hit(m, mace))
                    .waitUntil(m -> target != null && m.player.distanceTo(target) <= m.player.entityInteractionRange() + 0.4D, 30));
            QUEUE.add(new Step(1, DTapEngine::restore));
        }
    }

    // ------------------------------------------------------------- auto mode

    /**
     * Auto D-tap: while armed, aims at the named player and starts the chosen d-tap once the
     * requirements are met (gliding for the elytra type, otherwise falling far enough) and the
     * target is within reach. It does not need the crosshair to be exactly on the target: the
     * server only checks reach, so the aim here mostly makes it look right.
     */
    private static void autoTick(Minecraft mc) {
        DTapConfig.Auto auto = DTapConfig.get().auto;
        LocalPlayer player = mc.player;
        AbstractClientPlayer victim = null;
        if (auto.targetName.isBlank()) {
            // No name set: use the nearest other player.
            double best = 64.0D * 64.0D;
            for (AbstractClientPlayer other : mc.level.players()) {
                if (other == player || !other.isAlive() || other.isSpectator()) {
                    continue;
                }
                double d = other.distanceToSqr(player);
                if (d < best) {
                    best = d;
                    victim = other;
                }
            }
            if (victim == null) {
                status(mc, "no player nearby");
                return;
            }
        } else {
            for (AbstractClientPlayer other : mc.level.players()) {
                if (other != player && other.isAlive()
                        && other.getGameProfile().name().equalsIgnoreCase(auto.targetName.trim())) {
                    victim = other;
                    break;
                }
            }
            if (victim == null) {
                status(mc, "player \"" + auto.targetName.trim() + "\" not found nearby");
                return;
            }
        }
        boolean gliding = player.isFallFlying();
        double dist = Math.sqrt(victim.getBoundingBox().distanceToSqr(player.getEyePosition()));
        boolean ready;
        String need;
        if (auto.type == DTapType.ELYTRA) {
            ready = gliding;
            need = "start gliding with the elytra";
        } else {
            double fallen = player.fallDistance;
            ready = !player.onGround() && !gliding && fallen > auto.minFallBlocks;
            need = "fall " + auto.minFallBlocks + "+ blocks (now " + String.format("%.1f", fallen) + ")";
        }
        if (!ready) {
            status(mc, "waiting: " + need + " | target " + String.format("%.1f", dist) + " blocks");
            return;
        }
        if (auto.aimEnabled) {
            // Snap harder when close so the hit lines up.
            aimAt(player, victim, dist < 8.0D ? Math.max(auto.aimSpeedPercent, 80) : auto.aimSpeedPercent);
        }
        double reach = player.entityInteractionRange() + 0.4D;
        if (dist <= reach && angleError(player, victim) <= 40.0D) {
            status(mc, "FIRE");
            start(mc, auto.type, victim);
            if (!QUEUE.isEmpty()) {
                autoCooldown = 40;
            }
        } else {
            status(mc, "closing in | target " + String.format("%.1f", dist) + " blocks (reach "
                    + String.format("%.1f", reach) + ")");
        }
    }

    private static int statusClock;

    /** Action-bar progress line (every 10 ticks) so you can see what auto mode is waiting for. */
    private static void status(Minecraft mc, String text) {
        if (!DTapConfig.get().auto.showStatus) {
            return;
        }
        if (statusClock++ % 10 == 0) {
            say(mc, "Auto: " + text);
        }
    }

    private static double angleError(LocalPlayer player, Entity target) {
        Vec3 toTarget = target.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
        double dot = Mth.clamp(player.getLookAngle().dot(toTarget), -1.0D, 1.0D);
        return Math.toDegrees(Math.acos(dot));
    }

    private static void aimAt(LocalPlayer player, Entity target, int speedPercent) {
        Vec3 eye = player.getEyePosition();
        // Aim slightly ahead of where the target is moving.
        Vec3 to = target.getBoundingBox().getCenter().add(target.getDeltaMovement());
        double dx = to.x - eye.x;
        double dy = to.y - eye.y;
        double dz = to.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx))) - 90.0F;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        float f = Mth.clamp(speedPercent / 100.0F, 0.05F, 1.0F);
        player.setYRot(player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * f);
        player.setXRot(Mth.clamp(player.getXRot() + (pitch - player.getXRot()) * f, -90.0F, 90.0F));
    }

    // ----------------------------------------------------------------- start

    private static void start(Minecraft mc, DTapType type) {
        start(mc, type, null);
    }

    /** @param forced the entity to hit; {@code null} = the one under the crosshair. */
    private static void start(Minecraft mc, DTapType type, LivingEntity forced) {
        DTapConfig.TypeSettings cfg = DTapConfig.get().of(type);
        LivingEntity living;
        if (type.isGrapple() && forced == null) {
            living = findAimedTarget(mc, 48.0D, 20.0D);
            if (living == null) {
                say(mc, "Look towards a player (within 48 blocks) first.");
                return;
            }
        } else if (forced != null) {
            living = forced;
        } else if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity found) {
            living = found;
        } else if (cfg.autoAim && (living = findAimedTarget(mc, 12.0D, 40.0D)) != null) {
            // auto aim: nobody under the crosshair, so use the player closest to where you look
        } else {
            say(mc, "Aim at a player or mob first.");
            return;
        }
        currentType = type;
        target = living;
        originalSlot = mc.player.getInventory().getSelectedSlot();
        restoreSlot = cfg.returnToSlot;
        long gap = Math.round(effectiveGapSeconds(mc, cfg) * 1.0E9D);
        stepClock = System.nanoTime();
        waitStarted = 0;

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
                QUEUE.add(new Step(gap, m -> hit(m, density))
                        .waitUntil(m -> !m.player.isFallFlying() && m.player.fallDistance > 1.5D, 12));
            }
            case FOLLOW_UP -> {
                if (density < 0) {
                    say(mc, "No mace in your hotbar.");
                    return;
                }
                QUEUE.add(new Step(0, m -> hit(m, density)));
            }
            case AERIAL_GRAPPLE, GROUND_GRAPPLE, FORCE_GRAPPLE -> {
                int pearl = findSlot(mc, st -> st.is(Items.ENDER_PEARL), -1);
                if (pearl < 0) {
                    say(mc, "No Ender Pearl in your hotbar.");
                    return;
                }
                if (density < 0) {
                    say(mc, "No mace in your hotbar.");
                    return;
                }
                if (type == DTapType.FORCE_GRAPPLE) {
                    int wind = findSlot(mc, st -> st.is(Items.WIND_CHARGE), -1);
                    if (wind < 0) {
                        say(mc, "No Wind Charge in your hotbar.");
                        return;
                    }
                    QUEUE.add(new Step(0, m -> throwAt(m, wind, feetPoint(), false)));
                    QUEUE.add(new Step(gap, m -> throwAt(m, pearl, bodyPoint(), true))
                            .waitUntil(m -> target != null && !target.onGround(), 12));
                } else if (type == DTapType.AERIAL_GRAPPLE) {
                    QUEUE.add(new Step(0, m -> throwAt(m, pearl, bodyPoint(), true)));
                } else {
                    QUEUE.add(new Step(0, m -> throwAt(m, pearl, feetPoint(), true)));
                }
                // Wait for the pearl to land us next to the target, then swing the mace.
                QUEUE.add(new Step(gap, m -> hit(m, density))
                        .waitUntil(m -> target != null && m.player.distanceTo(target) <= 4.5F, 60));
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
        if (DTapConfig.get().of(currentType).autoAim) {
            aimAt(mc.player, target, 100);
        }
        mc.player.getInventory().setSelectedSlot(slot);
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    /** Player nearest to where you are looking (within angle/distance), or null. */
    private static LivingEntity findAimedTarget(Minecraft mc, double maxDist, double maxAngle) {
        LivingEntity best = null;
        double bestAngle = maxAngle;
        for (AbstractClientPlayer other : mc.level.players()) {
            if (other == mc.player || !other.isAlive() || other.isSpectator()) {
                continue;
            }
            if (mc.player.distanceTo(other) > maxDist) {
                continue;
            }
            double angle = angleError(mc.player, other);
            if (angle < bestAngle) {
                bestAngle = angle;
                best = other;
            }
        }
        return best;
    }

    private static Vec3 bodyPoint() {
        // Falling target: aim at the top of the hitbox so you land higher and keep more fall time.
        boolean falling = !target.onGround() && target.getDeltaMovement().y < -0.1D;
        double height = target.getBbHeight() * (falling ? 0.95D : 0.35D);
        return target.position().add(0, height, 0).add(target.getDeltaMovement());
    }

    private static Vec3 feetPoint() {
        return target.position().add(target.getDeltaMovement().multiply(1, 0, 1));
    }

    /** Selects the slot, snaps the aim onto the point (pearl arc compensated) and uses the item. */
    private static void throwAt(Minecraft mc, int slot, Vec3 point, boolean arc) {
        if (target == null || !target.isAlive()) {
            QUEUE.clear();
            restore(mc);
            return;
        }
        LocalPlayer player = mc.player;
        Vec3 eye = player.getEyePosition();
        double dx = point.x - eye.x;
        double dz = point.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dy = point.y - eye.y;
        if (arc) {
            // Ender pearl: speed 1.5, gravity 0.03 per tick -> aim a bit higher for the drop.
            double ticks = Math.sqrt(dx * dx + dy * dy + dz * dz) / 1.5D;
            dy += 0.5D * 0.03D * ticks * ticks;
        }
        player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx))) - 90.0F);
        player.setXRot(Mth.clamp((float) (-Math.toDegrees(Math.atan2(dy, horizontal))), -90.0F, 90.0F));
        player.getInventory().setSelectedSlot(slot);
        mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
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

    public static double effectiveGapSeconds(Minecraft mc, DTapConfig.TypeSettings cfg) {
        double gap = cfg.pingGapEnabled ? cfg.pingGapSeconds[DTapConfig.bucketOf(currentPing(mc))] : cfg.gapSeconds;
        return Math.max(0.0, Math.min(2.0, gap));
    }

    private static void say(Minecraft mc, String text) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(PREFIX + text), true);
        }
    }
}
