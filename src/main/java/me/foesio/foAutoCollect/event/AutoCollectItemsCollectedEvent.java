package me.foesio.foAutoCollect.event;

import java.util.ArrayList;
import java.util.List;
import me.foesio.foAutoCollect.api.CollectionSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

public final class AutoCollectItemsCollectedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final List<ItemStack> items;
    private final Location location;
    private final CollectionSource source;

    public AutoCollectItemsCollectedEvent(Player player, List<ItemStack> items, Location location) {
        this(player, items, location, CollectionSource.UNKNOWN);
    }

    public AutoCollectItemsCollectedEvent(Player player, List<ItemStack> items, Location location, CollectionSource source) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.items = copyItems(items);
        this.location = location == null ? null : location.clone();
        this.source = source == null ? CollectionSource.UNKNOWN : source;
    }

    public Player getPlayer() {
        return player;
    }

    public List<ItemStack> getItems() {
        return copyItems(items);
    }

    public Location getLocation() {
        return location == null ? null : location.clone();
    }

    /** Gameplay path that supplied the accepted items. */
    public CollectionSource getSource() {
        return source;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    private static List<ItemStack> copyItems(List<ItemStack> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<ItemStack> copy = new ArrayList<>(source.size());
        for (ItemStack item : source) {
            if (item != null && !item.getType().isAir()) {
                copy.add(item.clone());
            }
        }
        return List.copyOf(copy);
    }
}
