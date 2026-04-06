package com.vaultsniper;

import com.vaultsniper.mixin.KeyMappingAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
    private int clickCooldown = 0;
    private String pendingItem = null;
    private boolean clickedThisReveal = false;
    private String lastSeenItem = "";

    private VaultSniper() {}

    public void toggle() {
        active = !active;
        if (!active) { pendingItem = null; clickCooldown = 0; clickedThisReveal = false; }
        System.out.println("[VaultSniper] " + (active ? "ACTIVE" : "DISABLED"));
    }

    public boolean isActive() { return active; }
    public void addTarget(String id) { String n = norm(id); if (!n.isEmpty() && !targetItems.contains(n)) targetItems.add(n); }
    public void removeTarget(String id) { targetItems.remove(norm(id)); }
    public List<String> getTargets() { return Collections.unmodifiableList(targetItems); }
    public void clearTargets() { targetItems.clear(); }

    public void tick(Minecraft mc) {
        if (!active || mc.player == null || mc.level == null) return;

        if (clickCooldown > 0) {
            clickCooldown--;
            if (clickCooldown == 0 && pendingItem != null) fireClick(mc);
            return;
        }

        // Must be looking at a vault
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() == HitResult.Type.MISS) return;
        if (!(hit instanceof BlockHitResult bhr)) return;
        BlockPos pos = bhr.getBlockPos();
        var state = mc.level.getBlockState(pos);
        boolean isVault = state.is(Blocks.VAULT) || state.is(Blocks.OMINOUS_VAULT);
        if (!isVault) { clickedThisReveal = false; lastSeenItem = ""; return; }

        String id = getDisplayItemId(mc, pos);
        if (id.isEmpty()) return;

        // Reset click flag when the displayed item changes
        if (!id.equals(lastSeenItem)) { lastSeenItem = id; clickedThisReveal = false; }
        if (clickedThisReveal) return;
        if (!isTargeted(id)) return;

        // Target spotted — queue click with human reaction delay
        pendingItem = id;
        clickCooldown = 1 + new Random().nextInt(3);
    }

    private void fireClick(Minecraft mc) {
        if (mc.player == null || mc.hitResult == null) { pendingItem = null; return; }
        if (!(mc.hitResult instanceof BlockHitResult bhr)) { pendingItem = null; return; }
        var state = mc.level.getBlockState(bhr.getBlockPos());
        if (!state.is(Blocks.VAULT) && !state.is(Blocks.OMINOUS_VAULT)) { pendingItem = null; return; }

        // Trigger right-click exactly like real player input
        ((KeyMappingAccessor)mc.options.keyUse).vs_setClickCount(
            ((KeyMappingAccessor)mc.options.keyUse).vs_getClickCount() + 1);

        System.out.println("[VaultSniper] Clicked! Target: " + pendingItem);
        clickedThisReveal = true;
        pendingItem = null;
    }

    /** Read vault display item via NBT — works client-side, no class-specific imports needed */
    public static String getDisplayItemId(Minecraft mc, BlockPos pos) {
        try {
            BlockEntity be = mc.level.getBlockEntity(pos);
            if (be == null) return "";
            // saveWithoutMetadata returns Optional<CompoundTag> in 1.21.11
            var optNbt = be.saveWithoutMetadata(mc.level.registryAccess());
            CompoundTag nbt = optNbt instanceof Optional ? ((Optional<CompoundTag>)optNbt).orElse(new CompoundTag()) : (CompoundTag) optNbt;
            // Try known vault NBT paths
            CompoundTag shared = getCompound(nbt, "shared_data");
            Tag displayTag = shared.get("display_item");
            if (displayTag instanceof CompoundTag displayNbt) {
                var optStack = ItemStack.parseOptional(mc.level.registryAccess(), displayNbt);
                ItemStack stack = optStack instanceof Optional ? ((Optional<ItemStack>)optStack).orElse(ItemStack.EMPTY) : (ItemStack) optStack;
                if (!stack.isEmpty()) return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static CompoundTag getCompound(CompoundTag tag, String key) {
        try {
            var result = tag.getCompound(key);
            if (result instanceof Optional) return ((Optional<CompoundTag>)result).orElse(new CompoundTag());
            return (CompoundTag) result;
        } catch (Exception e) { return new CompoundTag(); }
    }

    private boolean isTargeted(String id) {
        for (String t : targetItems) {
            if (id.equals(t) || id.endsWith(":" + t) || id.contains(t)) return true;
        }
        return false;
    }

    private static String norm(String s) { return s.trim().toLowerCase().replace(" ", "_"); }

    public String getStatus(Minecraft mc) {
        if (!active) return "OFF";
        if (mc.level == null || mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS) return "Active — aim at a vault";
        if (!(mc.hitResult instanceof BlockHitResult bhr)) return "Active — aim at a vault";
        var state = mc.level.getBlockState(bhr.getBlockPos());
        if (!state.is(Blocks.VAULT) && !state.is(Blocks.OMINOUS_VAULT)) return "Active — aim at a vault";
        String id = getDisplayItemId(mc, bhr.getBlockPos());
        if (id.isEmpty()) return "Watching vault...";
        return "Showing: " + id + (isTargeted(id) ? "  §a← CLICKING!" : "");
    }
}
