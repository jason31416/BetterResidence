package cn.jason31416.betterresidence.core;

import cn.jason31416.betterresidence.command.AdminCommand;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AdminModeManager {
    private static final Set<UUID> disabledAdminModePlayers = ConcurrentHashMap.newKeySet();

    private AdminModeManager() {
    }

    public static boolean isAdminMode(SimplePlayer player) {
        return isAdminMode(player.getPlayer());
    }

    public static boolean isAdminMode(Player player) {
        return player.hasPermission(AdminCommand.PERMISSION) && !disabledAdminModePlayers.contains(player.getUniqueId());
    }

    public static boolean toggleAdminMode(Player player) {
        UUID uuid = player.getUniqueId();
        if (disabledAdminModePlayers.remove(uuid)) {
            return true;
        }
        disabledAdminModePlayers.add(uuid);
        return false;
    }

    public static void onPlayerQuit(Player player) {
        disabledAdminModePlayers.remove(player.getUniqueId());
    }
}
