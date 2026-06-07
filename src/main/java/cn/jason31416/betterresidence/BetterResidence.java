package cn.jason31416.betterresidence;

import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.betterresidence.core.ClaimNameValidator;
import cn.jason31416.betterresidence.core.DefaultClaimGroupRegistry;
import cn.jason31416.betterresidence.core.TargetGroup;
import cn.jason31416.betterresidence.core.AreaBox;
import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.TestClaimGenerator;
import cn.jason31416.planetlib.Required;
import cn.jason31416.planetlib.message.MessageTheme;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import cn.jason31416.planetlib.wrapper.SimpleWorld;
import cn.jason31416.planetlib.util.PluginLogger;
import org.bukkit.plugin.java.JavaPlugin;
import cn.jason31416.planetlib.PlanetLib;
import cn.jason31416.planetlib.util.Config;
import cn.jason31416.betterresidence.command.BetterResidenceCommand;
import cn.jason31416.betterresidence.handler.CommandSuggestionListener;
import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.betterresidence.handler.GeneralEventListener;
import cn.jason31416.betterresidence.handler.ProtectionEventListener;
import cn.jason31416.betterresidence.handler.visual.AreaBoxVisualizerManager;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.util.Util;
import lombok.Getter;

public final class BetterResidence extends JavaPlugin {
    @Getter
    private static BetterResidence instance;

    private boolean launched=false;

    @Override
    public void onEnable() {
        instance = this;
        PlanetLib.initialize(this, Required.VAULT, Required.NBT, Required.PLACEHOLDERAPI);
        try {
            reloadPluginConfig();

            PluginLogger.send(Lang.getMessage("console.loading"));

            DataHandler.init();
            new BetterResidenceCommand().register();
            getServer().getPluginManager().registerEvents(new CommandSuggestionListener(), this);
            getServer().getPluginManager().registerEvents(new GeneralEventListener(), this);
            getServer().getPluginManager().registerEvents(new ProtectionEventListener(), this);

            PluginLogger.send(Lang.getMessage("console.loaded"));
            launched = true;
        } catch (Exception e) {
            PluginLogger.error("Found illegal config: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }

//        createTestClaim();
    }

    // For testing, do not delete during development stages.
    private void createTestClaim() {
        new TestClaimGenerator().generate();
    }

    public void reloadPluginConfig() {
        Config.load(this);

        Util.saveFolder("lang");
        Lang.init("lang/" + Config.getString("lang", "en-us") + ".yml");
        MessageTheme.loadThemesFromFile("lang/theme.yml");
        MessageTheme.useTheme(Config.getString("theme", "default"));
        ClaimNameValidator.validateConfig();
        DefaultClaimGroupRegistry.loadConfig();
        TargetGroup.loadConfig();
        ClaimManager.clearCache();
    }

    @Override
    public void onDisable() {
        if(!launched) return;
        AreaBoxVisualizerManager.shutdown();
        DataHandler.close();
        PluginLogger.send(Lang.getMessage("console.disabled"));
    }
}
