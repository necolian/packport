
package net.necohub.packport.preferences;

import org.geysermc.geyser.api.extension.Extension;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Lang {

    private static final Properties messages = new Properties();

    private Lang() {
    }

    public static void load(Extension extension, Config config) {
        String language = config.getString("language", "en_US");

        if (!language.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException(
                    "Invalid language name: " + language
            );
        }

        Path langFolder = extension.dataFolder().resolve("lang");
        Path langFile = langFolder.resolve(language + ".lang");
        String resource = "lang/" + language + ".lang";

        try {
            Files.createDirectories(langFolder);

            if (Files.notExists(langFile)) {
                try (InputStream input =
                             Lang.class.getClassLoader()
                                     .getResourceAsStream(resource)) {

                    if (input == null) {
                        throw new IOException(
                                "Language resource not found: " + resource
                        );
                    }

                    Files.copy(input, langFile);
                }
            }

            Properties loaded = new Properties();

            try (InputStreamReader reader = new InputStreamReader(
                    Files.newInputStream(langFile),
                    StandardCharsets.UTF_8
            )) {
                loaded.load(reader);
            }

            messages.clear();
            messages.putAll(loaded);

            extension.logger().info(
                    "Loaded language: " + language
            );

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load language file: " + langFile, e
            );
        }
    }

    public static String get(String key) {
        return messages.getProperty(key, key);
    }

    public static String get(String key, Object... args) {
        return get(key).formatted(args);
    }
}
