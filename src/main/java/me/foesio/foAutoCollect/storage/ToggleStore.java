package me.foesio.foAutoCollect.storage;

import me.foesio.foAutoCollect.FoAutoCollect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ToggleStore {
    private final FoAutoCollect plugin;
    private final Map<UUID, Boolean> enabledByPlayer = new HashMap<>();

    public ToggleStore(FoAutoCollect plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled(UUID playerId) {
        return enabledByPlayer.getOrDefault(playerId, plugin.isDefaultEnabled());
    }

    public boolean hasOverrides() {
        return !enabledByPlayer.isEmpty();
    }

    public boolean hasEnabledOverrides() {
        return enabledByPlayer.containsValue(Boolean.TRUE);
    }

    public void setEnabled(UUID playerId, boolean enabled) {
        enabledByPlayer.put(playerId, enabled);
    }

}
