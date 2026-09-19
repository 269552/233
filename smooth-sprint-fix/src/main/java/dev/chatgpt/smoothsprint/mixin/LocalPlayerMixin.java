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
 * Improves Minecraft 1.21.1 double-tap-W sprint handling.
 *
 * Besides fixing the short post-landing lockout, this also allows a fresh
 * double-tap of W to start sprinting while the local player is airborne.
 * Ground sprint behavior remains vanilla; the mod only supplements the cases
 * vanilla 1.21.1 does not handle smoothly.
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

    /**
     * Tracks a forward-key press globally, so the first press may happen on
     * the ground and the second press may happen in the air.
     */
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

        // Air -> ground: clear stale vanilla 1.21.1 sprint-double-tap state so
        // a new double tap can be recognized immediately after landing.
        if (!this.smoothsprint$wasOnGround && onGround) {
            this.sprintTriggerTime = 0;
            this.smoothsprint$landingGraceTicks = SMOOTHSPRINT$LANDING_GRACE_TICKS;
        }

        if (this.smoothsprint$landingGraceTicks > 0) {
            this.smoothsprint$landingGraceTicks--;
        }
        if (this.smoothsprint$doubleTapTicks > 0) {
            this.smoothsprint$doubleTapTicks--;
        }

        // Only react to a real W press edge, not to holding W down.
        if (minecraft.screen == null && forwardDown && !this.smoothsprint$wasForwardDown) {
            if (this.smoothsprint$doubleTapTicks > 0) {
                // Vanilla 1.21.1 only starts double-tap sprint in its grounded
                // path. Supplement that behavior while airborne, and retain
                // the previous landing-grace fix for the first moments after
                // touching the ground.
                if ((!onGround || this.smoothsprint$landingGraceTicks > 0)
                        && smoothsprint$canSprint(player, onGround)) {
                    player.setSprinting(true);
                    this.sprintTriggerTime = 0;
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
    private static boolean smoothsprint$canSprint(LocalPlayer player, boolean onGround) {
        // The important difference from vanilla's grounded double-tap path is
        // that onGround is NOT required here. All normal sprint restrictions
        // are still respected.
        return !player.isSprinting()
                && !player.isPassenger()
                && !player.isShiftKeyDown()
                && !player.isUsingItem()
                && !player.hasEffect(MobEffects.BLINDNESS)
                && !player.getAbilities().flying
                && !player.isFallFlying()
                && !player.isInWater()
                && (player.getFoodData().getFoodLevel() > 6 || player.getAbilities().mayfly);
    }
}
