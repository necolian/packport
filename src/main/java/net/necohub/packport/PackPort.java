package net.necohub.packport;

import net.necohub.packport.preferences.Config;
import net.necohub.packport.preferences.Lang;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPreInitializeEvent;
import org.geysermc.geyser.api.extension.Extension;

/**
 * The main class of your extension - must implement extension, and be in the extension.yml file.
 * See {@link Extension} for available methods - for example to get the path to the configuration folder.
 */
public class PackPort implements Extension {

    @Subscribe
    public void onPreInitialize(GeyserPreInitializeEvent event) {
        Config config = new Config(this);
        config.load();

        Lang.load(this, config);
        this.logger().info(Lang.get("packport.loading",this.description().name()));
    }

    @Subscribe
    public void onPostInitialize(GeyserPostInitializeEvent event) {
        this.logger().info(Lang.get("packport.loaded",this.description().name()));
    }

}
