package cn.jason31416.betterresidence.core;

import cn.jason31416.betterresidence.handler.DataHandler;
import cn.jason31416.planetlib.util.PluginLogger;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import cn.jason31416.planetlib.wrapper.SimpleWorld;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;

import java.util.*;

public class TestClaimGenerator {
    private static final int TOTAL_CLAIMS = 100_00;
    private static final int SPACE_BOUND = 1000;
    private static final int MIN_Y = 50;
    private static final int MAX_Y = 100;
    private static final int MIN_SIZE = 20;
    private static final int MAX_SIZE_NORMAL = 100;
    private static final int MAX_SIZE_EXCEPTION = 500;
    private static final double EXCEPTION_CHANCE = 0.05;

    private static final String[] ADJECTIVES = {
        "Great", "High", "Low", "Dark", "Bright", "Old", "New", "Grand", "Silent", "Frozen",
        "Burning", "Ancient", "Mystic", "Sacred", "Hidden", "Lost", "Deep", "Tall", "Wide", "Long",
        "Swift", "Slow", "Calm", "Wild", "Fierce", "Gentle", "Rough", "Smooth", "Sharp", "Blunt",
        "Hard", "Soft", "Hot", "Cold", "Warm", "Cool", "Dry", "Wet", "Rich", "Poor",
        "Strong", "Weak", "Bold", "Timid", "Proud", "Humble", "Wise", "Foolish", "Brave", "Cowardly",
        "Lucky", "Unlucky", "Happy", "Sad", "Joyful", "Gloomy", "Peaceful", "Chaotic", "Orderly", "Messy",
        "Clean", "Dirty", "Pure", "Corrupt", "Holy", "Unholy", "Divine", "Mortal", "Eternal", "Fleeting",
        "Vast", "Tiny", "Giant", "Mini", "Mega", "Ultra", "Super", "Hyper", "Neo", "Retro",
        "Crystal", "Iron", "Golden", "Silver", "Copper", "Stone", "Wooden", "Crystal", "Ruby", "Emerald",
        "Sapphire", "Diamond", "Obsidian", "Marble", "Granite", "Sand", "Snow", "Ice", "Fire", "Storm"
    };

    private static final String[] NOUNS = {
        "Mountain", "Valley", "Forest", "River", "Lake", "Ocean", "Desert", "Plains", "Hills", "Cliffs",
        "Canyon", "Gorge", "Ravine", "Basin", "Plateau", "Mesa", "Butte", "Peak", "Summit", "Ridge",
        "Slope", "Valley", "Glade", "Grove", "Thicket", "Swamp", "Marsh", "Bog", "Fen", "Moor",
        "Tundra", "Savanna", "Jungle", "Rainforest", "Wetland", "Floodland", "Badlands", "Wasteland", "Hinterland", "Backland",
        "Highland", "Lowland", "Midland", "Northland", "Southland", "Eastland", "Westland", "Heartland", "Mainland", "Island",
        "Peninsula", "Archipelago", "Cape", "Bay", "Gulf", "Inlet", "Fjord", "Lagoon", "Reef", "Atoll",
        "Harbor", "Port", "Dock", "Wharf", "Pier", "Bridge", "Crossroads", "Junction", "Gateway", "Portal",
        "Tower", "Castle", "Fortress", "Citadel", "Palace", "Temple", "Shrine", "Sanctuary", "Refuge", "Haven",
        "Kingdom", "Empire", "Realm", "Domain", "Territory", "Province", "District", "Region", "Zone", "Area",
        "Settlement", "Village", "Town", "City", "Metropolis", "Capital", "Outpost", "Colony", "Camp", "Base"
    };

    private final List<String> worldUuids;
    private final Random random;
    private final Map<String, List<AreaBox>> worldAreas;

