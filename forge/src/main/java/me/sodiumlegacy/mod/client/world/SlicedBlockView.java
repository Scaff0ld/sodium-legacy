package me.sodiumlegacy.mod.client.world;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * 1.8.9 port: an {@link IBlockAccess} facade over a {@link WorldSlice}.
 *
 * <p>Vanilla's dedicated renderers (the context-sensitive {@code BlockRendererDispatcher#getModelFromBlockState}
 * lookup, the fluid tessellation, and the {@code Block#isSideInvisible(...)}/{@code
 * Block#setBlockBounds(...)}/{@code Block#shouldSideBeRendered(...)} face-gating chain) all operate
 * on an {@code IBlockAccess}. Sodium's mesher works on the pooled {@code WorldSlice} snapshot, so
 * this adapter exposes that snapshot through vanilla's view interface, with light packed in
 * vanilla's {@code (sky << 20) | (block << 4)} format exactly as {@code World#getCombinedLight} does.</p>
 */
public class SlicedBlockView implements IBlockAccess {
    private final WorldSlice slice;
    private final WorldClient world;

    public SlicedBlockView(WorldSlice slice, WorldClient world) {
        this.slice = slice;
        this.world = world;
    }

    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        return this.slice.getBlockEntity(pos);
    }

    @Override
    public int getCombinedLight(BlockPos pos, int minBlockLight) {
        // Mirrors World#getCombinedLight: sky light shifted to the high half, block light (raised to
        // the requested minimum) shifted into the low byte range, both <<4 as vanilla does.
        int sky = this.slice.getLightLevel(EnumSkyBlock.SKY, pos);
        int block = this.slice.getLightLevel(EnumSkyBlock.BLOCK, pos);
        if (block < minBlockLight) {
            block = minBlockLight;
        }

        return sky << 20 | block << 4;
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return this.slice.getBlockState(pos);
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        return this.slice.getBlockState(pos).getBlock() == Blocks.air;
    }

    @Override
    public BiomeGenBase getBiomeGenForCoords(BlockPos pos) {
        return this.slice.getBiome(pos);
    }

    @Override
    public boolean extendedLevelsInChunkCache() {
        return false;
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing EnumFacing) {
        // 1.8.9's renderers never consult redstone power through the view for terrain meshing.
        return 0;
    }

    @Override
    public WorldType getWorldType() {
        return this.world.getWorldType();
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
        // 1.8.9's block meshing never queries solid sides through the view.
        return _default;
    }
}
