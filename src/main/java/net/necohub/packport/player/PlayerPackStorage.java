package net.necohub.packport.player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.geysermc.geyser.api.extension.Extension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class PlayerPackStorage {

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    private final Path directory;

    public PlayerPackStorage(Extension extension) {
        this.directory = extension.dataFolder().resolve("players");
    }

    public List<String> load(String playerId) throws IOException {
        Path file = getFile(playerId);

        if (!Files.exists(file)) {
            return List.of();
        }

        try (var reader = Files.newBufferedReader(
                file, StandardCharsets.UTF_8)) {

            PlayerData data = GSON.fromJson(reader, PlayerData.class);

            if (data == null || data.selectedPacks == null) {
                return List.of();
            }

            return data.selectedPacks.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .distinct()
                    .toList();

        } catch (JsonParseException e) {
            throw new IOException("Invalid player data: " + file, e);
        }
    }

    public void save(
            String playerId,
            List<String> selectedPacks
    ) throws IOException {

        Files.createDirectories(directory);

        PlayerData data = new PlayerData();
        data.selectedPacks = selectedPacks.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();

        Path file = getFile(playerId);
        Path temporaryFile = Files.createTempFile(
                directory, playerId, ".tmp"
        );

        try {
            try (var writer = Files.newBufferedWriter(
                    temporaryFile, StandardCharsets.UTF_8)) {

                GSON.toJson(data, writer);
            }

            try {
                Files.move(
                        temporaryFile,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (IOException e) {
                Files.move(
                        temporaryFile,
                        file,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private Path getFile(String playerId) {
        if (!playerId.matches("[0-9]+")) {
            throw new IllegalArgumentException("Invalid player XUID");
        }

        return directory.resolve(playerId + ".json");
    }

    private static final class PlayerData {
        private List<String> selectedPacks = new ArrayList<>();
    }
}