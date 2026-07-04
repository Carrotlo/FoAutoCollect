package me.foesio.foAutoCollect.command;

import java.util.List;
import java.util.Optional;
import me.foesio.core.command.FoAdminArgument;
import me.foesio.core.command.FoAdminArguments;
import me.foesio.core.command.FoAdminCommand;
import me.foesio.core.command.FoAdminCommandContext;
import me.foesio.core.command.FoAdminMessages;
import me.foesio.core.command.FoAdminSubcommand;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foAutoCollect.FoAutoCollect;
import me.foesio.foAutoCollect.editor.EditorManager;
import org.bukkit.entity.Player;

public final class AutoCollectAdminCommand {
    private static final FoAdminArgument<Player> ONLINE_PLAYER = FoAdminArguments.onlinePlayer();
    private static final FoAdminArgument<Boolean> ON_OFF = FoAdminArguments.onOff();
    private static final FoAdminArgument<String> STATUS_ON_OFF = FoAdminArguments.statusOnOff();
    private static final List<String> HELP_KEYS = List.of(
        "help-header",
        "help-version",
        "help-reload",
        "help-editor",
        "help-set",
        "help-force"
    );

    private AutoCollectAdminCommand() {
    }

    public static void register(
        FoAutoCollect plugin,
        EditorManager editorManager,
        FoMessageService messages,
        FoReloadRegistry reloadRegistry,
        UpdateNoticeService updates
    ) {
        FoAdminCommand.builder(plugin, messages)
            .commandName("foautocollectadmin")
            .permission("foautocollect.admin")
            .adminMessages(adminMessages())
            .reloads(reloadRegistry)
            .updates(updates)
            .editor(editorManager::openMainMenu)
            .defaultExecutor(context -> sendHelp(plugin, context))
            .addSubcommand(setSubcommand(plugin))
            .addSubcommand(forceSubcommand(plugin))
            .register();
    }

    private static FoAdminMessages adminMessages() {
        return FoAdminMessages.builder()
            .generalNoPermission("messages.no-permission", "{prefix} {bad}You don't have permission.")
            .generalPlayerOnly("messages.players-only", "{prefix} {bad}Only players can run this command.")
            .reloadSuccess("messages.reloaded", "{prefix} {good}Reloaded config and messages.")
            .commandMissing("messages.command-missing", "{prefix} {bad}Command is missing from plugin.yml: {theme}{command}")
            .commandFailed("messages.command-failed", "{prefix} {bad}Command failed. {muted}{error}")
            .editorOpened("admin.editor-opened", "{prefix} {theme}Editor opened.")
            .build();
    }

    private static FoAdminSubcommand setSubcommand(FoAutoCollect plugin) {
        return FoAdminSubcommand.builder("set", context -> {
                if (context.args().length < 3) {
                    plugin.sendMessage(context.sender(), "invalid-state");
                    return true;
                }

                Optional<Player> target = ONLINE_PLAYER.parse(context.arg(1));
                if (target.isEmpty()) {
                    plugin.sendMessage(context.sender(), "player-not-found");
                    return true;
                }

                Optional<Boolean> state = ON_OFF.parse(context.arg(2));
                if (state.isEmpty()) {
                    plugin.sendMessage(context.sender(), "invalid-state");
                    return true;
                }

                Player targetPlayer = target.get();
                boolean enabled = state.get();
                plugin.getToggleStore().setEnabled(targetPlayer.getUniqueId(), enabled);
                plugin.refreshCollectionListeners();
                plugin.sendMessage(context.sender(), enabled ? "admin-set-enabled" : "admin-set-disabled", "{player}", targetPlayer.getName());
                plugin.sendMessage(targetPlayer, enabled ? "toggle-enabled" : "toggle-disabled");
                return true;
            })
            .usage("set <player> <on|off>")
            .tabCompleter(context -> tabSet(context))
            .build();
    }

    private static FoAdminSubcommand forceSubcommand(FoAutoCollect plugin) {
        return FoAdminSubcommand.builder("force", context -> {
                Optional<String> state = STATUS_ON_OFF.parse(context.arg(1));
                if (context.args().length < 2 || state.filter("status"::equals).isPresent()) {
                    plugin.sendMessage(
                        context.sender(),
                        "admin-force-state",
                        "{state}",
                        plugin.isForceEnabled() ? "{good}enabled" : "{bad}disabled"
                    );
                    plugin.sendMessage(context.sender(), "admin-force-usage");
                    return true;
                }

                Optional<Boolean> enabled = ON_OFF.parse(context.arg(1));
                if (enabled.isEmpty()) {
                    plugin.sendMessage(context.sender(), "invalid-force-state");
                    return true;
                }

                plugin.setForceEnabled(enabled.get());
                plugin.sendMessage(context.sender(), enabled.get() ? "admin-force-enabled" : "admin-force-disabled");
                return true;
            })
            .usage("force <on|off|status>")
            .tabCompleter(context -> context.args().length == 2 ? STATUS_ON_OFF.complete(context.arg(1)) : List.of())
            .build();
    }

    private static boolean sendHelp(FoAutoCollect plugin, FoAdminCommandContext context) {
        for (String key : HELP_KEYS) {
            plugin.sendMessage(context.sender(), key);
        }
        return true;
    }

    private static List<String> tabSet(FoAdminCommandContext context) {
        if (context.args().length == 2) {
            return ONLINE_PLAYER.complete(context.arg(1));
        }
        if (context.args().length == 3) {
            return ON_OFF.complete(context.arg(2));
        }
        return List.of();
    }
}
