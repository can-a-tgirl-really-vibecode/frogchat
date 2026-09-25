package dev.frogchat;

import net.fabricmc.api.ClientModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint. There is no tick loop and no event wiring — every one of the four features runs from a
 * Mixin on {@link net.minecraft.client.gui.components.ChatComponent}, so all this does is read the
 * config once before the first chat line can arrive.
 */
public final class FrogChat implements ClientModInitializer {

    public static final String ID = "frogchat";
    public static final Logger LOG = LoggerFactory.getLogger("FrogChat");

    private static ChatConfig config = new ChatConfig();

    /** Live settings. Never null — a config that fails to load leaves the defaults in place. */
    public static ChatConfig config() {
        return config;
    }

    @Override
    public void onInitializeClient() {
        config = ChatConfig.load();
        LOG.info("FrogChat ready — colours={} heads={} hover={} overrides={}",
                config.nameColours, config.heads, config.hoverTimestamps, config.usableOverrides());
    }
}
