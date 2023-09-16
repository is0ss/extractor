package org.archipel.mixin;

import net.minecraft.SharedConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(SharedConstants.class)
public class DataFixerDisable {

    @Overwrite
    public static void enableDataFixerOptimization() {}

}
