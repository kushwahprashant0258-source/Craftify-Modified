package org.foranly.craftify.client.dtap;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import org.foranly.craftify.client.CraftifyClient;

/**
 * Mace double-tap ("d-tap") macro: one key press performs
 * 1) mace smash on the entity under the crosshair (pops the first totem),
 * 2) after a short delay, a quick swap to a follow-up weapon and a second hit (pops the second totem),
 * 3) swap back to the slot you started on.
 *
 * You still aim and you still have to be falling (jump / wind charge) for the smash.
 * Single-player or servers that allow it only. Many servers ban combat macros.
 */
public final class DTapMacro {

    private enum Follow { AUTO, MACE, AXE, SWORD }

    private static final String PREFIX = "[Larper🥀] ";

    private static final KeyMapping KEY = new KeyMapping(
            "key.craftify.dtap", InputConstants.Type.KEYSYM,
            org.lwjgl.glfw.GLFW.GLFW_KEY_R, CraftifyClient.CATEGORY);

    // Settings (reset on restart).
    private static int delayTicks = 10;
    private static Follow follow = Follow.AUTO;

    // Running sequence: 0 idle, 1 waiting for follow-up, 2 restoring slot.
    private static int phase = 0;
    private static int wait = 0;
    private static int originalSlot = 0;
    private static int smashSlot = -1;
    private static Entity target = null;

    private DTapMacro() {
    }

    public static void register() {
        KeyBindingHelper.registerKeyBinding(KEY);
        ClientTickEvents.END_CLIENT_TICK.register(DTapMacro::tick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("larper")
                        .then(ClientCommandManager.literal("dtap")
                                .executes(ctx -> {
                                    status(ctx.getSource());
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("delay")
                                        .then(ClientCommandManager.argument("ticks", IntegerArgumentType.integer(0, 40))
                                                .executes(ctx -> {
                                                    delayTicks = IntegerArgumentType.getInteger(ctx, "ticks");
                                                    status(ctx.getSource());
                                                    return 1;
                                                })))
                                .then(ClientCommandManager.literal("follow")
                                        .then(followLiteral(Follow.AUTO))
                                        .then(followLiteral(Follow.MACE))
                                        .then(followLiteral(Follow.AXE))
                                        .then(followLiteral(Follow.SWORD))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> followLiteral(Follow f) {
        return ClientCommandManager.literal(f.name().toLowerCase()).executes(ctx -> {
            follow = f;
            status(ctx.getSource());
            return 1;
        });
    }

    private static void status(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal(PREFIX + "D-tap: key R | delay " + delayTicks
                + " ticks | follow-up " + follow.name().toLowerCase()
                + ". Change: /larper dtap delay <0-40>, /larper dtap follow <auto|mace|axe|sword>"));
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null || mc.screen != null) {
            phase = 0;
            target = null;
            return;
        }
        switch (phase) {
            case 0 -> {
                if (KEY.consumeClick()) {
                    start(mc);
                }
            }
            case 1 -> {
                if (wait > 0) {
                    wait--;
                    return;
                }
                followUp(mc);
            }
            case 2 -> {
                mc.player.getInventory().setSelectedSlot(originalSlot);
                phase = 0;
                target = null;
            }
            default -> phase = 0;
        }
    }

    private static void start(Minecraft mc) {
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity living)) {
            mc.player.displayClientMessage(Component.literal(PREFIX + "Aim at a player or mob first."), true);
            return;
        }
        int mace = findSlot(mc, -1, Follow.MACE);
        if (mace < 0) {
            mc.player.displayClientMessage(Component.literal(PREFIX + "No mace in your hotbar."), true);
            return;
        }
        originalSlot = mc.player.getInventory().getSelectedSlot();
        smashSlot = mace;
        target = living;
        mc.player.getInventory().setSelectedSlot(mace);
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        wait = delayTicks;
        phase = 1;
    }

    private static void followUp(Minecraft mc) {
        int slot = findSlot(mc, smashSlot, follow);
        if (slot < 0) {
            mc.player.displayClientMessage(Component.literal(PREFIX + "No follow-up weapon in your hotbar."), true);
            phase = 2;
            return;
        }
        if (target == null || !target.isAlive() || mc.player.distanceTo(target) > 4.5f) {
            phase = 2;
            return;
        }
        mc.player.getInventory().setSelectedSlot(slot);
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
        phase = 2;
    }

    /** Finds a hotbar slot for the wanted weapon, never returning {@code exclude}. */
    private static int findSlot(Minecraft mc, int exclude, Follow want) {
        if (want == Follow.AUTO) {
            for (Follow f : new Follow[]{Follow.MACE, Follow.AXE, Follow.SWORD}) {
                int s = findSlot(mc, exclude, f);
                if (s >= 0) {
                    return s;
                }
            }
            return -1;
        }
        for (int i = 0; i < 9; i++) {
            if (i == exclude) {
                continue;
            }
            ItemStack stack = mc.player.getInventory().getItem(i);
            boolean match = switch (want) {
                case MACE -> stack.is(Items.MACE);
                case AXE -> stack.is(ItemTags.AXES);
                case SWORD -> stack.is(ItemTags.SWORDS);
                default -> false;
            };
            if (match) {
                return i;
            }
        }
        return -1;
    }
}
