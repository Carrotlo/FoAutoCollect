package me.foesio.foAutoCollect.event;

import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after an actual session preference change; force mode may keep effective state unchanged. */
public final class AutoCollectPreferenceChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Optional<Boolean> previousPreference;
    private final boolean preference;
    private final boolean previousActive;
    private final boolean active;

    public AutoCollectPreferenceChangedEvent(Player player, Optional<Boolean> previousPreference,
                                             boolean preference, boolean previousActive, boolean active) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.previousPreference = previousPreference;
        this.preference = preference;
        this.previousActive = previousActive;
        this.active = active;
    }

    public Player getPlayer() { return player; }
    public Optional<Boolean> getPreviousPreference() { return previousPreference; }
    public boolean getPreference() { return preference; }
    public boolean wasActive() { return previousActive; }
    public boolean isActive() { return active; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
