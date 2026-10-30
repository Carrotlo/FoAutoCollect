package me.foesio.foAutoCollect.api;

/** Outcome of offering an owned item reward to FoAutoCollect. */
public record RewardOfferResult(Status status, int acceptedAmount, int overflowAmount) {
    public enum Status {
        /** The reward was deposited, dropped as overflow, or both. The caller must not deliver it again. */
        HANDLED,
        /** Collection was disabled or the reward/player was invalid. The caller still owns the reward. */
        DECLINED,
        /** A pre-collection listener vetoed routing. The caller still owns the reward. */
        CANCELLED,
        /** BLOCK_COLLECTION rejected the whole item because it would not fit. The caller still owns it. */
        BLOCKED
    }

    public boolean handled() {
        return status == Status.HANDLED;
    }
}
