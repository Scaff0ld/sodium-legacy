package me.sodiumlegacy.mod.client.model.quad;

import me.sodiumlegacy.mod.client.model.quad.properties.ModelQuadFacing;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

public interface BakedQuadView extends ModelQuadView {
    ModelQuadFacing getNormalFace();
    
    boolean hasShade();

    /**
     * @return the atlas TextureAtlasSprite this quad's UVs fall into (found lazily, cached per quad), or null
     */
    TextureAtlasSprite getCtmSprite();
}


