package com.vaultsniper;

import com.vaultsniper.mixin.KeyMappingAccessor;
import com.vaultsniper.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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
        if (clickCooldown > 0) { clickCooldown--; if (clickCooldown == 0 && pendingItem != null) fireClick(mc); return; }

        HitResult hit = ((MinecraftAccessor)(Object)mc).vs_getHitResult();
        if (hit == null || hit.getType() == HitResult.Type.MISS) return;
        if (!(hit instanceof BlockHitResult bhr)) return;
        BlockPos pos = bhr.getBlockPos();
        if (!isVault(mc, pos)) { clickedThisReveal = false; lastSeenItem = ""; return; }

        String id = displayItem(mc, pos);
        if (id.isEmpty()) return;
        if (!id.equals(lastSeenItem)) { lastSeenItem = id; clickedThisReveal = false; }
        if (clickedThisReveal || !isTargeted(id)) return;

        pendingItem = id;
        clickCooldown = 1 + new Random().nextInt(3);
    }

    private void fireClick(Minecraft mc) {
        HitResult hit = ((MinecraftAccessor)(Object)mc).vs_getHitResult();
        if (mc.player == null || !(hit instanceof BlockHitResult bhr) || !isVault(mc, bhr.getBlockPos())) { pendingItem = null; return; }
        ((KeyMappingAccessor)mc.options.keyUse).vs_setClickCount(((KeyMappingAccessor)mc.options.keyUse).vs_getClickCount() + 1);
        System.out.println("[VaultSniper] Clicked for: " + pendingItem);
        clickedThisReveal = true;
        pendingItem = null;
    }

    /** Vault detection via registry key string — avoids hardcoded Mojang field names */
    private static boolean isVault(Minecraft mc, BlockPos pos) {
        try {
            Block block = mc.level.getBlockState(pos).getBlock();
            String key = BuiltInRegistries.BLOCK.getKey(block).toString();
            return key.contains("vault");
        } catch (Exception e) { return false; }
    }

    /** Read vault display item from NBT.
     *  API in 1.21.11:
     *    saveWithoutMetadata() → CompoundTag  (direct)
     *    getCompound(key)      → Optional<CompoundTag>
     *    Tag.getAsString()     → String
     */
    @SuppressWarnings("unchecked")
    public static String displayItem(Minecraft mc, BlockPos pos) {
        try {
            BlockEntity be = mc.level.getBlockEntity(pos);
            if (be == null) return "";
            // saveWithoutMetadata returns CompoundTag directly in 1.21.11
            CompoundTag root = be.saveWithoutMetadata(mc.level.registryAccess());
            if (!root.contains("shared_data")) return "";

            // getCompound returns Optional<CompoundTag> in 1.21.11
            Object sharedRaw = root.getCompound("shared_data");
            CompoundTag shared = sharedRaw instanceof Optional ? ((Optional<CompoundTag>)sharedRaw).orElse(null) : (CompoundTag)sharedRaw;
            if (shared == null || !shared.contains("display_item")) return "";

            Object displayRaw = shared.getCompound("display_item");
            CompoundTag display = displayRaw instanceof Optional ? ((Optional<CompoundTag>)displayRaw).orElse(null) : (CompoundTag)displayRaw;
            Tag displayTag = shared.get("display_item");
            // MC 1.21+ can serialize item as just a string OR a compound {id:"...",count:1}
            if (displayTag instanceof StringTag st) {
                return st.value(); // simplified: "minecraft:heavy_core"
            } else if (displayTag instanceof CompoundTag display) {
                Tag idTag = display.get("id"); // full: {id: "minecraft:heavy_core", count: 1}
                if (!(idTag instanceof StringTag idSt)) return "";
                return idSt.value();
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isTargeted(String id) {
        for (String t : targetItems) { if (id.equals(t) || id.endsWith(":" + t) || id.contains(t)) return true; }
        return false;
    }

    private static String norm(String s) { return s.trim().toLowerCase().replace(" ","_"); }

    public String getStatus(Minecraft mc) {
        if (!active) return "OFF";
        if (mc.level == null) return "Active — aim at a vault";
        HitResult hit = ((MinecraftAccessor)(Object)mc).vs_getHitResult();
        if (hit == null || hit.getType() == HitResult.Type.MISS || !(hit instanceof BlockHitResult bhr)) return "Active — aim at a vault";
        if (!isVault(mc, bhr.getBlockPos())) return "Active — aim at a vault";
        String id = displayItem(mc, bhr.getBlockPos());
        if (id.isEmpty()) return "Watching vault...";
        return "Showing: " + id + (isTargeted(id) ? " <- CLICKING!" : "");
    }
}
