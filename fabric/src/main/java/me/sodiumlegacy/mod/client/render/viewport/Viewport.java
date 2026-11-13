package me.sodiumlegacy.mod.client.render.viewport;

import me.sodiumlegacy.mod.client.util.SectionPos;
import me.sodiumlegacy.mod.client.render.viewport.frustum.Frustum;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;

public final class Viewport {
    private Frustum frustum;
    private CameraTransform transform;

    private SectionPos chunkCoords;
    private BlockPos blockCoords;

    public Viewport(Frustum frustum, Vector3d position) {
        // `transform` is re-used via set(); chunkCoords / blockCoords are immutable MC/utility
        // types, so they are still fresh allocations per set() (kept small on purpose).
        this.transform = new CameraTransform(0.0, 0.0, 0.0);

        this.set(frustum, position);
    }

    /**
     * Re-populates this viewport in place. Avoids allocating a fresh Viewport + CameraTransform
     * (and letting the old ones die young in the nursery) on the per-frame terrain setup path.
     */
    public Viewport set(Frustum frustum, Vector3d position) {
        this.frustum = frustum;
        this.transform.set(position.x, position.y, position.z);

        this.chunkCoords = SectionPos.from(
                SectionPos.getSectionCoord((int) Math.floor(position.x)),
                SectionPos.getSectionCoord((int) Math.floor(position.y)),
                SectionPos.getSectionCoord((int) Math.floor(position.z))
        );

        this.blockCoords = new BlockPos(
                (int) Math.floor(position.x),
                (int) Math.floor(position.y),
                (int) Math.floor(position.z));

        return this;
    }

    public boolean isBoxVisible(int intOriginX, int intOriginY, int intOriginZ, float floatSizeX, float floatSizeY, float floatSizeZ) {
        float floatOriginX = (intOriginX - this.transform.intX) - this.transform.fracX;
        float floatOriginY = (intOriginY - this.transform.intY) - this.transform.fracY;
        float floatOriginZ = (intOriginZ - this.transform.intZ) - this.transform.fracZ;

        return this.frustum.testAab(
                floatOriginX - floatSizeX,
                floatOriginY - floatSizeY,
                floatOriginZ - floatSizeZ,

                floatOriginX + floatSizeX,
                floatOriginY + floatSizeY,
                floatOriginZ + floatSizeZ
        );
    }

    public CameraTransform getTransform() {
        return this.transform;
    }

    public SectionPos getChunkCoord() {
        return this.chunkCoords;
    }

    public BlockPos getBlockCoord() {
        return this.blockCoords;
    }
}
