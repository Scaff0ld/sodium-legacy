package me.sodiumlegacy.mod.client.model.quad;

import me.sodiumlegacy.mod.client.model.quad.properties.ModelQuadFacing;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;

public interface BakedQuadView extends ModelQuadView {
    ModelQuadFacing getNormalFace();
    
    boolean hasShade();

    /**
     * @return the atlas sprite this quad's UVs fall into (found lazily, cached per quad), or null
     */
    Sprite getCtmSprite();
}
