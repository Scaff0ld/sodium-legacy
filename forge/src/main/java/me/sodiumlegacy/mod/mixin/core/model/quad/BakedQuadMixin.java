package me.sodiumlegacy.mod.mixin.core.model.quad;

import me.sodiumlegacy.mod.client.ctm.CtmManager;
import me.sodiumlegacy.mod.client.model.quad.BakedQuadView;
import me.sodiumlegacy.mod.client.model.quad.properties.ModelQuadFacing;
import me.sodiumlegacy.mod.client.model.quad.properties.ModelQuadFlags;
import me.sodiumlegacy.mod.client.util.ModelQuadUtil;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static me.sodiumlegacy.mod.client.util.ModelQuadUtil.*;

/**
 * 1.8.9 adaptation.
 *
 * <p>Vanilla 1.8.9's {@code BakedQuad} carries only {@code vertexData}, {@code tintIndex}
 * and {@code EnumFacing} (the light face). The 1.20.1 fields {@code TextureAtlasSprite} and {@code shade}
 * do not exist: quads are pre-atlased at bake time (the TextureAtlasSprite identity is lost), and
 * diffuse shading is global. The shadow field therefore renamed {@code face} to vanilla
 * 1.8.9's {@code EnumFacing}.</p>
 */
@Mixin(BakedQuad.class)
public class BakedQuadMixin implements BakedQuadView {
    @Shadow
    @Final
    protected int[] vertexData;

    @Shadow
    @Final
    protected int tintIndex;

    // 1.8.9: this is really the light face; vanilla named it "EnumFacing".
    @Shadow
    @Final
    protected EnumFacing face;

    @Unique
    private int flags;

    @Unique
    private int normal;

    @Unique
    private ModelQuadFacing normalFace;

    // Connected textures: the TextureAtlasSprite is recovered lazily from the quad's UVs (benign race: the
    // lookup is idempotent). Quads are re-baked on every resource reload, so this never goes stale.
    @Unique
    private TextureAtlasSprite ctmSprite;

    @Unique
    private boolean ctmSpriteResolved;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(int[] vertexData, int tintIndex, EnumFacing face, CallbackInfo ci) {
        this.normal = ModelQuadUtil.calculateNormal(this);
        this.normalFace = ModelQuadUtil.findNormalFace(this.normal);

        this.flags = ModelQuadFlags.getQuadFlags(this, face);
    }

    @Override
    public float getX(int idx) {
        return Float.intBitsToFloat(this.vertexData[vertexOffset(idx) + POSITION_INDEX]);
    }

    @Override
    public float getY(int idx) {
        return Float.intBitsToFloat(this.vertexData[vertexOffset(idx) + POSITION_INDEX + 1]);
    }

    @Override
    public float getZ(int idx) {
        return Float.intBitsToFloat(this.vertexData[vertexOffset(idx) + POSITION_INDEX + 2]);
    }

    @Override
    public int getColor(int idx) {
        return this.vertexData[vertexOffset(idx) + COLOR_INDEX];
    }

    @Override
    public TextureAtlasSprite getSprite() {
        // 1.8.9 quads store only pre-mapped atlas UVs; the originating TextureAtlasSprite is no longer
        // reachable from here. TextureAtlasSprite animation is ticked globally by vanilla's
        // TextureManager, so no per-section tracking is needed (see SpriteUtil).
        return null;
    }

    @Override
    public TextureAtlasSprite getCtmSprite() {
        if (!this.ctmSpriteResolved) {
            this.ctmSprite = CtmManager.findTextureAtlasSprite(this);
            // Only cache a miss once an atlas snapshot exists, otherwise early quads would stay null.
            this.ctmSpriteResolved = this.ctmSprite != null;
        }

        return this.ctmSprite;
    }

    @Override
    public float getTexU(int idx) {
        return Float.intBitsToFloat(this.vertexData[vertexOffset(idx) + TEXTURE_INDEX]);
    }

    @Override
    public float getTexV(int idx) {
        return Float.intBitsToFloat(this.vertexData[vertexOffset(idx) + TEXTURE_INDEX + 1]);
    }

    @Override
    public int getFlags() {
        return this.flags;
    }

    @Override
    public int getColorIndex() {
        return tintIndex;
    }

    @Override
    public ModelQuadFacing getNormalFace() {
        return this.normalFace;
    }

    @Override
    public EnumFacing getLightFace() {
        return this.face;
    }

    @Override
    @Unique(silent = true) // no 1.8.9 method with this name; kept silent for parity with upstream
    public boolean hasShade() {
        // 1.8.9 quads carry no per-quad shade flag; vanilla applies diffuse shading everywhere.
        return true;
    }
}