    public TestClaimGenerator() {
        this.worldUuids = new ArrayList<>();
        this.random = new Random();
        this.worldAreas = new HashMap<>();

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            String uuid = world.getUID().toString();
            worldUuids.add(uuid);
            worldAreas.put(uuid, new ArrayList<>());
        }
    }

    @SneakyThrows
    public void generate() {
        if (worldUuids.isEmpty()) {
            PluginLogger.error("No worlds available for claim generation");
            return;
        }

        PluginLogger.info("Starting generation of " + TOTAL_CLAIMS + " test claims...");
        long startTime = System.currentTimeMillis();

        int created = 0;
        int attempts = 0;
        int maxAttempts = TOTAL_CLAIMS * 10;

        while (created < TOTAL_CLAIMS && attempts < maxAttempts) {
            attempts++;

            try {
                String worldUuid = worldUuids.get(random.nextInt(worldUuids.size()));
                AreaBox areaBox = generateRandomAreaBox();

                if (!isValidPlacement(worldUuid, areaBox)) {
                    continue;
                }

                Claim parentClaim = findParentClaim(worldUuid, areaBox);
                String parentUuid = parentClaim != null ? parentClaim.getUuid() : null;

                String ownerName = "HelloThere";
                SimplePlayer owner = SimplePlayer.of(ownerName);
                String claimName = generateClaimName();

                Claim claim = ClaimManager.createClaim(owner, claimName, parentUuid, worldUuid, areaBox);

                worldAreas.get(worldUuid).add(areaBox);
                created++;

                if (created % 1000 == 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    PluginLogger.info("Progress: " + created + "/" + TOTAL_CLAIMS + " claims created (" + elapsed + "ms)");
                }
            } catch (Exception e) {
                // Skip failed attempts
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        PluginLogger.info("Claim generation complete: " + created + " claims created in " + elapsed + "ms");
    }

    private AreaBox generateRandomAreaBox() {
        int sizeX = generateSize();
        int sizeZ = generateSize();

        int minX = random.nextInt(SPACE_BOUND * 2 - sizeX) - SPACE_BOUND;
        int minZ = random.nextInt(SPACE_BOUND * 2 - sizeZ) - SPACE_BOUND;

        int minY = MIN_Y + random.nextInt(MAX_Y - MIN_Y - 10);
        int maxY = minY + 10 + random.nextInt(Math.min(MAX_Y - minY - 10, 50));

        return new AreaBox(minX, minX + sizeX - 1, minY, maxY, minZ, minZ + sizeZ - 1);
    }

    private int generateSize() {
        if (random.nextDouble() < EXCEPTION_CHANCE) {
            return MAX_SIZE_NORMAL + random.nextInt(MAX_SIZE_EXCEPTION - MAX_SIZE_NORMAL + 1);
        }
        return MIN_SIZE + random.nextInt(MAX_SIZE_NORMAL - MIN_SIZE + 1);
    }

    private boolean isValidPlacement(String worldUuid, AreaBox newBox) {
        for (AreaBox existing : worldAreas.get(worldUuid)) {
            if (existing.overlaps(newBox)) {
                if (!existing.contains(newBox) && !newBox.contains(existing)) {
                    return false;
                }
            }
        }
        return true;
    }

    private Claim findParentClaim(String worldUuid, AreaBox box) {
        List<ClaimManager.OverlappingClaimAreaInfo> overlapping = ClaimManager.fetchOverlappingClaimAreas(worldUuid, box);

        if (overlapping.isEmpty()) {
            return null;
        }

        Claim deepestClaim = null;
        int maxDepth = -1;

        Set<String> checkedUuids = new HashSet<>();
        for (ClaimManager.OverlappingClaimAreaInfo info : overlapping) {
            if (!checkedUuids.add(info.claimUuid())) {
                continue;
            }

            Claim claim = ClaimManager.fetchClaim(info.claimUuid());
            if (claim == null) continue;

            if (ClaimManager.isAreaCoveredByClaim(claim.getUuid(), worldUuid, box)) {
                int depth = claim.getDepth();
                if (depth > maxDepth) {
                    maxDepth = depth;
                    deepestClaim = claim;
                }
            }
        }

        return deepestClaim;
    }

    private String generateClaimName() {
        String adj = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[random.nextInt(NOUNS.length)];
        int num = random.nextInt(1000000);
        return adj + noun + String.format("%04d", num);
    }
}
