package dev.chatgpt.smoothsprint.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes the awkward 1.21.1 double-tap sprint lockout after landing from a jump.
 *
 * Vanilla 1.21.1 keeps its own sprintTriggerTime state. Around the airborne ->
 * grounded transition this can make a very fast new W-double-tap feel ignored.
 * We reset stale vanilla state on landing and, for a short post-landing window,
 * recognize a fresh pair of forward-key presses ourselves.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Shadow
    protected int sprintTriggerTime;

    @Unique
    private static final int SMOOTHSPRINT$LANDING_GRACE_TICKS = 24;

    @Unique
    private static final int SMOOTHSPRINT$DOUBLE_TAP_TICKS = 8;

    @Unique
    private boolean smoothsprint$initialized;

    @Unique
    private boolean smoothsprint$wasOnGround;

    @Unique
    private boolean smoothsprint$wasForwardDown;

    @Unique
    private int smoothsprint$landingGraceTicks;

    @Unique
    private int smoothsprint$doubleTapTicks;

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void smoothsprint$afterAiStep(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player != player) {
            return;
        }

        boolean onGround = player.onGround();
        boolean forwardDown = minecraft.options.keyUp.isDown();

        if (!this.smoothsprint$initialized) {
            this.smoothsprint$initialized = true;
            this.smoothsprint$wasOnGround = onGround;
            this.smoothsprint$wasForwardDown = forwardDown;
            return;
        }

        // A real air -> ground transition. Clear the old 1.21.1 double-tap
        // timer so the next pair of W presses starts from a clean state.
        if (!this.smoothsprint$wasOnGround && onGround) {
            this.sprintTriggerTime = 0;
            this.smoothsprint$landingGraceTicks = SMOOTHSPRINT$LANDING_GRACE_TICKS;
            this.smoothsprint$doubleTapTicks = 0;
        }

        if (this.smoothsprint$landingGraceTicks > 0) {
            this.smoothsprint$landingGraceTicks--;
        }
        if (this.smoothsprint$doubleTapTicks > 0) {
            this.smoothsprint$doubleTapTicks--;
        }

        // Only supplement vanilla immediately after landing. Everywhere else,
        // Minecraft's original double-tap behavior is untouched.
        if (this.smoothsprint$landingGraceTicks > 0
                && minecraft.screen == null
                && forwardDown
                && !this.smoothsprint$wasForwardDown) {
            if (this.smoothsprint$doubleTapTicks > 0) {
                if (smoothsprint$canSprint(player)) {
                    player.setSprinting(true);
                }
                this.smoothsprint$doubleTapTicks = 0;
            } else {
                this.smoothsprint$doubleTapTicks = SMOOTHSPRINT$DOUBLE_TAP_TICKS;
            }
        }

        this.smoothsprint$wasOnGround = onGround;
        this.smoothsprint$wasForwardDown = forwardDown;
    }

    @Unique
    private static boolean smoothsprint$canSprint(LocalPlayer player) {
        return player.onGround()
                && !player.isPassenger()
                && !player.isShiftKeyDown()
                && !player.isUsingItem()
                && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.getAbilities().flying
                && (player.getFoodData().getFoodLevel() > 6 || player.getAbilities().mayfly);
    }
}
