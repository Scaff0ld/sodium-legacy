package me.sodiumlegacy.mod.mixin.core.model.quad;

import me.sodiumlegacy.mod.client.ctm.CtmManager;
import me.sodiumlegacy.mod.client.model.quad.BakedQuadView;
import me.sodiumlegacy.mod.client.model.quad.properties.ModelQuadFacing;
import me.sodiumlegacy.mod.client.model.quad.properties.ModelQuadFlags;
import me.sodiumlegacy.mod.client.util.ModelQuadUtil;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
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
 * <p>Vanilla 1.8.9's {@code BakedQuad} carries only {@code vertexData}, {@code colorIndex}
 * and {@code direction} (the light face). The 1.20.1 fields {@code sprite} and {@code shade}
 * do not exist: quads are pre-atlased at bake time (the sprite identity is lost), and
 * diffuse shading is global. The shadow field therefore renamed {@code face} to vanilla
 * 1.8.9's {@code direction}.</p>
 */
@Mixin(BakedQuad.class)
public class BakedQuadMixin implements BakedQuadView {
    @Shadow
    @Final
    protected int[] vertexData;

    @Shadow
    @Final
    protected int colorIndex;

    // 1.8.9: this is really the light face; vanilla named it "direction".
    @Shadow
    @Final
    protected Direction direction;

    @Unique
    private int flags;

    @Unique
    private int normal;

    @Unique
    private ModelQuadFacing normalFace;

    // Connected textures: the sprite is recovered lazily from the quad's UVs (benign race: the
    // lookup is idempotent). Quads are re-baked on every resource reload, so this never goes stale.
    @Unique
    private Sprite ctmSprite;

    @Unique
    private boolean ctmSpriteResolved;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(int[] vertexData, int colorIndex, Direction direction, CallbackInfo ci) {
        this.normal = ModelQuadUtil.calculateNormal(this);
        this.normalFace = ModelQuadUtil.findNormalFace(this.normal);

        this.flags = ModelQuadFlags.getQuadFlags(this, direction);
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
    public Sprite getSprite() {
        // 1.8.9 quads store only pre-mapped atlas UVs; the originating sprite is no longer
        // reachable from here. Sprite animation is ticked globally by vanilla's
        // TextureManager, so no per-section tracking is needed (see SpriteUtil).
        return null;
    }

    @Override
    public Sprite getCtmSprite() {
        if (!this.ctmSpriteResolved) {
            this.ctmSprite = CtmManager.findSprite(this);
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
        return this.colorIndex;
    }

    @Override
    public ModelQuadFacing getNormalFace() {
        return this.normalFace;
    }

    @Override
    public Direction getLightFace() {
        return this.direction;
    }

    @Override
    @Unique(silent = true) // no 1.8.9 method with this name; kept silent for parity with upstream
    public boolean hasShade() {
        // 1.8.9 quads carry no per-quad shade flag; vanilla applies diffuse shading everywhere.
        return true;
    }
}
