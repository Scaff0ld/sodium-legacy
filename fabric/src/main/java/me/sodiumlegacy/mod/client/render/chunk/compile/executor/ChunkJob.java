package me.sodiumlegacy.mod.client.render.chunk.compile.executor;

import me.sodiumlegacy.mod.client.render.chunk.compile.ChunkBuildContext;
import me.sodiumlegacy.mod.client.util.task.CancellationToken;

public interface ChunkJob extends CancellationToken {
    void execute(ChunkBuildContext context);

    boolean isStarted();
}
