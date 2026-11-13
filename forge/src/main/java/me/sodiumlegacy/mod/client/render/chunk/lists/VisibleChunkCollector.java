package me.sodiumlegacy.mod.client.render.chunk.lists;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.sodiumlegacy.mod.client.render.chunk.ChunkUpdateType;
import me.sodiumlegacy.mod.client.render.chunk.RenderSection;
import me.sodiumlegacy.mod.client.render.chunk.occlusion.OcclusionCuller;
import me.sodiumlegacy.mod.client.render.chunk.region.RenderRegion;

import java.util.*;

public class VisibleChunkCollector implements OcclusionCuller.Visitor {
    private final ObjectArrayList<ChunkRenderList> sortedRenderLists;
    private final EnumMap<ChunkUpdateType, ArrayDeque<RenderSection>> sortedRebuildLists;

    private int frame;

    public VisibleChunkCollector() {
        this.sortedRenderLists = new ObjectArrayList<>();
        this.sortedRebuildLists = new EnumMap<>(ChunkUpdateType.class);

        for (ChunkUpdateType type : ChunkUpdateType.values()) {
            this.sortedRebuildLists.put(type, new ArrayDeque<>());
        }
    }

    /**
     * Prepares the collector for a graph traversal. The instance is re-used across updates
     * (one per frame while the camera moves), so all per-traversal state is reset here. The
     * rebuild deques are cleared by the render section manager's own {@code resetRenderLists}
     * before this is called (they alias the map handed out by {@link #getRebuildLists()}).
     */
    public void begin(int frame) {
        this.frame = frame;
        this.sortedRenderLists.clear();
    }

    @Override
    public void visit(RenderSection section, boolean visible) {
        RenderRegion region = section.getRegion();
        ChunkRenderList renderList = region.getRenderList();

        // Even if a section does not have render objects, we must ensure the render list is initialized and put
        // into the sorted queue of lists, so that we maintain the correct order of draw calls.
        if (renderList.getLastVisibleFrame() != this.frame) {
            renderList.reset(this.frame);

            this.sortedRenderLists.add(renderList);
        }

        if (visible && section.getFlags() != 0) {
            renderList.add(section);
        }

        this.addToRebuildLists(section);
    }

    private void addToRebuildLists(RenderSection section) {
        ChunkUpdateType type = section.getPendingUpdate();

        if (type != null && section.getBuildCancellationToken() == null) {
            Queue<RenderSection> queue = this.sortedRebuildLists.get(type);

            if (queue.size() < type.getMaximumQueueSize()) {
                queue.add(section);
            }
        }
    }

    public SortedRenderLists createRenderLists() {
        return new SortedRenderLists(this.sortedRenderLists);
    }

    public Map<ChunkUpdateType, ArrayDeque<RenderSection>> getRebuildLists() {
        return this.sortedRebuildLists;
    }
}
