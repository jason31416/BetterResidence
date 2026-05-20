package cn.jason31416.betterresidence.handler;

import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.wrapper.SimpleLocation;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Objects;

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

    private boolean checkPlayerPermission(SimplePlayer player, SimpleLocation location, String permission, @Nullable String material) {
        Claim claim = ClaimManager.findClaimAt(location);
        if (claim == null) {
            return true;
        }
        if (claim.checkPlayerPermission(player, permission, material)) {
            return true;
        }
        sendProhibitedMessage(player, permission, material, claim.getPlayerGroup(player).first());
        return false;
    }

    private String target(Material material) {
        return material.name();
    }

    private String target(Entity entity) {
        return entity.getType().name();
    }

    private String target(EntityType entityType) {
        return entityType.name();
    }

    private SimpleLocation location(Entity entity) {
        return SimpleLocation.of(entity.getLocation());
    }

    private @Nullable Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean isBlockLikeHanging(Entity entity) {
        return switch (entity.getType()) {
            case ITEM_FRAME, GLOW_ITEM_FRAME, PAINTING -> true;
            default -> false;
        };
    }

    private @Nullable Material hangingMaterial(Entity entity) {
        return switch (entity.getType()) {
            case ITEM_FRAME -> Material.ITEM_FRAME;
            case GLOW_ITEM_FRAME -> Material.GLOW_ITEM_FRAME;
            case PAINTING -> Material.PAINTING;
            default -> null;
        };
    }

    private @Nullable Material bucketPlacementMaterial(Material bucket) {
        return switch (bucket) {
            case WATER_BUCKET -> Material.WATER;
            case LAVA_BUCKET -> Material.LAVA;
            case POWDER_SNOW_BUCKET -> Material.POWDER_SNOW;
            default -> null;
        };
    }

    private @Nullable EntityType spawnEntityType(Material item) {
        // Spawn eggs, vehicles, and armor stands all create entities directly, so they share entity.spawn.
        if (item == Material.ARMOR_STAND) {
            return EntityType.ARMOR_STAND;
        }
        EntityType vehicle = vehicleEntityType(item);
        if (vehicle != null) {
            return vehicle;
        }
        String name = item.name();
        if (!name.endsWith("_SPAWN_EGG")) {
            return null;
        }
        try {
            return EntityType.valueOf(name.substring(0, name.length() - "_SPAWN_EGG".length()));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private @Nullable EntityType vehicleEntityType(Material item) {
        return switch (item) {
            case MINECART -> EntityType.MINECART;
            case CHEST_MINECART -> EntityType.CHEST_MINECART;
            case FURNACE_MINECART -> EntityType.FURNACE_MINECART;
            case HOPPER_MINECART -> EntityType.HOPPER_MINECART;
            case TNT_MINECART -> EntityType.TNT_MINECART;
            case COMMAND_BLOCK_MINECART -> EntityType.COMMAND_BLOCK_MINECART;
            case OAK_BOAT -> EntityType.OAK_BOAT;
            case SPRUCE_BOAT -> EntityType.SPRUCE_BOAT;
            case BIRCH_BOAT -> EntityType.BIRCH_BOAT;
            case JUNGLE_BOAT -> EntityType.JUNGLE_BOAT;
            case ACACIA_BOAT -> EntityType.ACACIA_BOAT;
            case DARK_OAK_BOAT -> EntityType.DARK_OAK_BOAT;
            case MANGROVE_BOAT -> EntityType.MANGROVE_BOAT;
            case CHERRY_BOAT -> EntityType.CHERRY_BOAT;
            case PALE_OAK_BOAT -> EntityType.PALE_OAK_BOAT;
            case BAMBOO_RAFT -> EntityType.BAMBOO_RAFT;
            case OAK_CHEST_BOAT -> EntityType.OAK_CHEST_BOAT;
            case SPRUCE_CHEST_BOAT -> EntityType.SPRUCE_CHEST_BOAT;
            case BIRCH_CHEST_BOAT -> EntityType.BIRCH_CHEST_BOAT;
            case JUNGLE_CHEST_BOAT -> EntityType.JUNGLE_CHEST_BOAT;
            case ACACIA_CHEST_BOAT -> EntityType.ACACIA_CHEST_BOAT;
            case DARK_OAK_CHEST_BOAT -> EntityType.DARK_OAK_CHEST_BOAT;
            case MANGROVE_CHEST_BOAT -> EntityType.MANGROVE_CHEST_BOAT;
            case CHERRY_CHEST_BOAT -> EntityType.CHERRY_CHEST_BOAT;
            case PALE_OAK_CHEST_BOAT -> EntityType.PALE_OAK_CHEST_BOAT;
            case BAMBOO_CHEST_RAFT -> EntityType.BAMBOO_CHEST_RAFT;
            default -> null;
        };
    }

    private boolean isPlacementLikeItem(@Nullable ItemStack item) {
        if (item == null) {
            return false;
        }
        Material material = item.getType();
        return material.isBlock() || bucketPlacementMaterial(material) != null || spawnEntityType(material) != null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        SimpleLocation location = SimpleLocation.of(event.getBlock());
        SimplePlayer player = SimplePlayer.of(event.getPlayer());
        handlePlayerEvent(event,
                player,
                location,
                "block.break",
                target(event.getBlock().getType())
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        handlePlayerEvent(event,
                SimplePlayer.of(event.getPlayer()),
                SimpleLocation.of(event.getBlockPlaced()),
                "block.place",
                target(event.getBlockPlaced().getType())
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        // Filling a bucket removes the clicked source block, so it is checked as block.break.
        Block block = event.getBlock();
        handlePlayerEvent(event,
                SimplePlayer.of(event.getPlayer()),
                SimpleLocation.of(block),
                "block.break",
                target(block.getType())
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Material placedMaterial = bucketPlacementMaterial(event.getBucket());
        if (placedMaterial == null) {
            return;
        }
        // Emptying a bucket places a world material into the adjacent target block.
        Block targetBlock = event.getBlock().getRelative(event.getBlockFace());
        handlePlayerEvent(event,
                SimplePlayer.of(event.getPlayer()),
                SimpleLocation.of(targetBlock),
                "block.place",
                target(placedMaterial)
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.PHYSICAL && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        SimplePlayer player = SimplePlayer.of(event.getPlayer());
        Block clickedBlock = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (clickedBlock != null && action == Action.PHYSICAL) {
            Material material = clickedBlock.getType();
            // Physical actions can either damage the block itself or simply activate it.
            handlePlayerEvent(event, player, SimpleLocation.of(clickedBlock), physicalInteractPermission(material), target(material));
            return;
        }
        if (clickedBlock != null && shouldCheckBlockInteract(event, clickedBlock, item)) {
            handlePlayerEvent(event, player, SimpleLocation.of(clickedBlock), "block.interact", target(clickedBlock.getType()));
        }

        if (event.isCancelled()) {
            return;
        }
        EntityType spawnType = item == null ? null : spawnEntityType(item.getType());
        if (spawnType == null) {
            return;
        }

        Location spawnLocation = clickedBlock == null
                ? event.getPlayer().getLocation()
                : clickedBlock.getRelative(event.getBlockFace() == BlockFace.SELF ? BlockFace.UP : event.getBlockFace()).getLocation();
        handlePlayerEvent(event, player, SimpleLocation.of(spawnLocation), "entity.spawn", target(spawnType));
    }

    private String physicalInteractPermission(Material material) {
        return switch (material) {
            case FARMLAND, TURTLE_EGG -> "block.break";
            default -> "block.interact";
        };
    }

    private boolean shouldCheckBlockInteract(PlayerInteractEvent event, Block clickedBlock, @Nullable ItemStack item) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        // Do not turn ordinary placement into block.interact. Interactable blocks still need this
        // check so containers, doors, buttons, etc. stay protected even while holding a block item.
        return clickedBlock.getType().isInteractable() || !isPlacementLikeItem(item);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Material hangingTarget = hangingMaterial(entity);
        if (hangingTarget != null) {
            // Item frames and paintings are decorative block-like entities, not entity permissions.
            handlePlayerEvent(event, SimplePlayer.of(event.getPlayer()), location(entity), "block.interact", target(hangingTarget));
            return;
        }
        handlePlayerEvent(event, SimplePlayer.of(event.getPlayer()), location(entity), "entity.interact", target(entity));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        handlePlayerEvent(event,
                SimplePlayer.of(event.getPlayer()),
                location(event.getRightClicked()),
                "entity.interact",
                target(event.getRightClicked())
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShearEntity(PlayerShearEntityEvent event) {
        handlePlayerEvent(event, SimplePlayer.of(event.getPlayer()), location(event.getEntity()), "entity.interact", target(event.getEntity()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeashEntity(PlayerLeashEntityEvent event) {
        handlePlayerEvent(event, SimplePlayer.of(event.getPlayer()), location(event.getEntity()), "entity.interact", target(event.getEntity()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUnleashEntity(PlayerUnleashEntityEvent event) {
        handlePlayerEvent(event, SimplePlayer.of(event.getPlayer()), location(event.getEntity()), "entity.interact", target(event.getEntity()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getCaught() == null) {
            return;
        }
        handlePlayerEvent(event, SimplePlayer.of(event.getPlayer()), location(event.getCaught()), "entity.interact", target(event.getCaught()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Player player = resolvePlayer(event.getDamager());
        if (player == null) {
            return;
        }
        Material hangingTarget = hangingMaterial(event.getEntity());
        if (hangingTarget != null) {
            handlePlayerEvent(event, SimplePlayer.of(player), location(event.getEntity()), "block.break", target(hangingTarget));
            return;
        }
        handlePlayerEvent(event, SimplePlayer.of(player), location(event.getEntity()), "entity.damage", target(event.getEntity()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        Player player = resolvePlayer(event.getAttacker());
        if (player == null) {
            return;
        }
        handlePlayerEvent(event, SimplePlayer.of(player), location(event.getVehicle()), "entity.damage", target(event.getVehicle()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Player player = resolvePlayer(event.getAttacker());
        if (player == null) {
            return;
        }
        handlePlayerEvent(event, SimplePlayer.of(player), location(event.getVehicle()), "entity.damage", target(event.getVehicle()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player player = resolvePlayer(event.getRemover());
        Material material = hangingMaterial(event.getEntity());
        if (player == null || material == null) {
            return;
        }
        handlePlayerEvent(event, SimplePlayer.of(player), location(event.getEntity()), "block.break", target(material));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getPlayer() == null || !isBlockLikeHanging(event.getEntity())) {
            return;
        }
        Material material = Objects.requireNonNull(hangingMaterial(event.getEntity()));
        handlePlayerEvent(event, SimplePlayer.of(event.getPlayer()), location(event.getEntity()), "block.place", target(material));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        handlePlayerEvent(event, SimplePlayer.of(player), location(event.getMount()), "entity.interact", target(event.getMount()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player)) {
            return;
        }
        handlePlayerEvent(event, SimplePlayer.of(player), location(event.getVehicle()), "entity.interact", target(event.getVehicle()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEggThrow(PlayerEggThrowEvent event) {
        if (!event.isHatching()) {
            return;
        }
        // Egg hatching is not cancellable, so deny it by forcing the hatch count to zero.
        SimplePlayer player = SimplePlayer.of(event.getPlayer());
        SimpleLocation location = SimpleLocation.of(event.getEgg().getLocation());
        if (!checkPlayerPermission(player, location, "entity.spawn", target(event.getHatchingType()))) {
            event.setHatching(false);
            event.setNumHatches((byte) 0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null || isSameBlock(event.getFrom(), event.getTo())) {
            return;
        }
        handleEnter(event, event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        handleEnter(event, event.getPlayer(), event.getFrom(), event.getTo());
    }

    private void handleEnter(Cancellable event, Player player, Location from, Location to) {
        Claim fromClaim = ClaimManager.findClaimAt(SimpleLocation.of(from));
        Claim toClaim = ClaimManager.findClaimAt(SimpleLocation.of(to));
        String fromClaimUuid = fromClaim == null ? null : fromClaim.getUuid();
        String toClaimUuid = toClaim == null ? null : toClaim.getUuid();
        if (toClaim == null || Objects.equals(fromClaimUuid, toClaimUuid)) {
            return;
        }
        if (!checkPlayerPermission(SimplePlayer.of(player), SimpleLocation.of(to), "enter", null)) {
            event.setCancelled(true);
        }
    }

    private boolean isSameBlock(Location from, Location to) {
        return from.getWorld().equals(to.getWorld())
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ();
    }
}
