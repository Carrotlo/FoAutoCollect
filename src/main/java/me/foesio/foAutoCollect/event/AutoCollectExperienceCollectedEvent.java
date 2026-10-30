package me.foesio.foAutoCollect.event;

import me.foesio.foAutoCollect.api.CollectionSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after FoAutoCollect gives experience directly to a player. */
public final class AutoCollectExperienceCollectedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final int amount;
    private final Location location;
    private final CollectionSource source;

    public AutoCollectExperienceCollectedEvent(Player player, int amount, Location location, CollectionSource source) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.amount = amount;
        this.location = location == null ? null : location.clone();
        this.source = source == null ? CollectionSource.UNKNOWN : source;
    }

    public Player getPlayer() { return player; }
    public int getAmount() { return amount; }
    public Location getLocation() { return location == null ? null : location.clone(); }
    public CollectionSource getSource() { return source; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
