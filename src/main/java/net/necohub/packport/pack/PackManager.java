
package net.necohub.packport.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.geysermc.geyser.api.extension.Extension;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipFile;

public final class PackManager {

    private final Path packsDirectory;
    private final Extension extension;

    private volatile List<ResourcePackInfo> packs = List.of();

    public PackManager(Extension extension, Path dataFolder) {
        this.extension = extension;
        this.packsDirectory = dataFolder.resolve("packs");
    }

    public synchronized void reload() throws IOException {
        Files.createDirectories(packsDirectory);

        extension.logger().info(
                "[PackPort] Packs directory: "
                        + packsDirectory.toAbsolutePath()
        );

        List<ResourcePackInfo> discovered = new ArrayList<>();

        try (var paths = Files.walk(packsDirectory)) {
            List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .filter(PackManager::isPackFile)
                    .sorted()
                    .toList();

            extension.logger().info(
                    "[PackPort] Pack files found: " + files.size()
            );

            for (Path path : files) {
                try {
                    ResourcePackInfo info = readPack(path);
                    discovered.add(info);

                    extension.logger().info(
                            "[PackPort] Loaded: " + info.id()
                                    + " (" + info.name() + ")"
                    );
                } catch (Exception e) {
                    extension.logger().error(
                            "[PackPort] Failed to load "
                                    + path.toAbsolutePath()
                                    + ": " + e.getMessage(),
                            e
                    );
                }
            }
        }

        discovered.sort(
                Comparator.comparing(
                                ResourcePackInfo::category,
                                Comparator.nullsFirst(
                                        String.CASE_INSENSITIVE_ORDER
                                )
                        )
                        .thenComparing(
                                ResourcePackInfo::name,
                                String.CASE_INSENSITIVE_ORDER
                        )
        );

        packs = List.copyOf(discovered);

        extension.logger().info(
                "[PackPort] Total packs loaded: " + packs.size()
        );
    }

    private ResourcePackInfo readPack(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var entry = zip.getEntry("manifest.json");

            if (entry == null || entry.isDirectory()) {
                List<? extends java.util.zip.ZipEntry> manifests =
                        zip.stream()
                                .filter(e -> !e.isDirectory())
                                .filter(e -> e.getName().endsWith("/manifest.json"))
                                .toList();

                if (manifests.size() != 1) {
                    throw new IOException(
                            "Expected one manifest.json, found "
                                    + manifests.size()
                    );
                }

                String manifestPath = manifests.getFirst().getName();

                String prefix = manifestPath.substring(
                        0,
                        manifestPath.length() - "manifest.json".length()
                );

                entry = zip.getEntry(manifestPath);

                extension.logger().info(
                        "[PackPort] Detected nested pack root: " + prefix
                );
            }

            JsonObject root;

            try (InputStream input = zip.getInputStream(entry);
                 InputStreamReader inputReader =
                         new InputStreamReader(
                                 input,
                                 StandardCharsets.UTF_8
                         );
                 JsonReader reader = new JsonReader(inputReader)) {

                // Allow // and /* */ comments in manifest.json.
                reader.setStrictness(
                        com.google.gson.Strictness.LENIENT
                );

                JsonElement parsed = JsonParser.parseReader(reader);

                if (!parsed.isJsonObject()) {
                    throw new IOException(
                            "manifest.json root is not an object"
                    );
                }

                root = parsed.getAsJsonObject();
            } catch (RuntimeException e) {
                throw new IOException(
                        "Failed to parse manifest.json: "
                                + e.getMessage(),
                        e
                );
            }

            JsonElement headerElement = root.get("header");

            if (headerElement == null
                    || !headerElement.isJsonObject()) {
                throw new IOException(
                        "manifest.json has no valid header object"
                );
            }

            JsonObject header = headerElement.getAsJsonObject();

            String name = readString(header, "name");
            String description = readString(header, "description");
            String uuid = readString(header, "uuid");

            try {
                uuid = UUID.fromString(uuid).toString();
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid header UUID: " + uuid, e);
            }

            JsonElement versionElement = header.get("version");

            List<Integer> version = getVersion(versionElement);

            Path relativePath = packsDirectory.relativize(path);
            Path parent = relativePath.getParent();

            String category = parent == null
                    ? ""
                    : parent.toString().replace('\\', '/');

            String id = relativePath.toString().replace('\\', '/');

            return new ResourcePackInfo(
                    id,
                    path.toAbsolutePath().normalize(),
                    name,
                    description,
                    uuid,
                    version,
                    category,
                    Files.size(path)
            );
        }
    }

    private static @NonNull List<Integer> getVersion(JsonElement versionElement) throws IOException {
        if (versionElement == null
                || !versionElement.isJsonArray()) {
            throw new IOException(
                    "header.version must be an array"
            );
        }

        JsonArray rawVersion = versionElement.getAsJsonArray();

        if (rawVersion.size() != 3) {
            throw new IOException(
                    "header.version must contain 3 numbers"
            );
        }

        List<Integer> version = new ArrayList<>();

        for (JsonElement part : rawVersion) {
            if (!part.isJsonPrimitive()
                    || !part.getAsJsonPrimitive().isNumber()) {
                throw new IOException(
                        "header.version contains a non-number"
                );
            }

            double number = part.getAsDouble();

            if (!Double.isFinite(number)
                    || number < 0
                    || number != Math.floor(number)
                    || number > Integer.MAX_VALUE) {
                throw new IOException(
                        "Invalid version component: " + part
                );
            }

            version.add((int) number);
        }
        return version;
    }

    private static String readString(
            JsonObject object,
            String key
    ) throws IOException {
        JsonElement element = object.get(key);

        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new IOException(
                    "Missing or invalid manifest field: " + key
            );
        }

        String value = element.getAsString();

        if (value.isBlank()) {
            throw new IOException(
                    "Manifest field is blank: " + key
            );
        }

        return value;
    }

    private static boolean isPackFile(Path path) {
        String name = path.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        return name.endsWith(".mcpack")
                || name.endsWith(".zip");
    }

    public List<ResourcePackInfo> getPacks() {
        return packs;
    }

}
