package net.necohub.packport.player;

import net.necohub.packport.pack.PackManager;
import net.necohub.packport.pack.ResourcePackInfo;
import net.necohub.packport.preferences.Lang;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.session.GeyserSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PackSelectionGui {

    private final Extension extension;
    private final PackManager packManager;
    private final PlayerPackStorage storage;

    public PackSelectionGui(
            Extension extension,
            PackManager packManager,
            PlayerPackStorage storage
    ) {
        this.extension = extension;
        this.packManager = packManager;
        this.storage = storage;
    }

    /**
     * connectionId: フォーム表示・再接続に使用する Java UUID
     * storageId: プレイヤー設定の保存・読み込みに使用する XUID
     */
    public void open(UUID connectionId, String storageId) {
        if (connectionId == null) {
            extension.logger().warning(
                    "Cannot open PackPort GUI: Java UUID is unavailable."
            );
            return;
        }

        if (storageId == null || !storageId.matches("[0-9]+")) {
            extension.logger().warning(
                    "Cannot open PackPort GUI: XUID is unavailable."
            );

            var connection = GeyserApi.api()
                    .connectionByUuid(connectionId);

            if (connection != null) {
                connection.sendMessage(
                        Lang.get("packport.gui.load-failed")
                );
            }
            return;
        }

        Set<String> selected;

        try {
            selected = new HashSet<>(storage.load(storageId));
        } catch (IOException e) {
            extension.logger().error(
                    Lang.get("packport.gui.load-failed"), e
            );

            var connection = GeyserApi.api()
                    .connectionByUuid(connectionId);

            if (connection != null) {
                connection.sendMessage(
                        Lang.get("packport.gui.load-failed")
                );
            }
            return;
        }

        showBrowser(connectionId, storageId, "", selected);
    }

    /**
     * ファイルマネージャー形式の一覧を表示する。
     * currentDirectory は packs/ からの相対パス。
     */
    private void showBrowser(
            UUID connectionId,
            String storageId,
            String currentDirectory,
            Set<String> selected
    ) {
        List<ResourcePackInfo> packs = packManager.getPacks();

        Set<String> directories = new LinkedHashSet<>();
        List<ResourcePackInfo> files = new ArrayList<>();

        String prefix = currentDirectory.isEmpty()
                ? ""
                : currentDirectory + "/";

        for (ResourcePackInfo pack : packs) {
            String id = pack.id().replace('\\', '/');

            if (!currentDirectory.isEmpty()
                    && !id.startsWith(prefix)) {
                continue;
            }

            String remainder = currentDirectory.isEmpty()
                    ? id
                    : id.substring(prefix.length());

            if (remainder.isEmpty()) {
                continue;
            }

            int slash = remainder.indexOf('/');

            if (slash >= 0) {
                String directoryName = remainder.substring(0, slash);
                directories.add(directoryName);
            } else if (isPackFile(remainder)) {
                files.add(pack);
            }
        }

        List<BrowserEntry> entries = new ArrayList<>();

        directories.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(directoryName -> {
                    String path = currentDirectory.isEmpty()
                            ? directoryName
                            : currentDirectory + "/" + directoryName;

                    entries.add(new BrowserEntry(
                            directoryName,
                            path,
                            null
                    ));
                });

        files.sort(
                Comparator.comparing(
                        ResourcePackInfo::name,
                        String.CASE_INSENSITIVE_ORDER
                )
        );

        for (ResourcePackInfo pack : files) {
            entries.add(new BrowserEntry(
                    pack.name(),
                    null,
                    pack
            ));
        }

        String location = currentDirectory.isEmpty()
                ? "packs/"
                : "packs/" + currentDirectory + "/";

        SimpleForm.Builder form = SimpleForm.builder()
                .title(Lang.get("packport.gui.title"))
                .content(
                        Lang.get("packport.gui.instructions")
                                + "\n\n"
                                + "§7" + location
                );

        int buttonIndex = 0;

        // 親フォルダへ戻るボタン
        if (!currentDirectory.isEmpty()) {
            form.button("§e..");
            buttonIndex++;
        }

        // フォルダとパックを表示
        for (BrowserEntry entry : entries) {
            if (entry.pack() == null) {
                form.button("§b[DIR] §r" + entry.name());
            } else {
                String prefixMark = selected.contains(entry.pack().id())
                        ? "§a✓ §r"
                        : "§7○ §r";

                form.button(prefixMark + entry.name());
            }

            buttonIndex++;
        }

        // 適用ボタンは常に最後
        int applyButtonIndex = buttonIndex;
        form.button(Lang.get("packport.gui.apply"));

        form.validResultHandler(response -> {
            int button = response.clickedButtonId();

            int entryOffset = 0;

            if (!currentDirectory.isEmpty()) {
                if (button == 0) {
                    int lastSlash = currentDirectory.lastIndexOf('/');

                    String parentDirectory = lastSlash < 0
                            ? ""
                            : currentDirectory.substring(0, lastSlash);

                    showBrowser(
                            connectionId,
                            storageId,
                            parentDirectory,
                            selected
                    );
                    return;
                }

                entryOffset = 1;
            }

            if (button == applyButtonIndex) {
                showConfirmation(
                        connectionId,
                        storageId,
                        currentDirectory,
                        selected
                );
                return;
            }

            int entryIndex = button - entryOffset;

            if (entryIndex >= entries.size()) {
                return;
            }

            BrowserEntry entry = entries.get(entryIndex);

            if (entry.pack() == null) {
                showBrowser(
                        connectionId,
                        storageId,
                        entry.directory(),
                        selected
                );
            } else {
                showDetails(
                        connectionId,
                        storageId,
                        currentDirectory,
                        selected,
                        entry.pack()
                );
            }
        });

        GeyserApi.api().sendForm(connectionId, form.build());
    }

    private boolean isPackFile(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);

        return lower.endsWith(".mcpack")
                || lower.endsWith(".zip");
    }

    /**
     * パックの詳細画面。
     */
    private void showDetails(
            UUID connectionId,
            String storageId,
            String currentDirectory,
            Set<String> selected,
            ResourcePackInfo pack
    ) {
        String description = pack.description() == null
                ? ""
                : pack.description();

        boolean isSelected = selected.contains(pack.id());

        String content = pack.name()
                + "\n\n"
                + description
                + "\n\n"
                + Lang.get(
                "packport.gui.pack-info",
                pack.formattedVersion(),
                pack.formattedSize()
        )
                + "\n\n"
                + Lang.get(
                isSelected
                        ? "packport.gui.selected"
                        : "packport.gui.not-selected"
        );

        SimpleForm form = SimpleForm.builder()
                .title(pack.name())
                .content(content)
                .button(
                        Lang.get(
                                isSelected
                                        ? "packport.gui.deselect-pack"
                                        : "packport.gui.select-pack"
                        )
                )
                .button(Lang.get("packport.gui.back"))
                .validResultHandler(response -> {
                    int button = response.clickedButtonId();

                    if (button == 0) {
                        if (isSelected) {
                            selected.remove(pack.id());
                        } else {
                            selected.add(pack.id());
                        }

                        showBrowser(
                                connectionId,
                                storageId,
                                currentDirectory,
                                selected
                        );
                    } else if (button == 1) {
                        showBrowser(
                                connectionId,
                                storageId,
                                currentDirectory,
                                selected
                        );
                    }
                })
                .build();

        GeyserApi.api().sendForm(connectionId, form);
    }

    /**
     * 選択内容を確認してから保存する。
     */
    private void showConfirmation(
            UUID connectionId,
            String storageId,
            String currentDirectory,
            Set<String> selected
    ) {
        ModalForm form = ModalForm.builder()
                .title(Lang.get("packport.gui.confirm-title"))
                .content(
                        Lang.get(
                                "packport.gui.confirm-content",
                                selected.size()
                        )
                )
                .button1(Lang.get("packport.gui.confirm"))
                .button2(Lang.get("packport.gui.cancel"))
                .validResultHandler(response -> {
                    if (response.clickedButtonId() == 0) {
                        saveSelection(
                                connectionId,
                                storageId,
                                selected
                        );
                    } else {
                        showBrowser(
                                connectionId,
                                storageId,
                                currentDirectory,
                                selected
                        );
                    }
                })
                .build();

        GeyserApi.api().sendForm(connectionId, form);
    }

    /**
     * 確定した選択内容を保存する。
     */
    private void saveSelection(
            UUID connectionId,
            String storageId,
            Set<String> selected
    ) {
        List<String> newSelection = new ArrayList<>(selected);

        try {
            storage.save(storageId, newSelection);

            var connection = GeyserApi.api()
                    .connectionByUuid(connectionId);

            if (connection instanceof GeyserSession session) {
                session.transfer(
                        session.joinAddress(),
                        session.joinPort()
                );
            }
        } catch (IOException e) {
            extension.logger().error(
                    Lang.get("packport.gui.save-failed"), e
            );

            var connection = GeyserApi.api()
                    .connectionByUuid(connectionId);

            if (connection != null) {
                connection.sendMessage(
                        Lang.get("packport.gui.save-failed")
                );
            }
        }
    }

    /**
     * フォルダ、またはパックを表す一覧項目。
     */
    private record BrowserEntry(
            String name,
            String directory,
            ResourcePackInfo pack
    ) {
    }
}