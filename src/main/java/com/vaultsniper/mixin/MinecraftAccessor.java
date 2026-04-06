package com.vaultsniper.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Accessor("hitResult")
    HitResult vs_getHitResult();
}
