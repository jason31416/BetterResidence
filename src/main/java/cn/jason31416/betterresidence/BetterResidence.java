package cn.jason31416.betterresidence;

import cn.jason31416.planetlib.Required;
import cn.jason31416.planetlib.message.MessageTheme;
import cn.jason31416.planetlib.util.PluginLogger;
import org.bukkit.plugin.java.JavaPlugin;
import cn.jason31416.planetlib.PlanetLib;
import cn.jason31416.planetlib.util.Config;
import cn.jason31416.betterresidence.command.BetterResidenceCommand;
import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.util.Util;
import lombok.Getter;

public final class BetterResidence extends JavaPlugin {
    @Getter
    private static BetterResidence instance;

    @Override
    public void onEnable() {
        instance = this;
        PlanetLib.initialize(this, Required.VAULT, Required.NBT, Required.PLACEHOLDERAPI);
        Config.load(this);

        Util.saveFolder("lang");
        Lang.init("lang/"+Config.getString("lang", "en-us")+".yml");
        MessageTheme.loadThemesFromFile("lang/theme.yml");
        MessageTheme.useTheme(Config.getString("theme", "default"));

        PluginLogger.send(Lang.getMessage("console.loading"));

        DataHandler.init();
        new BetterResidenceCommand().register();

        PluginLogger.send(Lang.getMessage("console.loaded"));
    }

    @Override
    public void onDisable() {
        DataHandler.close();
        PluginLogger.send(Lang.getMessage("console.disabled"));
    }
}
