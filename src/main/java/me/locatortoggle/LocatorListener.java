package me.locatortoggle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

public final class LocatorListener implements Listener {

    private final Plugin plugin;
    private final LocatorManager manager;

    public LocatorListener(Plugin plugin, LocatorManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // В Folia событие входа вызывается в потоке игрока, так что применять можно сразу.
        manager.apply(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        // Временные модификаторы могут потеряться при возрождении — переприменяем через тик в потоке игрока.
        Player player = event.getPlayer();
        player.getScheduler().runDelayed(plugin, task -> manager.apply(player), null, 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof LocatorMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // Реагируем только на клики по верхнему инвентарю (меню), а не по своему.
        if (event.getClickedInventory() != menu.getInventory()) {
            return;
        }

        switch (event.getRawSlot()) {
            case LocatorMenu.TOGGLE_SLOT -> {
                boolean enabled = manager.toggle(player);
                menu.render(player);
                player.playSound(player.getLocation(),
                        enabled ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
                player.sendActionBar(enabled
                        ? Component.text("Локатор включён", NamedTextColor.GREEN)
                        : Component.text("Локатор выключен", NamedTextColor.RED));
            }
            case LocatorMenu.CLOSE_SLOT -> player.closeInventory();
            default -> { }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof LocatorMenu) {
            event.setCancelled(true);
        }
    }
}
