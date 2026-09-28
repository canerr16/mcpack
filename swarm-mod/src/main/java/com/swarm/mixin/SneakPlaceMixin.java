package com.swarm.mixin;

import com.swarm.worker.WorkerBuildState;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * İnşa sırasında "sneak-place" davranışı: shouldCancelInteraction() true dönerse
 * bloğa (huni/smoker/sandık gibi konteynerlere) tıklamak GUI açmaz, bloğu koyar.
 *
 * Fiilen sneak tuşuna basmadan bunu yaptığımız için karakter çömelmez → hareket
 * yavaşlamaz, Baritone normal pathing yapar. Yalnızca worker inşa halindeyken aktif.
 *
 * NOT: shouldCancelInteraction() 1.21 yarn adı. Farklı mapping'de intermediary/isim
 * değişebilir; build sırasında refMap ile eşlenir.
 */
@Mixin(PlayerEntity.class)
public class SneakPlaceMixin {
    @Inject(method = "shouldCancelInteraction", at = @At("HEAD"), cancellable = true)
    private void swarm$forceSneakPlace(CallbackInfoReturnable<Boolean> cir) {
        if (WorkerBuildState.isBuilding()) {
            cir.setReturnValue(true);
        }
    }
}
