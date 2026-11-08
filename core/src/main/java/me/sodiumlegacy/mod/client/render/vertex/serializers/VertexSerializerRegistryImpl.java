package me.sodiumlegacy.mod.client.render.vertex.serializers;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import me.sodiumlegacy.mod.client.render.vertex.serializers.generated.VertexSerializerFactory;
import me.sodiumlegacy.mod.api.vertex.format.VertexFormatDescription;
import me.sodiumlegacy.mod.api.vertex.serializer.VertexSerializer;
import me.sodiumlegacy.mod.api.vertex.serializer.VertexSerializerRegistry;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.StampedLock;

public class VertexSerializerRegistryImpl implements VertexSerializerRegistry {
    private static final Logger LOGGER = LogManager.getLogger(VertexSerializerRegistryImpl.class);

    private static final Path CLASS_DUMP_PATH;

    static {
        String classDumpPath = System.getProperty("sodiumlegacy.codegen.dump", null);

        if (classDumpPath != null) {
            // 1.8.9 port: Path.of does not exist until Java 11; Paths.get is the Java 8 equivalent.
            CLASS_DUMP_PATH = Paths.get(classDumpPath);
        } else {
            CLASS_DUMP_PATH = null;
        }
    }

    private final Long2ReferenceMap<VertexSerializer> cache = new Long2ReferenceOpenHashMap<>();
    private final StampedLock lock = new StampedLock();

    @Override
    public VertexSerializer get(VertexFormatDescription srcFormat, VertexFormatDescription dstFormat) {
        long identifier = createKey(srcFormat, dstFormat);
        VertexSerializer serializer = this.find(identifier);

        if (serializer == null) {
            serializer = this.create(identifier, srcFormat, dstFormat);
        }

        return serializer;
    }

    private VertexSerializer create(long identifier, VertexFormatDescription srcFormat, VertexFormatDescription dstFormat) {
        long stamp = this.lock.writeLock();

        try {
            // Additional lookup to avoid calling createSerializer twice
            // Necessary because 'find' only acquires a non-exclusive lock
            VertexSerializer cached = this.cache.get(identifier);
            if (cached != null) {
                return cached;
            }

            // Create serializer
            VertexSerializer serializer = createSerializer(srcFormat, dstFormat);
            this.cache.put(identifier, serializer);
            return serializer;
        } finally {
            this.lock.unlockWrite(stamp);
        }
    }

    private VertexSerializer find(long identifier) {
        long stamp = this.lock.readLock();

        try {
            return this.cache.get(identifier);
        } finally {
            this.lock.unlockRead(stamp);
        }
    }

    private static VertexSerializer createSerializer(VertexFormatDescription srcVertexFormat, VertexFormatDescription dstVertexFormat) {
        String identifier = String.format("%04X$%04X", srcVertexFormat.id(), dstVertexFormat.id());

        VertexSerializerFactory.Bytecode bytecode = VertexSerializerFactory.generate(srcVertexFormat, dstVertexFormat, identifier);

        if (CLASS_DUMP_PATH != null) {
            dumpClass(identifier, bytecode);
        }

        Class<?> clazz = VertexSerializerFactory.define(bytecode);
        Constructor<?> constructor;

        try {
            constructor = clazz.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to find constructor of generated class", e);
        }

        Object instance;

        try {
            instance = constructor.newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to instantiate generated class", e);
        }

        VertexSerializer serializer;

        try {
            serializer = (VertexSerializer) instance;
        } catch (ClassCastException e) {
            throw new RuntimeException("Failed to cast generated class to interface type", e);
        }

        return serializer;
    }

    private static void dumpClass(String id, VertexSerializerFactory.Bytecode bytecode) {
        Path path = CLASS_DUMP_PATH.resolve(String.format("VertexSerializer$Impl$%s.class", id));

        try {
            Files.write(path, bytecode.copy());
        } catch (IOException e) {
            LOGGER.warn("Could not dump bytecode to location: {}", path, e);
        }
    }

    private static long createKey(VertexFormatDescription a, VertexFormatDescription b) {
        return (long) a.id() & 0xffffffffL | ((long) b.id() & 0xffffffffL) << 32;
    }
}
