package id.alvarennation.acidIsland.world;

import id.alvarennation.acidIsland.AcidIsland;
import id.alvarennation.acidIsland.island.Island;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class IslandValueScanner {

    private final AcidIsland plugin;
    private final Queue<Island> queue = new ArrayDeque<>();
    private final Set<UUID> queuedOwners = new HashSet<>();

    private Island activeIsland;
    private BukkitTask activeAsyncTask;
    private boolean cancelled;

    public IslandValueScanner(AcidIsland plugin) {
        this.plugin = plugin;
    }

    public void enqueue(Island island) {
        if (island == null || cancelled) {
            return;
        }
        UUID owner = island.getOwner();
        if ((activeIsland != null && activeIsland.getOwner().equals(owner)) || queuedOwners.contains(owner)) {
            return;
        }

        island.setLevelScanInProgress(true);
        queuedOwners.add(owner);
        queue.add(island);
        plugin.getLogger().fine("Queued island value scan for " + owner + ". Queue size: " + queue.size());
        processNext();
    }

    public void cancelAll() {
        cancelled = true;
        if (activeAsyncTask != null) {
            activeAsyncTask.cancel();
            activeAsyncTask = null;
        }
        if (activeIsland != null) {
            activeIsland.setLevelScanInProgress(false);
            activeIsland = null;
        }
        for (Island island : queue) {
            island.setLevelScanInProgress(false);
        }
        queue.clear();
        queuedOwners.clear();
        cancelled = false;
    }

    private void processNext() {
        if (activeIsland != null || queue.isEmpty()) {
            return;
        }

        activeIsland = queue.poll();
        queuedOwners.remove(activeIsland.getOwner());
        startScan(activeIsland);
    }

    private void startScan(Island island) {
        try {
            World world = plugin.getWorldManager().getAcidWorld();
            FileConfiguration config = plugin.getConfigManager().getConfig();
            int borderSize = plugin.getIslandManager().getBorderSize(island);
            int half = borderSize / 2;
            int minX = island.getX() - half;
            int maxX = island.getX() + half;
            int minZ = island.getZ() - half;
            int maxZ = island.getZ() + half;
            int minY = Math.max(world.getMinHeight(), config.getInt("level.scan-min-y", 63));
            int maxY = Math.min(world.getMaxHeight() - 1, config.getInt("level.scan-max-y", 320));

            if (maxY < minY) {
                finishScan(island, 0L);
                return;
            }

            ScanContext context = new ScanContext(
                    island,
                    world,
                    minX,
                    maxX,
                    minZ,
                    maxZ,
                    minY,
                    maxY,
                    Math.max(1, config.getInt("level.chunks-per-batch", 4)),
                    Math.max(1, config.getInt("level.points-per-level", 100)),
                    config.getLong("level.default-block-value", 1L),
                    buildBlockValues(config.getConfigurationSection("level.block-values")),
                    buildChunkList(minX, maxX, minZ, maxZ)
            );

            plugin.getLogger().fine("Starting island value scan for " + island.getOwner() + " across " + context.chunks.size() + " chunks.");
            scanNextBatch(context);
        } catch (RuntimeException ex) {
            failScan(island, ex);
        }
    }

    private void scanNextBatch(ScanContext context) {
        if (cancelled || activeIsland == null || !activeIsland.getOwner().equals(context.island.getOwner())) {
            context.island.setLevelScanInProgress(false);
            return;
        }

        if (context.nextChunkIndex >= context.chunks.size()) {
            finishScan(context.island, context.value);
            return;
        }

        int end = Math.min(context.nextChunkIndex + context.chunksPerBatch, context.chunks.size());
        List<ChunkCoord> batch = context.chunks.subList(context.nextChunkIndex, end);
        context.nextChunkIndex = end;

        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (ChunkCoord coord : batch) {
            futures.add(context.world.getChunkAtAsync(coord.x, coord.z));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> failScan(context.island, throwable));
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    List<SnapshotSlice> snapshots = new ArrayList<>();
                    for (int i = 0; i < futures.size(); i++) {
                        ChunkCoord coord = batch.get(i);
                        ChunkSnapshot snapshot = futures.get(i).join().getChunkSnapshot(false, false, false);
                        snapshots.add(new SnapshotSlice(coord, snapshot));
                    }

                    activeAsyncTask = plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        long batchValue = 0L;
                        try {
                            for (SnapshotSlice slice : snapshots) {
                                batchValue += scanSnapshot(context, slice);
                            }
                        } catch (RuntimeException ex) {
                            plugin.getServer().getScheduler().runTask(plugin, () -> failScan(context.island, ex));
                            return;
                        }

                        long finalBatchValue = batchValue;
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            activeAsyncTask = null;
                            context.value += finalBatchValue;
                            scanNextBatch(context);
                        });
                    });
                } catch (RuntimeException ex) {
                    failScan(context.island, ex);
                }
            });
        });
    }

    private long scanSnapshot(ScanContext context, SnapshotSlice slice) {
        int chunkMinX = slice.coord.x << 4;
        int chunkMinZ = slice.coord.z << 4;
        int localMinX = Math.max(0, context.minX - chunkMinX);
        int localMaxX = Math.min(15, context.maxX - chunkMinX);
        int localMinZ = Math.max(0, context.minZ - chunkMinZ);
        int localMaxZ = Math.min(15, context.maxZ - chunkMinZ);

        long value = 0L;
        for (int x = localMinX; x <= localMaxX; x++) {
            for (int z = localMinZ; z <= localMaxZ; z++) {
                for (int y = context.minY; y <= context.maxY; y++) {
                    Material material = slice.snapshot.getBlockType(x, y, z);
                    if (material.isAir() || material == Material.WATER || material == Material.BEDROCK) {
                        continue;
                    }
                    value += context.blockValues.getOrDefault(material, context.defaultValue);
                }
            }
        }
        return value;
    }

    private void finishScan(Island island, long value) {
        try {
            int level = (int) Math.max(0, value / Math.max(1, plugin.getConfigManager().getConfig().getInt("level.points-per-level", 100)));
            island.setLevelCache(value, level);
        } finally {
            island.setLevelScanInProgress(false);
            activeIsland = null;
            processNext();
        }
    }

    private void failScan(Island island, Throwable throwable) {
        plugin.getLogger().log(Level.WARNING, "Island value scan failed for " + island.getOwner() + ".", throwable);
        island.setLevelScanInProgress(false);
        activeIsland = null;
        activeAsyncTask = null;
        processNext();
    }

    private EnumMap<Material, Long> buildBlockValues(ConfigurationSection section) {
        EnumMap<Material, Long> values = new EnumMap<>(Material.class);
        if (section == null) {
            return values;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material != null) {
                values.put(material, section.getLong(key));
            }
        }
        return values;
    }

    private List<ChunkCoord> buildChunkList(int minX, int maxX, int minZ, int maxZ) {
        int minChunkX = Math.floorDiv(minX, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);

        List<ChunkCoord> chunks = new ArrayList<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                chunks.add(new ChunkCoord(cx, cz));
            }
        }
        return chunks;
    }

    private static final class ScanContext {
        private final Island island;
        private final World world;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private final int minY;
        private final int maxY;
        private final int chunksPerBatch;
        private final int pointsPerLevel;
        private final long defaultValue;
        private final EnumMap<Material, Long> blockValues;
        private final List<ChunkCoord> chunks;
        private int nextChunkIndex;
        private long value;

        private ScanContext(Island island, World world, int minX, int maxX, int minZ, int maxZ, int minY, int maxY,
                            int chunksPerBatch, int pointsPerLevel, long defaultValue,
                            EnumMap<Material, Long> blockValues, List<ChunkCoord> chunks) {
            this.island = island;
            this.world = world;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.minY = minY;
            this.maxY = maxY;
            this.chunksPerBatch = chunksPerBatch;
            this.pointsPerLevel = pointsPerLevel;
            this.defaultValue = defaultValue;
            this.blockValues = blockValues;
            this.chunks = chunks;
        }
    }

    private record ChunkCoord(int x, int z) {
    }

    private record SnapshotSlice(ChunkCoord coord, ChunkSnapshot snapshot) {
    }
}
