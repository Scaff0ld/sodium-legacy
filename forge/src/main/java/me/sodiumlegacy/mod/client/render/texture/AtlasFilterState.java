package me.sodiumlegacy.mod.client.render.texture;

import me.sodiumlegacy.mod.client.SodiumClientMod;
import me.sodiumlegacy.mod.client.gui.SodiumGameOptions;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

/**
 * Applies the user's mipmap type / anisotropic filtering settings to the block atlas that
 * is currently bound on the active texture unit.
 *
 * <p>Vanilla 1.8.9 re-writes the atlas' min filter itself at several points of a frame
 * ({@code setBlurMipmap}/{@code restoreLastBlurMipmap}), so this is re-applied right before each
 * terrain pass instead of being cached. It is only a handful of glTexParameter calls.</p>
 */
public final class AtlasFilterState {
    private static float maxAnisotropy = -1.0F;

    private AtlasFilterState() {
    }

    /**
     * Must be called with the block atlas bound on the active texture unit.
     */
    public static void applyToBoundAtlas() {
        SodiumGameOptions.QualitySettings quality = SodiumClientMod.options().quality;

        int mipmapLevels = Minecraft.getMinecraft().gameSettings.mipmapLevels;

        // With 0 mipmap levels the atlas has no mip chain: using a *_MIPMAP_* min filter on it
        // would make the texture incomplete (renders white), so leave vanilla's GL_NEAREST alone.
        if (mipmapLevels > 0) {
            SodiumGameOptions.MipmapType type = quality.mipmapType != null
                    ? quality.mipmapType
                    : SodiumGameOptions.MipmapType.LINEAR;

            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, type.getGlMinFilter());
            // Close-up stays pixel-crisp, exactly like vanilla.
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        }

        float max = getMaxAnisotropy();

        if (max > 1.0F) {
            float wanted = Math.max(1.0F, Math.min(quality.anisotropicLevel, max));

            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, wanted);
        }
    }

    /**
     * @return The maximum anisotropy supported by the GPU, or 1.0 if the extension is unavailable
     */
    public static float getMaxAnisotropy() {
        if (maxAnisotropy < 0.0F) {
            if (GLContext.getCapabilities().GL_EXT_texture_filter_anisotropic) {
                maxAnisotropy = GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            } else {
                maxAnisotropy = 1.0F;
            }
        }

        return maxAnisotropy;
    }
}



