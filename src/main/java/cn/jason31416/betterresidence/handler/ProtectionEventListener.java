package cn.jason31416.betterresidence.handler;

import cn.jason31416.betterresidence.claim.Claim;
import cn.jason31416.betterresidence.claim.ClaimManager;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.wrapper.SimpleLocation;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.projectiles.ProjectileSource;

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

    @Nullable
    private SimplePlayer resolveResponsiblePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return SimplePlayer.of(player);
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return SimplePlayer.of(player);
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        SimpleLocation location = SimpleLocation.of(event.getBlock());
        SimplePlayer player = SimplePlayer.of(event.getPlayer());
        handlePlayerEvent(event,
                player,
                location,
                "block.break",
                event.getBlock().getType().name()
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        handlePlayerEvent(event,
                SimplePlayer.of(event.getPlayer()),
                SimpleLocation.of(event.getBlockPlaced()),
                "block.place",
                event.getBlockPlaced().getType().name()
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.PHYSICAL) {
            return;
        }

        handlePlayerEvent(event,
                SimplePlayer.of(event.getPlayer()),
                SimpleLocation.of(event.getClickedBlock()),
                "block.interact",
                event.getClickedBlock().getType().name()
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        SimplePlayer player = resolveResponsiblePlayer(event.getDamager());
        if (player == null) {
            return;
        }

        handlePlayerEvent(event,
                player,
                SimpleLocation.of(event.getEntity().getLocation()),
                "entity.damage",
                event.getEntity().getType().name()
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType() == EntityType.ARMOR_STAND) {
            return;
        }

        handlePlayerEvent(event,
                SimplePlayer.of(event.getPlayer()),
                SimpleLocation.of(event.getRightClicked().getLocation()),
                "entity.interact",
                event.getRightClicked().getType().name()
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        handlePlayerEvent(event,
                SimplePlayer.of(event.getPlayer()),
                SimpleLocation.of(event.getRightClicked().getLocation()),
                "entity.interact",
                EntityType.ARMOR_STAND.name()
        );
    }
}
