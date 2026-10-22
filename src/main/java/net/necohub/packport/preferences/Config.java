package net.necohub.packport.preferences;

import org.geysermc.geyser.api.extension.Extension;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class Config {

    private final Extension extension;
    private Map<String, Object> values = Map.of();

    public Config(Extension extension) {
        this.extension = extension;
    }

    public void load() {
        Path configFile =
                extension.dataFolder().resolve("config.yml");

        try {
            Files.createDirectories(configFile.getParent());

            // 初回のみデフォルト設定をコピー
            if (Files.notExists(configFile)) {
                try (InputStream input =
                             getClass().getClassLoader()
                                     .getResourceAsStream("config.yml")) {

                    if (input == null) {
                        throw new IOException(
                                "Default config.yml not found in JAR"
                        );
                    }

                    Files.copy(input, configFile);
                }
            }

            // YAMLを読み込む
            Yaml yaml = new Yaml(
                    new SafeConstructor(new LoaderOptions())
            );

            try (InputStreamReader reader = new InputStreamReader(
                    Files.newInputStream(configFile),
                    StandardCharsets.UTF_8
            )) {
                Object loaded = yaml.load(reader);

                if (loaded == null) {
                    values = Map.of();
                } else if (loaded instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result =
                            (Map<String, Object>) map;
                    values = Map.copyOf(result);
                } else {
                    throw new IOException(
                            "config.yml must contain a YAML mapping"
                    );
                }
            }

        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    "Failed to load config.yml", e
            );
        }
    }

    public String getString(String key, String defaultValue) {
        Object value = values.get(key);
        return value instanceof String s ? s : defaultValue;
    }
}