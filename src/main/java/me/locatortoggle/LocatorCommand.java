package me.locatortoggle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/**
 * /locator          — открыть меню
 * /locator on|off   — включить/выключить без меню
 * /locator toggle   — переключить
 *
 * В Folia команда игрока выполняется в его потоке, поэтому трогать игрока и открывать меню можно сразу.
 */
public final class LocatorCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("on", "off", "toggle");

    private final LocatorManager manager;

    public LocatorCommand(LocatorManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Эту команду может использовать только игрок.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            player.openInventory(new LocatorMenu(manager, player).getInventory());
            return true;
        }

        boolean enabled;
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on" -> { manager.setEnabled(player, true); enabled = true; }
            case "off" -> { manager.setEnabled(player, false); enabled = false; }
            case "toggle" -> enabled = manager.toggle(player);
            default -> {
                player.sendMessage(Component.text("Использование: /" + label + " [on|off|toggle]", NamedTextColor.RED));
                return true;
            }
        }

        player.sendMessage(enabled
                ? Component.text("Локатор включён: вы видите игроков с включённым локатором, а они — вас.", NamedTextColor.GREEN)
                : Component.text("Локатор выключен: вас никто не видит, и вы не видите других.", NamedTextColor.RED));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
    }
}
