package me.foesio.foAutoCollect.event;

import java.util.ArrayList;
import java.util.List;
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

    public AutoCollectItemsCollectedEvent(Player player, List<ItemStack> items, Location location) {
        this.player = player;
        this.items = copyItems(items);
        this.location = location == null ? null : location.clone();
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
