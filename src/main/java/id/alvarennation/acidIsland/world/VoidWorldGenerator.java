package id.alvarennation.acidIsland.world;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import java.util.Random;

public class VoidWorldGenerator extends ChunkGenerator {

    private final int acidWaterHeight;

    public VoidWorldGenerator(int acidWaterHeight) {
        this.acidWaterHeight = acidWaterHeight;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        int minHeight = worldInfo.getMinHeight(); // Y = -64
        
        // Bedrock di dasar paling bawah Y = -64
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunkData.setBlock(x, minHeight, z, Material.BEDROCK);
            }
        }

        // Air beracun dari Y = -63 sampai Y = acidWaterHeight (default 62)
        for (int y = minHeight + 1; y <= acidWaterHeight; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    chunkData.setBlock(x, y, z, Material.WATER);
                }
            }
        }
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
