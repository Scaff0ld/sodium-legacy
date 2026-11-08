package me.sodiumlegacy.mod.client.ctm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Finds the file-based resource packs that are active right now (zip or folder) and exposes
 * their entries, so CTM {@code .properties} files and tile PNGs can be read directly.
 *
 * <p>Packs are discovered by reflection on the live resource manager and matched by field
 * <i>type</i> (Map / List / File), so this does not depend on any mapped field names.</p>
 */
public final class CtmPackReader {
    private static final Logger LOGGER = LogManager.getLogger("SodiumLegacy-CTM");

    /** A readable pack. Entry names are zip-style: "assets/minecraft/optifine/ctm/glass/0.png". */
    public interface Source extends AutoCloseable {
        List<String> list();

        byte[] read(String entry) throws IOException;

        String label();

        @Override
        void close();
    }

    private CtmPackReader() {
    }

    /** @return active packs ordered lowest to highest priority */
    public static List<Source> openActivePacks() {
        List<Source> result = new ArrayList<>();

        try {
            Object manager = CtmCompat.resourceManager();

            for (Object pack : collectPacks(manager)) {
                File file = findFile(pack);

                if (file == null || !file.exists()) {
                    continue;
                }

                Source source = file.isDirectory() ? new FolderSource(file) : openZip(file);

                if (source != null) {
                    result.add(source);
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Could not enumerate resource packs for connected textures", t);
        }

        return result;
    }

    private static List<Object> collectPacks(Object manager) throws IllegalAccessException {
        List<Object> packs = new ArrayList<>();

        if (manager == null) {
            return packs;
        }

        for (Field mapField : fieldsOfType(manager.getClass(), Map.class)) {
            Map<?, ?> domains = (Map<?, ?>) mapField.get(manager);

            if (domains == null) {
                continue;
            }

            Object fallback = domains.get("minecraft");

            if (fallback == null) {
                continue;
            }

            for (Field listField : fieldsOfType(fallback.getClass(), List.class)) {
                List<?> list = (List<?>) listField.get(fallback);

                if (list != null) {
                    packs.addAll(list);
                }
            }
        }

        return packs;
    }

    private static File findFile(Object pack) throws IllegalAccessException {
        for (Field f : fieldsOfType(pack.getClass(), File.class)) {
            File file = (File) f.get(pack);

            if (file != null) {
                return file;
            }
        }

        return null;
    }

    private static List<Field> fieldsOfType(Class<?> type, Class<?> wanted) {
        List<Field> fields = new ArrayList<>();

        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && wanted.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    fields.add(f);
                }
            }
        }

        return fields;
    }

    private static Source openZip(File file) {
        try {
            return new ZipSource(file);
        } catch (IOException e) {
            LOGGER.warn("Could not open resource pack {}", file, e);
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;

        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }

        return out.toByteArray();
    }

    private static final class ZipSource implements Source {
        private final File file;
        private final ZipFile zip;

        ZipSource(File file) throws IOException {
            this.file = file;
            this.zip = new ZipFile(file);
        }

        @Override
        public List<String> list() {
            List<String> names = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = this.zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();

                if (!e.isDirectory()) {
                    names.add(e.getName());
                }
            }

            return names;
        }

        @Override
        public byte[] read(String entry) throws IOException {
            ZipEntry e = this.zip.getEntry(entry);

            if (e == null) {
                return null;
            }

            try (InputStream in = this.zip.getInputStream(e)) {
                return readAll(in);
            }
        }

        @Override
        public String label() {
            return this.file.getName();
        }

        @Override
        public void close() {
            try {
                this.zip.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static final class FolderSource implements Source {
        private final File root;

        FolderSource(File root) {
            this.root = root;
        }

        @Override
        public List<String> list() {
            List<String> names = new ArrayList<>();
            File assets = new File(this.root, "assets");

            if (assets.isDirectory()) {
                walk(assets, "assets", names);
            }

            return names;
        }

        private void walk(File dir, String prefix, Collection<String> out) {
            File[] children = dir.listFiles();

            if (children == null) {
                return;
            }

            for (File child : children) {
                String name = prefix + "/" + child.getName();

                if (child.isDirectory()) {
                    walk(child, name, out);
                } else {
                    out.add(name);
                }
            }
        }

        @Override
        public byte[] read(String entry) throws IOException {
            File f = new File(this.root, entry);

            return f.isFile() ? Files.readAllBytes(f.toPath()) : null;
        }

        @Override
        public String label() {
            return this.root.getName();
        }

        @Override
        public void close() {
        }
    }

    public static List<String> sorted(List<String> names) {
        List<String> copy = new ArrayList<>(names);
        Collections.sort(copy);
        return copy;
    }
}
