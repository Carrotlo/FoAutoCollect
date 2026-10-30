package me.foesio.foAutoCollect.event;

import java.util.ArrayList;
import java.util.List;
import me.foesio.foAutoCollect.api.CollectionSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/** Fired before item deposit. Cancellation stops routing; queued block items are dropped at their batch location. */
public final class AutoCollectPreCollectEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final List<ItemStack> items;
    private final Location location;
    private final CollectionSource source;
    private boolean cancelled;

    public AutoCollectPreCollectEvent(Player player, List<ItemStack> items, Location location, CollectionSource source) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.items = copyItems(items);
        this.location = location == null ? null : location.clone();
        this.source = source == null ? CollectionSource.UNKNOWN : source;
    }

    public Player getPlayer() { return player; }
    public List<ItemStack> getItems() { return copyItems(items); }
    public Location getLocation() { return location == null ? null : location.clone(); }
    public CollectionSource getSource() { return source; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }

    private static List<ItemStack> copyItems(List<ItemStack> source) {
        if (source == null || source.isEmpty()) return List.of();
        List<ItemStack> result = new ArrayList<>(source.size());
        for (ItemStack item : source) if (item != null && !item.getType().isAir()) result.add(item.clone());
        return List.copyOf(result);
    }
}
