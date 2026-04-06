package com.vaultsniper.mixin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.VaultBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(VaultBlockEntity.SharedData.class)
public interface SharedDataAccessor {
    @Accessor("displayItem")
    ItemStack vs_getDisplayItem();
}
