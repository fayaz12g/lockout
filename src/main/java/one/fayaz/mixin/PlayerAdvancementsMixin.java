package one.fayaz.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.PlayerAdvancements;
import one.fayaz.LockoutGame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public class PlayerAdvancementsMixin {
	@Shadow
	private ServerPlayer player;

	@Inject(method = "award", at = @At("RETURN"))
	private void onAdvancementAwarded(AdvancementHolder advancement, String criterionKey, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue()) {
			String advancementId = advancement.id().toString();
			if (!advancementId.contains("recipes") && !advancementId.contains("root")) {
				// Check if the advancement is now fully completed
				PlayerAdvancements advancements = (PlayerAdvancements) (Object) this;
				AdvancementProgress progress = advancements.getOrStartProgress(advancement);

				if (progress.isDone()) {
					LockoutGame.INSTANCE.handleAdvancement(this.player, advancement);
				}
			}
		}
	}
}