package cn.jason31416.betterresidence.handler;

import cn.jason31416.betterresidence.claim.Claim;
import cn.jason31416.betterresidence.claim.ClaimManager;
import cn.jason31416.betterresidence.claim.PermissionType;
import cn.jason31416.planetlib.message.Message;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.util.PluginLogger;
import cn.jason31416.planetlib.wrapper.SimpleLocation;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import javax.annotation.Nullable;
import java.util.Locale;

public class ProtectionEventListener implements Listener {
    private void sendProhibitedMessage(SimplePlayer player, String missingPermission, @Nullable String material, String groupName){
        String permission = material==null?missingPermission:missingPermission+":"+material.toLowerCase(Locale.ROOT);
        String materialTranslationKey="";
        if(material!=null) {
            Material mat = Material.getMaterial(material.toUpperCase(Locale.ROOT));
            if (mat != null) {
                materialTranslationKey = "<lang:" + mat.translationKey() + ">";
            } else {
                try {
                    EntityType type = EntityType.valueOf(material.toUpperCase(Locale.ROOT));
                    materialTranslationKey = "<lang:" + type.translationKey() + ">";
                } catch (Exception ignored) {
                }
            }
        }
        if(materialTranslationKey.isEmpty()) {
            materialTranslationKey = material==null?"":material.toLowerCase(Locale.ROOT);
        }
        Lang.getMessage("claim.prohibited-action")
                .add("permission", Lang.getMessage("claim.action."+missingPermission, permission).add("material", materialTranslationKey))
                .add("group-name", groupName)
                .sendActionbar(player);
    }

    private void handlePlayerEvent(Cancellable event, SimplePlayer player, SimpleLocation location, String permission, @Nullable String material){
        Claim claim = ClaimManager.findClaimAt(location);
        if(claim==null){
            return;
        }
        if(!claim.checkPlayerPermission(player, permission, material)){
            sendProhibitedMessage(player, permission, material, claim.getPlayerGroup(player).first());
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        SimpleLocation location = SimpleLocation.of(event.getBlock());
        SimplePlayer player = SimplePlayer.of(event.getPlayer());
        handlePlayerEvent(event,
                player,
                location,
                PermissionType.BLOCK_BREAK.getId(),
                event.getBlock().getType().name()
        );
    }
}
