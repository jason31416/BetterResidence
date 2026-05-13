package cn.jason31416.betterresidence.claim;

import cn.jason31416.betterresidence.BetterResidence;
import cn.jason31416.betterresidence.misc.IllegalConfigurationException;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.util.MapTree;
import cn.jason31416.planetlib.util.Util;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.bukkit.Material;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@AllArgsConstructor
public abstract class MaterialGroup {
    private static final Map<String, MaterialGroup> materialGroups = new ConcurrentHashMap<>();

    private final String name;
    @Getter
    private final int priority;

    public String getName(){
        return Lang.messageLoader.getRawMessage(name, name);
    }

    public abstract boolean isInGroup(String material);

    public static MaterialGroup getMaterialGroup(String name){
        return materialGroups.get(name);
    }

    @SneakyThrows
    public static void loadConfig(){
        materialGroups.clear();

        Util.savePluginResource("material-groups.yml");
        MapTree mapTree = MapTree.fromYaml(Files.readString(BetterResidence.getInstance().getDataFolder().toPath().resolve("material-groups.yml")));
        if(!mapTree.contains("all")){
            throw new IllegalConfigurationException("Missing `all` material group");
        }
        for(String key : mapTree.getKeys()){
            List<String> regexs = mapTree.getStringList(key+".materials");
            materialGroups.put(key, new MaterialGroup(mapTree.getString(key+".name"), mapTree.getInt(key+".priority")) {
                @Override
                public boolean isInGroup(String material) {
                    for(String regex : regexs){
                        if(material.toLowerCase().matches(regex.toLowerCase())){
                            return true;
                        }
                    }
                    return false;
                }
            });
        }
    }
}
