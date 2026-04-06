package com.vaultsniper;

import com.vaultsniper.mixin.KeyMappingAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class VaultSniper {
    private static final VaultSniper INSTANCE = new VaultSniper();
    public static VaultSniper getInstance() { return INSTANCE; }

    private boolean active = false;
    private final List<String> targetItems = new CopyOnWriteArrayList<>();

    // Human-like reaction: 1-3 ticks delay after spotting target
    private int clickCooldown = 0;
    private String pendingItem = null;
    private boolean clickedThisSession = false; // prevent spam-clicking same item

    private VaultSniper() {}

    public void toggle() {
        active = !active;
        if (!active) { pendingItem = null; clickCooldown = 0; clickedThisSession = false; }
        System.out.println("[VaultSniper] " + (active ? "ACTIVE" : "DISABLED"));
    }

    public boolean isActive() { return active; }
    public void addTarget(String itemId) { String n = norm(itemId); if (!n.isEmpty() && !targetItems.contains(n)) targetItems.add(n); }
    public void removeTarget(String itemId) { targetItems.remove(norm(itemId)); }
    public List<String> getTargets() { return Collections.unmodifiableList(targetItems); }
    public void clearTargets() { targetItems.clear(); }

    public void tick(Minecraft mc) {
        if (!active || mc.player == null || mc.level == null) return;

        // Cooldown ticking down → fire click when it hits 0
        if (clickCooldown > 0) {
            clickCooldown--;
            if (clickCooldown == 0 && pendingItem != null) {
                fireClick(mc);
            }
            return;
        }

        // Must be looking at a vault block
        if (!(mc.hitResult instanceof BlockHitResult bhr)) return;
        if (bhr.getType() == HitResult.Type.MISS) return;

        BlockPos pos = bhr.getBlockPos();
        var state = mc.level.getBlockState(pos);
        boolean isVault = state.is(Blocks.VAULT) || state.is(Blocks.OMINOUS_VAULT);
        if (!isVault) { clickedThisSession = false; return; } // reset when leaving vault

        // Read display item via NBT (works client-side, no class import needed)
        ItemStack displayed = getDisplayItemNBT(mc, pos);
        if (displayed.isEmpty()) return;

        String id = itemId(displayed);
        if (!isTargeted(id)) { clickedThisSession = false; return; }
        if (clickedThisSession) return; // already clicked this reveal

        // Target found! Queue click with human-like reaction delay
        pendingItem = id;
        clickCooldown = 1 + new Random().nextInt(3); // 50-150ms
    }

    private void fireClick(Minecraft mc) {
        if (mc.player == null || mc.hitResult == null) { pendingItem = null; return; }
        if (!(mc.hitResult instanceof BlockHitResult bhr)) { pendingItem = null; return; }

        var state = mc.level.getBlockState(bhr.getBlockPos());
        if (!state.is(Blocks.VAULT) && !state.is(Blocks.OMINOUS_VAULT)) { pendingItem = null; return; }

        // Trigger use key exactly like a real player right-click
        ((KeyMappingAccessor)mc.options.keyUse).vs_setClickCount(
            ((KeyMappingAccessor)mc.options.keyUse).vs_getClickCount() + 1);

        System.out.println("[VaultSniper] Clicked for: " + pendingItem);
        clickedThisSession = true;
        pendingItem = null;
    }

    private static ItemStack getDisplayItemNBT(Minecraft mc, BlockPos pos) {
        try {
            BlockEntity be = mc.level.getBlockEntity(pos);
            if (be == null) return ItemStack.EMPTY;
            CompoundTag nbt = be.saveWithoutMetadata(mc.level.registryAccess());
            // Vault stores display item in shared_data.display_item
            if (nbt.contains("shared_data")) {
                CompoundTag shared = nbt.getCompound("shared_data");
                if (shared.contains("display_item")) {
                    return ItemStack.parseOptional(mc.level.registryAccess(), shared.getCompound("display_item"));
                }
            }
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }

    private boolean isTargeted(String id) {
        for (String t : targetItems) {
            if (id.equals(t) || id.endsWith(":" + t) || id.contains(t)) return true;
        }
        return false;
    }

    private static String itemId(ItemStack s) { return BuiltInRegistries.ITEM.getKey(s.getItem()).toString(); }
    private static String norm(String s) { return s.trim().toLowerCase().replace(" ", "_"); }

    public String getStatus(Minecraft mc) {
        if (!active) return "OFF";
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult bhr)) return "Active — aim at a vault";
        var state = mc.level.getBlockState(bhr.getBlockPos());
        if (!state.is(Blocks.VAULT) && !state.is(Blocks.OMINOUS_VAULT)) return "Active — aim at a vault";
        ItemStack d = getDisplayItemNBT(mc, bhr.getBlockPos());
        if (d.isEmpty()) return "Watching vault... (no display item yet)";
        String id = itemId(d);
        return "Showing: " + id + (isTargeted(id) ? "  ← TARGET! CLICKING!" : "");
    }
}
