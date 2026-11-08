package me.sodiumlegacy.mod.client.world;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.level.LevelGeneratorType;

/**
 * 1.8.9 port: a {@link BlockView} facade over a {@link WorldSlice}.
 *
 * <p>Vanilla's dedicated renderers (the context-sensitive {@code BlockRenderManager#getModel}
 * lookup, the {@code FluidRenderer}, and the {@code Block#isSideInvisible(...)}/{@code
 * Block#setBoundingBox(...)} face-gating chain) all operate on a {@code BlockView}. Sodium's
 * mesher works on the pooled {@code WorldSlice} snapshot, so this adapter exposes that snapshot
 * through vanilla's view interface, with light packed in vanilla's {@code (sky << 20) | (block << 4)}
 * format exactly as {@code World#getLight(BlockPos, int)} does.</p>
 */
public class SlicedBlockView implements BlockView {
    private final WorldSlice slice;
    private final ClientWorld world;

    public SlicedBlockView(WorldSlice slice, ClientWorld world) {
        this.slice = slice;
        this.world = world;
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return this.slice.getBlockEntity(pos);
    }

    @Override
    public int getLight(BlockPos pos, int minBlockLight) {
        // Mirrors World#getLight: sky light shifted to the high half, block light (raised to the
        // requested minimum) shifted into the low byte range, both <<4 as vanilla does.
        int sky = this.slice.getLightLevel(LightType.SKY, pos);
        int block = this.slice.getLightLevel(LightType.BLOCK, pos);
        if (block < minBlockLight) {
            block = minBlockLight;
        }

        return sky << 20 | block << 4;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.slice.getBlockState(pos);
    }

    @Override
    public boolean isAir(BlockPos pos) {
        return this.slice.getBlockState(pos).getBlock() == Blocks.AIR;
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        return this.slice.getBiome(pos);
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public int getStrongRedstonePower(BlockPos pos, Direction direction) {
        // 1.8.9's renderers never consult redstone through the view for terrain meshing.
        return 0;
    }

    @Override
    public LevelGeneratorType getGeneratorType() {
        return this.world.getGeneratorType();
    }
}
