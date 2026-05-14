package cn.jason31416.betterresidence;

import cn.jason31416.betterresidence.claim.ClaimManager;
import cn.jason31416.betterresidence.claim.MaterialGroup;
import cn.jason31416.betterresidence.claim.AreaBox;
import cn.jason31416.betterresidence.claim.Claim;
import cn.jason31416.planetlib.Required;
import cn.jason31416.planetlib.message.MessageTheme;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import cn.jason31416.planetlib.wrapper.SimpleWorld;
import cn.jason31416.planetlib.util.PluginLogger;
import org.bukkit.plugin.java.JavaPlugin;
import cn.jason31416.planetlib.PlanetLib;
import cn.jason31416.planetlib.util.Config;
import cn.jason31416.betterresidence.command.BetterResidenceCommand;
import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.betterresidence.handler.GeneralEventListener;
import cn.jason31416.betterresidence.handler.ProtectionEventListener;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.util.Util;
import lombok.Getter;

import java.util.UUID;

public final class BetterResidence extends JavaPlugin {
    @Getter
    private static BetterResidence instance;

    @Override
    public void onEnable() {
        instance = this;
        PlanetLib.initialize(this, Required.VAULT, Required.NBT, Required.PLACEHOLDERAPI);
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

        // todo: Test
        createTestClaim();
    }

    private void createTestClaim() {
        String name = "test-claim";
        boolean exists = DataHandler.getDatabase().select("claim")
                .keyEquals("name", name)
                .one()
                .isPresent();
        if (exists) {
            return;
        }

        ClaimManager.createClaim(
                SimplePlayer.of("testing"),
                name,
                null,
                SimpleWorld.defaultWorld(),
                new AreaBox(-5, 50, 50, 75, -5, 5)
        );
    }

    public void reloadPluginConfig() {
        Config.load(this);

        Util.saveFolder("lang");
        Lang.init("lang/" + Config.getString("lang", "en-us") + ".yml");
        MessageTheme.loadThemesFromFile("lang/theme.yml");
        MessageTheme.useTheme(Config.getString("theme", "default"));
        MaterialGroup.loadConfig();
    }

    @Override
    public void onDisable() {
        DataHandler.close();
        PluginLogger.send(Lang.getMessage("console.disabled"));
    }
}
