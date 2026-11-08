package me.sodiumlegacy.mod.client.ctm;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

import java.util.Set;

/**
 * One parsed CTM rule (an OptiFine/MCPatcher {@code .properties} file or a built-in generated rule).
 */
public final class CtmRule {
    public enum Method {
        CTM, HORIZONTAL, VERTICAL, TOP, FIXED, RANDOM, REPEAT, GENERATED_GLASS
    }

    public enum Connect {
        BLOCK, TILE, STATE
    }

    public static final int ALL_FACES = 0x3F;

    public String name;
    public Method method;
    public Connect connect;
    public int priority;

    /** Normalized atlas names ("minecraft:blocks/glass") this rule applies to. */
    public Set<String> matchTiles;
    public Set<Block> matchBlocks;
    /** Bit i set = metadata i allowed. */
    public int metadataMask = 0xFFFF;
    /** Bit per {@link EnumFacing#ordinal()}. */
    public int facesMask = ALL_FACES;

    /** Tile atlas names to resolve after stitching ("minecraft:sodiumctm/..." or an existing vanilla TextureAtlasSprite). */
    public String[] tileNames = new String[0];
    public TextureAtlasSprite[] tiles = new TextureAtlasSprite[0];

    public int[] weights;
    public int totalWeight;
    public int width = 1;
    public int height = 1;

    /** For GENERATED_GLASS: TextureAtlasSprite per canonical mask, indexed 0..255 (null when not generated). */
    public TextureAtlasSprite[] generated;

    public boolean appliesToFace(EnumFacing face) {
        return (this.facesMask & (1 << face.ordinal())) != 0;
    }

    public boolean appliesToMeta(int meta) {
        return (this.metadataMask & (1 << (meta & 15))) != 0;
    }
}


