package com.vaultsniper;

import com.vaultsniper.mixin.SharedDataAccessor;
import com.vaultsniper.mixin.VaultBlockEntityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.phys.BlockHitResult;
import net.minecraft.phys.HitResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.VaultBlockEntity;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class VaultSniper {
    private static final VaultSniper INSTANCE = new VaultSniper();
    public static VaultSniper getInstance() { return INSTANCE; }

    private boolean active = false;
    private final List<String> targetItems = new CopyOnWriteArrayList<>();

    // Reaction delay: 1-4 ticks (50-200ms) — looks human, avoids 0-tick detection
    private int clickCooldown = 0;
    private String pendingClick = null; // item we matched and are about to click for

    private VaultSniper() {}

    public void toggle() {
        active = !active;
        if (!active) { pendingClick = null; clickCooldown = 0; }
        System.out.println("[VaultSniper] " + (active ? "ACTIVE" : "DISABLED"));
    }

    public boolean isActive() { return active; }

    public void addTarget(String itemId) {
        String norm = normalise(itemId);
        if (!norm.isEmpty() && !targetItems.contains(norm)) {
            targetItems.add(norm);
        }
    }

    public void removeTarget(String itemId) { targetItems.remove(normalise(itemId)); }
    public List<String> getTargets() { return Collections.unmodifiableList(targetItems); }
    public void clearTargets() { targetItems.clear(); }

    /** Called every client tick */
    public void tick(Minecraft mc) {
        if (!active || mc.player == null || mc.level == null || mc.gameMode == null) return;

        // Count down reaction delay
        if (clickCooldown > 0) {
            clickCooldown--;
            if (clickCooldown == 0 && pendingClick != null) {
                doClick(mc);
                pendingClick = null;
            }
            return;
        }

        // Player must be looking at a vault block
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr)) return;
        if (bhr.getType() == HitResult.Type.MISS) return;

        BlockPos pos = bhr.getBlockPos();
        var state = mc.level.getBlockState(pos);
        if (!state.is(Blocks.VAULT) && !state.is(Blocks.OMINOUS_VAULT)) return;

        // Get the vault's displayed item
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof VaultBlockEntity vault)) return;

        VaultBlockEntity.SharedData shared = ((VaultBlockEntityAccessor) vault).vs_getSharedData();
        if (shared == null) return;

        ItemStack displayed = ((SharedDataAccessor)(Object)shared).vs_getDisplayItem();
        if (displayed == null || displayed.isEmpty()) return;

        String displayedId = itemId(displayed);
        if (!isTargeted(displayedId)) return;

        // Match! Queue a click with a small human-like reaction delay (1-4 ticks)
        pendingClick = displayedId;
        clickCooldown = 1 + new Random().nextInt(3); // 1-3 ticks = 50-150ms
    }

    private void doClick(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null || mc.hitResult == null) return;
        if (!(mc.hitResult instanceof BlockHitResult bhr)) return;

        // Validate still looking at vault
        var state = mc.level.getBlockState(bhr.getBlockPos());
        if (!state.is(Blocks.VAULT) && !state.is(Blocks.OMINOUS_VAULT)) return;

        // Fire a completely normal right-click interact — identical to player input
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
        System.out.println("[VaultSniper] Clicked for: " + pendingClick);
    }

    private boolean isTargeted(String itemId) {
        if (targetItems.isEmpty()) return false;
        for (String t : targetItems) {
            if (itemId.equals(t) || itemId.endsWith(":" + t)) return true;
        }
        return false;
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static String normalise(String raw) {
        return raw.trim().toLowerCase().replace(" ", "_");
    }

    public String getStatus(Minecraft mc) {
        if (!active) return "OFF";
        if (mc.hitResult instanceof BlockHitResult bhr && mc.level != null) {
            var state = mc.level.getBlockState(bhr.getBlockPos());
            if (state.is(Blocks.VAULT) || state.is(Blocks.OMINOUS_VAULT)) {
                BlockEntity be = mc.level.getBlockEntity(bhr.getBlockPos());
                if (be instanceof VaultBlockEntity vault) {
                    VaultBlockEntity.SharedData shared = ((VaultBlockEntityAccessor) vault).vs_getSharedData();
                    if (shared != null) {
                        ItemStack displayed = ((SharedDataAccessor)(Object)shared).vs_getDisplayItem();
                        if (displayed != null && !displayed.isEmpty()) {
                            String id = itemId(displayed);
                            boolean targeted = isTargeted(id);
                            return "Watching — " + id + (targeted ? " ← TARGET!" : "");
                        }
                    }
                    return "Watching vault...";
                }
            }
        }
        return "Active — aim at a vault";
    }
}
