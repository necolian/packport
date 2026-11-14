package net.necohub.packport.pack;

import java.nio.file.Path;
import java.util.List;

public record ResourcePackInfo(
        String id,
        Path path,
        String name,
        String description,
        String uuid,
        List<Integer> version,
        String category,
        long sizeBytes
) {
    public ResourcePackInfo {
        version = List.copyOf(version);
    }

    public String formattedSize() {
        double size = sizeBytes;

        if (size < 1024) {
            return sizeBytes + " B";
        }

        size /= 1024;
        if (size < 1024) {
            return "%.2f KiB".formatted(size);
        }

        size /= 1024;
        return "%.2f MiB".formatted(size);
    }

    public String formattedVersion() {
        return String.join(".", version.stream()
                .map(String::valueOf)
                .toList());
    }

}