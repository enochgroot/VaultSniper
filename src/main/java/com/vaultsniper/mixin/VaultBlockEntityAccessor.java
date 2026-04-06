package com.vaultsniper.mixin;
import net.minecraft.world.level.block.entity.VaultBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(VaultBlockEntity.class)
public interface VaultBlockEntityAccessor {
    @Accessor("sharedData")
    VaultBlockEntity.SharedData vs_getSharedData();
}
