package cn.jason31416.betterresidence.handler;

import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.betterresidence.core.FlagRegistry;
import cn.jason31416.betterresidence.BetterResidence;
import cn.jason31416.planetlib.util.Lang;
import cn.jason31416.planetlib.wrapper.SimpleLocation;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Trident;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
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
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ProtectionEventListener implements Listener {
    private static final String FALLING_BLOCK_ORIGIN_CLAIM_KEY = "falling_block_origin_claim";

    private final NamespacedKey fallingBlockOriginClaimKey = new NamespacedKey(BetterResidence.getInstance(), FALLING_BLOCK_ORIGIN_CLAIM_KEY);

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

    private @Nullable Claim claimAt(SimpleLocation location) {
        return ClaimManager.findClaimAt(location);
    }

    private @Nullable String claimUuidAt(SimpleLocation location) {
        Claim claim = claimAt(location);
        return claim == null ? null : claim.getUuid();
    }

    private @Nullable String claimUuidAt(Location location) {
        return claimUuidAt(SimpleLocation.of(location));
    }

    private boolean isSameClaimContext(SimpleLocation first, SimpleLocation second) {
        return Objects.equals(claimUuidAt(first), claimUuidAt(second));
    }

    private String getFlagValue(SimpleLocation location, String flagId) {
        Claim claim = claimAt(location);
        if (claim == null) {
            return "true";
        }
        return FlagRegistry.getFlag(flagId)
                .map(claim::getFlag)
                .orElse("true");
    }

    private boolean isBooleanFlagAllowed(SimpleLocation location, String flagId) {
        return Boolean.parseBoolean(getFlagValue(location, flagId));
    }

    private boolean shouldBlockByBooleanFlag(SimpleLocation location, String flagId) {
        return !isBooleanFlagAllowed(location, flagId);
    }

    private void removeBlocksDeniedByFlag(List<Block> blocks, String flagId) {
        blocks.removeIf(block -> shouldBlockByBooleanFlag(SimpleLocation.of(block), flagId));
    }

    private void removeBlockStatesDeniedByFlag(List<BlockState> states, String fallbackFlagId) {
        states.removeIf(state -> shouldBlockByBooleanFlag(SimpleLocation.of(state.getLocation()), growthFlag(state.getType(), fallbackFlagId)));
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
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        SimplePlayer player = SimplePlayer.of(event.getPlayer());
        Block clickedBlock = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (item != null) {
            SimpleLocation location = clickedBlock == null
                    ? SimpleLocation.of(event.getPlayer().getLocation())
                    : SimpleLocation.of(clickedBlock);
            handlePlayerEvent(event, player, location, "use", target(item.getType()));
        }
        if (event.isCancelled()) {
            return;
        }
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
    public void onLiquidFlow(BlockFromToEvent event) {
        String flag = flowFlag(event.getBlock());
        if (flag == null) {
            return;
        }
        SimpleLocation from = SimpleLocation.of(event.getBlock());
        SimpleLocation to = SimpleLocation.of(event.getToBlock());
        String fromMode = getClaimFlowMode(from, flag);
        String toMode = getClaimFlowMode(to, flag);
        boolean crossingClaimBoundary = !isSameClaimContext(from, to);

        if (fromMode.equals("deny") || toMode.equals("deny")) {
            event.setCancelled(true);
            return;
        }
        if (crossingClaimBoundary && (fromMode.equals("internal") || toMode.equals("internal"))) {
            event.setCancelled(true);
        }
    }

    private String getClaimFlowMode(SimpleLocation location, String flag) {
        return claimAt(location) == null ? "allow" : getFlagValue(location, flag);
    }

    private @Nullable String flowFlag(Block block) {
        Material material = block.getType();
        if (material == Material.WATER) {
            return "flow.water";
        }
        if (material == Material.LAVA) {
            return "flow.lava";
        }
        // Waterlogged blocks can be the source of BlockFromToEvent while retaining their own material.
        if (block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            return "flow.water";
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (shouldBlockPistonMove(SimpleLocation.of(block), SimpleLocation.of(block.getRelative(event.getDirection())))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (shouldBlockPistonMove(SimpleLocation.of(block), SimpleLocation.of(block.getRelative(event.getDirection().getOppositeFace())))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean shouldBlockPistonMove(SimpleLocation from, SimpleLocation to) {
        if (isSameClaimContext(from, to)) {
            return false;
        }
        // Piston flags only control cross-border movement; internal piston contraptions remain allowed.
        return shouldBlockByBooleanFlag(from, "piston.cross-border") || shouldBlockByBooleanFlag(to, "piston.cross-border");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeBlocksDeniedByFlag(event.blockList(), explosionFlag(event.getEntity()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        String flag = switch (event.getBlock().getType()) {
            case RED_BED, BLUE_BED, BLACK_BED, BROWN_BED, CYAN_BED, GRAY_BED, GREEN_BED, LIGHT_BLUE_BED,
                 LIGHT_GRAY_BED, LIME_BED, MAGENTA_BED, ORANGE_BED, PINK_BED, PURPLE_BED, WHITE_BED, YELLOW_BED -> "explosion.bed";
            case RESPAWN_ANCHOR -> "explosion.respawn-anchor";
            default -> "explosion.other";
        };
        removeBlocksDeniedByFlag(event.blockList(), flag);
    }

    private String explosionFlag(Entity entity) {
        if (entity instanceof Creeper) {
            return "explosion.creeper";
        }
        if (entity instanceof Wither || entity instanceof WitherSkull) {
            return "explosion.wither";
        }
        if (entity instanceof Fireball) {
            return "explosion.fireball";
        }
        return switch (entity.getType()) {
            case TNT, TNT_MINECART -> "explosion.tnt";
            case END_CRYSTAL -> "explosion.crystal";
            default -> "explosion.other";
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (shouldBlockByBooleanFlag(SimpleLocation.of(event.getBlock()), "fire.ignite")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (shouldBlockByBooleanFlag(SimpleLocation.of(event.getBlock()), "fire.burn")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        String flag = isFire(event.getSource().getType()) || isFire(event.getNewState().getType())
                ? "fire.spread"
                : growthFlag(event.getNewState().getType(), "growth.other");
        if (shouldBlockByBooleanFlag(SimpleLocation.of(event.getBlock()), flag)) {
            event.setCancelled(true);
        }
    }

    private boolean isFire(Material material) {
        return material == Material.FIRE || material == Material.SOUL_FIRE;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock fallingBlock) {
            if (event.getTo() == Material.AIR) {
                storeFallingBlockOrigin(fallingBlock, event.getBlock().getLocation());
                return;
            }
            // Falling blocks are handled by origin tracking: only outside-to-inside landings are denied.
            if (shouldBlockFallingBlockLanding(fallingBlock, event.getBlock())) {
                event.setCancelled(true);
            }
            return;
        }
        if (shouldBlockByBooleanFlag(SimpleLocation.of(event.getBlock()), entityChangeBlockFlag(event.getEntity()))) {
            event.setCancelled(true);
        }
    }

    private String entityChangeBlockFlag(Entity entity) {
        if (entity instanceof Wither || entity instanceof WitherSkull) {
            return "entity-change-block.wither";
        }
        return switch (entity.getType()) {
            case ENDERMAN -> "entity-change-block.enderman";
            case RAVAGER -> "entity-change-block.ravager";
            case SHEEP -> "entity-change-block.sheep";
            case SILVERFISH -> "entity-change-block.silverfish";
            default -> "entity-change-block.other";
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallingBlockSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fallingBlock)) {
            return;
        }
        storeFallingBlockOrigin(fallingBlock, fallingBlock.getLocation());
    }

    private void storeFallingBlockOrigin(FallingBlock fallingBlock, Location origin) {
        String originClaimUuid = claimUuidAt(origin);
        if (originClaimUuid == null) {
            originClaimUuid = "";
        }
        // Store origin on the entity itself so no map cleanup is required and no memory leak is possible.
        fallingBlock.getPersistentDataContainer().set(fallingBlockOriginClaimKey, PersistentDataType.STRING, originClaimUuid);
    }

    private boolean shouldBlockFallingBlockLanding(FallingBlock fallingBlock, Block landingBlock) {
        String destinationClaimUuid = claimUuidAt(SimpleLocation.of(landingBlock));
        if (destinationClaimUuid == null) {
            return false;
        }
        PersistentDataContainer data = fallingBlock.getPersistentDataContainer();
        String originClaimUuid = data.get(fallingBlockOriginClaimKey, PersistentDataType.STRING);
        if (originClaimUuid == null || originClaimUuid.isEmpty()) {
            return true;
        }
        return !originClaimUuid.equals(destinationClaimUuid);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        if (shouldBlockByBooleanFlag(SimpleLocation.of(event.getBlock()), growthFlag(event.getNewState().getType(), "growth.other"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent event) {
        removeBlockStatesDeniedByFlag(event.getBlocks(), "growth.other");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        removeBlockStatesDeniedByFlag(event.getBlocks(), "growth.tree");
    }

    private String growthFlag(Material material, String fallbackFlag) {
        String name = material.name();
        if (name.endsWith("_SAPLING") || name.endsWith("_LEAVES") || name.endsWith("_LOG") || name.endsWith("_WOOD")) {
            return "growth.tree";
        }
        if (name.contains("VINE")) {
            return "growth.vine";
        }
        if (name.contains("MUSHROOM")) {
            return "growth.mushroom";
        }
        if (name.contains("SCULK")) {
            return "growth.sculk";
        }
        if (name.contains("AMETHYST")) {
            return "growth.amethyst";
        }
        return switch (material) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS, COCOA, NETHER_WART, SWEET_BERRY_BUSH -> "growth.crop";
            case MELON_STEM, ATTACHED_MELON_STEM, PUMPKIN_STEM, ATTACHED_PUMPKIN_STEM, MELON, PUMPKIN -> "growth.stem";
            case GRASS_BLOCK, SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN, MYCELIUM -> "growth.grass";
            case BAMBOO, BAMBOO_SAPLING -> "growth.bamboo";
            case CACTUS -> "growth.cactus";
            case SUGAR_CANE -> "growth.sugar-cane";
            case KELP, KELP_PLANT -> "growth.kelp";
            case CHORUS_FLOWER, CHORUS_PLANT -> "growth.chorus";
            default -> fallbackFlag;
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (shouldBlockByBooleanFlag(SimpleLocation.of(event.getBlock()), "decay.leaves")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (shouldBlockByBooleanFlag(SimpleLocation.of(event.getBlock()), decayFlag(event.getBlock().getType()))) {
            event.setCancelled(true);
        }
    }

    private String decayFlag(Material material) {
        String name = material.name();
        if (name.endsWith("_LEAVES")) {
            return "decay.leaves";
        }
        if (name.contains("ICE")) {
            return "decay.ice";
        }
        if (name.contains("SNOW")) {
            return "decay.snow";
        }
        if (name.contains("CORAL")) {
            return "decay.coral";
        }
        if (material == Material.FARMLAND) {
            return "decay.farmland";
        }
        return "decay.other";
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (shouldBlockByBooleanFlag(SimpleLocation.of(event.getBlock()), formFlag(event.getNewState().getType()))) {
            event.setCancelled(true);
        }
    }

    private String formFlag(Material material) {
        String name = material.name();
        if (name.contains("SNOW")) {
            return "form.snow";
        }
        if (name.contains("ICE")) {
            return "form.ice";
        }
        return switch (material) {
            case STONE, COBBLESTONE, BASALT -> "form.stone";
            case OBSIDIAN, CRYING_OBSIDIAN -> "form.obsidian";
            case WHITE_CONCRETE, ORANGE_CONCRETE, MAGENTA_CONCRETE, LIGHT_BLUE_CONCRETE, YELLOW_CONCRETE,
                 LIME_CONCRETE, PINK_CONCRETE, GRAY_CONCRETE, LIGHT_GRAY_CONCRETE, CYAN_CONCRETE,
                 PURPLE_CONCRETE, BLUE_CONCRETE, BROWN_CONCRETE, GREEN_CONCRETE, RED_CONCRETE, BLACK_CONCRETE -> "form.concrete";
            default -> "form.other";
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        String flag = portalCreateFlag(event.getReason());
        for (BlockState state : event.getBlocks()) {
            if (shouldBlockByBooleanFlag(SimpleLocation.of(state.getLocation()), flag)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private String portalCreateFlag(PortalCreateEvent.CreateReason reason) {
        return switch (reason) {
            case FIRE -> "portal-create.nether";
            case END_PLATFORM -> "portal-create.end";
            default -> "portal-create.custom";
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!isEnvironmentalSpawnReason(event.getSpawnReason())) {
            return;
        }
        String flag = event.getEntity() instanceof Monster ? "spawn.monster" : "spawn.animal";
        if (shouldBlockByBooleanFlag(location(event.getEntity()), flag)) {
            event.setCancelled(true);
        }
    }

    private boolean isEnvironmentalSpawnReason(CreatureSpawnEvent.SpawnReason reason) {
        return switch (reason) {
            case NATURAL, SPAWNER -> true;
            default -> false;
        };
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
    public void onEntityToggleGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding() || !(event.getEntity() instanceof Player player)) {
            return;
        }
        handlePlayerEvent(event, SimplePlayer.of(player), SimpleLocation.of(player.getLocation()), "fly", null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getBow() == null) {
            return;
        }
        handlePlayerEvent(event, SimplePlayer.of(player), SimpleLocation.of(player.getLocation()), "use", target(event.getBow().getType()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        handlePlayerEvent(event,
                SimplePlayer.of(event.getPlayer()),
                location(event.getItemDrop()),
                "dropitem.throw",
                target(event.getItemDrop().getItemStack().getType())
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        handlePlayerEvent(event,
                SimplePlayer.of(player),
                location(event.getItem()),
                "dropitem.pickup",
                target(event.getItem().getItemStack().getType())
        );
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        Material material = projectileUseMaterial(event.getEntity());
        if (material == null) {
            return;
        }
        handlePlayerEvent(event, SimplePlayer.of(player), SimpleLocation.of(player.getLocation()), "use", target(material));
    }

    private @Nullable Material projectileUseMaterial(Projectile projectile) {
        if (projectile instanceof EnderPearl) {
            return Material.ENDER_PEARL;
        }
        if (projectile instanceof ThrownPotion) {
            return Material.POTION;
        }
        if (projectile instanceof Trident) {
            return Material.TRIDENT;
        }
        if (projectile instanceof Snowball) {
            return Material.SNOWBALL;
        }
        if (projectile instanceof org.bukkit.entity.Egg) {
            return Material.EGG;
        }
        if (projectile instanceof ThrownExpBottle) {
            return Material.EXPERIENCE_BOTTLE;
        }
        if (projectile instanceof FishHook) {
            return Material.FISHING_ROD;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null || isSameBlock(event.getFrom(), event.getTo())) {
            return;
        }
        handleEnter(event, event.getPlayer(), event.getFrom(), event.getTo());
        handleGliding(event, event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }
        handleEnter(event, event.getPlayer(), event.getFrom(), event.getTo());
        handleGliding(event, event.getPlayer(), event.getTo());
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

    private void handleGliding(Cancellable event, Player player, Location to) {
        if (!player.isGliding()) {
            return;
        }
        if (!checkPlayerPermission(SimplePlayer.of(player), SimpleLocation.of(to), "fly", null)) {
            player.setGliding(false);
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
