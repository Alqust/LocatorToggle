package me.locatortoggle;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class LocatorTogglePlugin extends JavaPlugin {

    private LocatorManager locatorManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        locatorManager = new LocatorManager(this, getConfig().getBoolean("default-enabled", true));

        getServer().getPluginManager().registerEvents(new LocatorListener(this, locatorManager), this);

        LocatorCommand command = new LocatorCommand(locatorManager);
        var pluginCommand = getCommand("locator");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        // В Folia ванильный локатор отключён внутри сервера — включаем собственную рассылку точек.
        FoliaLocatorTracker tracker = null;
        if (isFolia()) {
            try {
                tracker = new FoliaLocatorTracker(this, locatorManager, new WaypointPackets());
                getServer().getPluginManager().registerEvents(tracker, this);
                getLogger().info("Folia: включён собственный локатор (ванильный в Folia не работает).");
            } catch (ReflectiveOperationException e) {
                getLogger().severe("Не удалось подключиться к пакетам локатора, точки показываться не будут: " + e);
            }
        }

        // Если плагин включили на работающем сервере (например, /reload) — применяем настройки к тем, кто уже онлайн.
        for (Player player : Bukkit.getOnlinePlayers()) {
            FoliaLocatorTracker t = tracker;
            player.getScheduler().run(this, task -> {
                locatorManager.apply(player);
                if (t != null) {
                    t.start(player);
                }
            }, null);
        }

        // Плагин работает поверх ванильного локатора, поэтому gamerule locatorBar должен быть включён.
        for (World world : Bukkit.getWorlds()) {
            Boolean value = world.getGameRuleValue(GameRule.LOCATOR_BAR);
            if (Boolean.FALSE.equals(value)) {
                getLogger().warning("В мире " + world.getName() + " выключено правило locatorBar. "
                        + "Включите его (/gamerule locatorBar true), иначе локатор не будет виден ни у кого.");
            }
        }
    }

    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onDisable() {
        // Модификаторы временные (transient) и не сохраняются в файл игрока,
        // поэтому после выключения плагина у всех просто будет ванильное поведение.
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.getScheduler().run(this, task -> locatorManager.clear(player), null);
        }
    }
}
