package me.foesio.foAutoCollect.command;

import me.foesio.foAutoCollect.FoAutoCollect;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AutoCollectCommand implements CommandExecutor, TabCompleter {
    private final FoAutoCollect plugin;

    public AutoCollectCommand(FoAutoCollect plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("foautocollect.use")) {
            plugin.sendMessage(sender, "no-permission");
            return true;
        }

        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "players-only");
            return true;
        }

        if (plugin.isForceEnabled()) {
            plugin.sendMessage(player, "toggle-forced");
            return true;
        }

        if (args.length > 0) {
            String subcommand = args[0].toLowerCase(Locale.ROOT);
            if (subcommand.equals("status")) {
                plugin.sendMessage(player, plugin.isAutoCollectActive(player) ? "status-enabled" : "status-disabled");
                return true;
            }
            if (subcommand.equals("on")) {
                plugin.getToggleStore().setEnabled(player.getUniqueId(), true);
                plugin.refreshCollectionListeners();
                plugin.sendMessage(player, "toggle-enabled");
                return true;
            }
            if (subcommand.equals("off")) {
                plugin.getToggleStore().setEnabled(player.getUniqueId(), false);
                plugin.refreshCollectionListeners();
                plugin.sendMessage(player, "toggle-disabled");
                return true;
            }

            plugin.sendMessage(player, "unknown-subcommand", "{arg}", args[0]);
            return true;
        }

        boolean enabled = !plugin.getToggleStore().isEnabled(player.getUniqueId());
        plugin.getToggleStore().setEnabled(player.getUniqueId(), enabled);
        plugin.refreshCollectionListeners();
        plugin.sendMessage(player, enabled ? "toggle-enabled" : "toggle-disabled");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length != 1 || !sender.hasPermission("foautocollect.use")) {
            return suggestions;
        }

        String input = args[0].toLowerCase(Locale.ROOT);
        for (String option : List.of("status", "on", "off")) {
            if (option.startsWith(input)) {
                suggestions.add(option);
            }
        }
        return suggestions;
    }
}
