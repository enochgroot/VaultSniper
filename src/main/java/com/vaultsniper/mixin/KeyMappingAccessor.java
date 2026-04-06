package com.vaultsniper.mixin;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("clickCount") int vs_getClickCount();
    @Accessor("clickCount") void vs_setClickCount(int count);
}
