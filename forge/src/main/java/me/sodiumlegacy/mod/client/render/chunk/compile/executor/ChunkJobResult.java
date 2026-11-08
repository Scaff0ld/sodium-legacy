package me.sodiumlegacy.mod.client.render.chunk.compile.executor;

import net.minecraft.util.ReportedException;

public class ChunkJobResult<OUTPUT> {
    private final OUTPUT output;
    private final Throwable throwable;

    private ChunkJobResult(OUTPUT output, Throwable throwable) {
        this.output = output;
        this.throwable = throwable;
    }

    public static <OUTPUT> ChunkJobResult<OUTPUT> exceptionally(Throwable throwable) {
        return new ChunkJobResult<>(null, throwable);
    }

    public static <OUTPUT> ChunkJobResult<OUTPUT> successfully(OUTPUT output) {
        return new ChunkJobResult<>(output, null);
    }

    public OUTPUT unwrap() {
        if (this.throwable instanceof ReportedException) {
            // Propagate ReportedExceptions directly to provide extra information
            ReportedException crashException = (ReportedException) this.throwable;

            throw crashException;
        } else if (this.throwable != null) {
            throw new RuntimeException("Exception thrown while executing job", this.throwable);
        }

        return this.output;
    }
}
