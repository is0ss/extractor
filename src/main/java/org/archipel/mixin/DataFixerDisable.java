package org.archipel.mixin;

import net.minecraft.SharedConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(SharedConstants.class)
public class DataFixerDisable {

    /**
     * @author iso
     * @reason Disable data fixer optimization
     */
    @Overwrite
    public static void enableDataFixerOptimization() {}

}
