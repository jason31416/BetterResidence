package cn.jason31416.betterresidence;

import cn.jason31416.betterresidence.claim.DefaultClaimGroupRegistry;
import cn.jason31416.betterresidence.claim.TargetGroup;
import cn.jason31416.betterresidence.message.MessageThemeManager;
import cn.jason31416.planetlib.Required;
import cn.jason31416.planetlib.util.PluginLogger;
import org.bukkit.plugin.java.JavaPlugin;
import cn.jason31416.planetlib.PlanetLib;
import cn.jason31416.planetlib.util.Config;
import cn.jason31416.betterresidence.command.BetterResidenceCommand;
import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.betterresidence.handler.GeneralEventListener;
import cn.jason31416.betterresidence.handler.ProtectionEventListener;
import cn.jason31416.betterresidence.visual.AreaBoxVisualizerManager;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.util.Util;
import lombok.Getter;

public final class BetterResidence extends JavaPlugin {
    @Getter
    private static BetterResidence instance;

    @Override
    public void onEnable() {
        instance = this;
        PlanetLib.initialize(this, Required.NBT, Required.PLACEHOLDERAPI);
        try {
            reloadPluginConfig();

            PluginLogger.send(Lang.getMessage("console.loading"));

            DataHandler.init();
            new BetterResidenceCommand().register();
            getServer().getPluginManager().registerEvents(new GeneralEventListener(), this);
            getServer().getPluginManager().registerEvents(new ProtectionEventListener(), this);

            PluginLogger.send(Lang.getMessage("console.loaded"));
        }catch (Exception e){
            PluginLogger.error("Found illegal config: "+e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    public void reloadPluginConfig() {
        Config.load(this);

        Util.saveFolder("lang");
        Lang.init("lang/" + Config.getString("lang", "en-us") + ".yml");
        MessageThemeManager.loadThemesFromFile("lang/theme.yml");
        MessageThemeManager.useTheme(Config.getString("theme", "default"));
        DefaultClaimGroupRegistry.loadConfig();
        TargetGroup.loadConfig();
    }

    @Override
    public void onDisable() {
        AreaBoxVisualizerManager.shutdown();
        DataHandler.close();
        PluginLogger.send(Lang.getMessage("console.disabled"));
    }
}
