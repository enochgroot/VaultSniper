package com.vaultsniper;

import com.vaultsniper.mixin.KeyMappingAccessor;
import com.vaultsniper.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
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

        // Get hit result via accessor (field is private in 1.21.11)
        HitResult hit = ((MinecraftAccessor)(Object)mc).vs_getHitResult();
        if (hit == null || hit.getType() == HitResult.Type.MISS) return;
        if (!(hit instanceof BlockHitResult bhr)) return;

        BlockPos pos = bhr.getBlockPos();
        if (!isVaultBlock(mc, pos)) { clickedThisReveal = false; lastSeenItem = ""; return; }

        String id = getDisplayItemId(mc, pos);
        if (id.isEmpty()) return;

        if (!id.equals(lastSeenItem)) { lastSeenItem = id; clickedThisReveal = false; }
        if (clickedThisReveal || !isTargeted(id)) return;

        pendingItem = id;
        clickCooldown = 1 + new Random().nextInt(3); // 50-150ms human delay
    }

    private void fireClick(Minecraft mc) {
        if (mc.player == null) { pendingItem = null; return; }
        HitResult hit = ((MinecraftAccessor)(Object)mc).vs_getHitResult();
        if (!(hit instanceof BlockHitResult bhr)) { pendingItem = null; return; }
        if (!isVaultBlock(mc, bhr.getBlockPos())) { pendingItem = null; return; }

        ((KeyMappingAccessor)mc.options.keyUse).vs_setClickCount(
            ((KeyMappingAccessor)mc.options.keyUse).vs_getClickCount() + 1);

        System.out.println("[VaultSniper] Clicked for: " + pendingItem);
        clickedThisReveal = true;
        pendingItem = null;
    }

    /** Check if a block is a vault using registry (avoids hardcoded Mojang field names) */
    private static boolean isVaultBlock(Minecraft mc, BlockPos pos) {
        try {
            Block block = mc.level.getBlockState(pos).getBlock();
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
            if (key == null) return false;
            String path = key.getPath();
            return path.equals("vault") || path.equals("ominous_vault");
        } catch (Exception e) { return false; }
    }

    /** Read vault display item from synced block entity NBT */
    public static String getDisplayItemId(Minecraft mc, BlockPos pos) {
        try {
            BlockEntity be = mc.level.getBlockEntity(pos);
            if (be == null) return "";
            // saveWithoutMetadata returns Optional<CompoundTag> in 1.21.11
            Optional<CompoundTag> optNbt = be.saveWithoutMetadata(mc.level.registryAccess());
            if (optNbt.isEmpty()) return "";
            CompoundTag nbt = optNbt.get();

            if (!nbt.contains("shared_data")) return "";
            CompoundTag shared = nbt.getCompound("shared_data"); // returns CompoundTag directly

            Tag displayTag = shared.get("display_item");
            if (!(displayTag instanceof CompoundTag displayNbt)) return "";

            Optional<ItemStack> optStack = ItemStack.parse(mc.level.registryAccess(), displayNbt);
            if (optStack.isEmpty()) return "";
            ItemStack stack = optStack.get();
            if (stack.isEmpty()) return "";
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        } catch (Exception e) {
            return "";
        }
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
        if (mc.level == null) return "Active — aim at a vault";
        HitResult hit = ((MinecraftAccessor)(Object)mc).vs_getHitResult();
        if (hit == null || hit.getType() == HitResult.Type.MISS || !(hit instanceof BlockHitResult bhr))
            return "Active — aim at a vault";
        if (!isVaultBlock(mc, bhr.getBlockPos())) return "Active — aim at a vault";
        String id = getDisplayItemId(mc, bhr.getBlockPos());
        if (id.isEmpty()) return "Watching vault...";
        return "Showing: " + id + (isTargeted(id) ? " ← CLICKING!" : "");
    }
}
