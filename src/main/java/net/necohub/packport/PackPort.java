package net.necohub.packport;

import net.necohub.packport.pack.PackManager;
import net.necohub.packport.pack.ResourcePackInfo;
import net.necohub.packport.player.PackSelectionGui;
import net.necohub.packport.player.PlayerPackStorage;
import net.necohub.packport.preferences.Config;
import net.necohub.packport.preferences.Lang;

import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.command.Command;
import org.geysermc.geyser.api.command.CommandSource;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCommandsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPreInitializeEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.event.bedrock.SessionLoadResourcePacksEvent;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.pack.option.ResourcePackOption;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PackPort implements Extension {

    private PackManager packManager;
    private PlayerPackStorage playerPackStorage;
    private PackSelectionGui packSelectionGui;
    private Config config;

    @Subscribe
    public void onPreInitialize(GeyserPreInitializeEvent event) {
        config = new Config(this);
        config.load();

        Lang.load(this, config);

        logger().info(
                Lang.get("packport.loading", description().name())
        );

        packManager = new PackManager(this, dataFolder());
        playerPackStorage = new PlayerPackStorage(this);

        packSelectionGui = new PackSelectionGui(
                this,
                packManager,
                playerPackStorage
        );

        try {
            packManager.reload();

            logger().info(
                    Lang.get(
                            "packport.packs-found",
                            packManager.getPacks().size()
                    )
            );
        } catch (IOException e) {
            logger().error(
                    Lang.get("packport.pack-scan-failed"), e
            );
        }
    }

    @Subscribe
    public void onPostInitialize(GeyserPostInitializeEvent event) {
        logger().info(
                Lang.get("packport.loaded", description().name())
        );
    }

    public PackManager getPackManager() {
        return packManager;
    }

    @Subscribe
    public void onDefineCommands(GeyserDefineCommandsEvent event) {
        Command reloadCommand = Command.builder(this)
                .name("reload")
                .description("Reload PackPort resource packs")
                .source(CommandSource.class)
                .permission("packport.reload")
                .executor((source, cmd, args) -> {
                    try {
                        packManager.reload();

                        source.sendMessage(
                                Lang.get(
                                        "packport.reload-success",
                                        packManager.getPacks().size()
                                )
                        );
                    } catch (IOException e) {
                        logger().error(
                                Lang.get("packport.pack-scan-failed"), e
                        );

                        source.sendMessage(
                                Lang.get("packport.reload-failed")
                        );
                    }
                })
                .build();

        event.register(reloadCommand);

        Command rpCommand = Command.builder(this)
                .name("rp")
                .description(
                        Lang.get("packport.command-rp-description")
                )
                .source(CommandSource.class)
                .permission("packport.rp")
                .playerOnly(true)
                .bedrockOnly(true)
                .executor((source, registeredCommand, args) -> {
                    var connection = source.connection();

                    if (connection == null) {
                        source.sendMessage(
                                Lang.get("packport.gui.bedrock-only")
                        );
                        return;
                    }

                    var connectionId = connection.javaUuid();
                    var storageId = connection.xuid();

                    if (connectionId == null) {
                        logger().warning(
                                "Cannot open PackPort GUI: Java UUID is unavailable."
                        );
                        source.sendMessage(
                                Lang.get("packport.gui.load-failed")
                        );
                        return;
                    }

                    if (!storageId.matches("[0-9]+")) {
                        logger().warning(
                                "Cannot open PackPort GUI: XUID is invalid."
                        );
                        source.sendMessage(
                                Lang.get("packport.gui.load-failed")
                        );
                        return;
                    }

                    packSelectionGui.open(connectionId, storageId);
                })
                .build();

        event.register(rpCommand);
    }

    @Subscribe
    public void onSessionLoadResourcePacks(
            SessionLoadResourcePacksEvent event
    ) {
        var connection = event.connection();
        String playerId = event.connection().xuid();

        if (!playerId.matches("[0-9]+")) {
            logger().warning(
                    "Skipping player resource packs: XUID is invalid."
            );
            return;
        }

        List<String> selectedPackIds;

        try {
            selectedPackIds = playerPackStorage.load(playerId);
        } catch (IOException | IllegalArgumentException e) {
            logger().error(
                    Lang.get("packport.player-data-load-failed"), e
            );
            return;
        }

        // 管理者のデフォルトパックを先に登録対象へ追加
        Set<String> packIds = new LinkedHashSet<>();

        String defaultPacks = config.getString("default-packs", "");

        for (String id : defaultPacks.split(";")) {
            String trimmed = id.trim();

            if (!trimmed.isEmpty()) {
                packIds.add(trimmed);
            }
        }

        // プレイヤーが選択したパックを追加
        packIds.addAll(selectedPackIds);

        Map<String, ResourcePackInfo> packsById =
                packManager.getPacks().stream()
                        .collect(Collectors.toMap(
                                ResourcePackInfo::id,
                                Function.identity(),
                                (first, second) -> first
                        ));

        for (String id : packIds) {
            ResourcePackInfo packInfo = packsById.get(id);

            if (packInfo == null) {
                logger().warning(
                        Lang.get("packport.pack-not-found", id)
                );
                continue;
            }

            try {
                ResourcePack pack = ResourcePack.create(
                        PackCodec.path(packInfo.path())
                );

                event.register(pack, new ResourcePackOption[0]);

            } catch (RuntimeException e) {
                logger().error(
                        Lang.get("packport.pack-register-failed", id),
                        e
                );
            }
        }
    }
}