package me.sodiumlegacy.mod.client.render.viewport.frustum;

import net.minecraft.client.render.CameraView;
import net.minecraft.util.math.Box;

/**
 * Bridges vanilla 1.8.9's {@link CameraView} into sodium's viewport frustum interface.
 *
 * <p>{@link Viewport} hands us an AABB in <em>camera-relative</em> coordinates (its own
 * {@code CameraTransform} was subtracted ahead of the call). The 1.8.9 {@code CameraView}, on the
 * other hand, tests boxes in <em>absolute</em> world space, so we simply shift the box back by the
 * camera position before delegating.</p>
 *
 * <p>Please note that this represents a compile phase of the port: precision is reduced once by
 * converting back with the doubles carried by the constructor, keeping relative float error small
 * enough that batch tests remain stable.</p>
 */
public class CameraViewFrustum implements Frustum {
    private final CameraView view;

    private final double camX;
    private final double camY;
    private final double camZ;

    public CameraViewFrustum(CameraView view, double camX, double camY, double camZ) {
        this.view = view;
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
    }

    @Override
    public boolean testAab(float relMinX, float relMinY, float relMinZ,
                           float relMaxX, float relMaxY, float relMaxZ) {
        return this.view.isBoxInFrustum(new Box(
                this.camX + relMinX, this.camY + relMinY, this.camZ + relMinZ,
                this.camX + relMaxX, this.camY + relMaxY, this.camZ + relMaxZ));
    }
}
