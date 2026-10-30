package me.foesio.foAutoCollect.api;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Main-thread integration service, discoverable through Bukkit's ServicesManager. */
public interface AutoCollectService {
    /** Effective state, including force-enabled and the default for players without a preference. */
    boolean isActive(Player player);

    /** A preference set this server session; empty means the configured default applies. */
    Optional<Boolean> getSessionPreference(UUID playerId);

    /** Player-style request. Force mode rejects it; no-op requests do not fire a change event. */
    PreferenceChangeResult requestPreference(Player player, boolean enabled);

    /**
     * Offer a reward owned by the caller. HANDLED means the caller must not deliver it again;
     * every other status leaves delivery to the caller. The input item and location are not mutated.
     */
    RewardOfferResult offerReward(Player player, ItemStack item, Location location);
}
