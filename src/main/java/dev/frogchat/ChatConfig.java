package dev.frogchat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The mod's settings, as a hand-editable {@code config/frogchat.json}.
 *
 * <p>A file rather than a settings screen on purpose: this is three toggles and a colour list, and a
 * screen for that would be most of the mod. The file is written back on load with any keys a newer
 * version added, so an old config picks up new defaults instead of silently missing them.
 */
public final class ChatConfig {

    /** Give each sender their own colour, and restyle {@code <name> body} as {@code name: body}. */
    public boolean nameColours = true;

    /** The sender's face beside their line. */
    public boolean heads = true;

    /** Pointing at a line while chat is open shows when it arrived. */
    public boolean hoverTimestamps = true;

    /**
     * Colours chosen by hand, beating the one derived from the name.
     *
     * <p>Keyed by player name, valued as hex — {@code "Spoop": "FF55FF"}, with or without a leading
     * {@code #}. Matched without regard to case, because the point is to type a name, not to reproduce
     * one. Anybody not listed keeps the colour their name hashes to.
     */
    public Map<String, String> colourOverrides = new LinkedHashMap<>();

    // ---------------------------------------------------------------- lookup

    /** The hand-picked colour for {@code name}, or null to let the hash decide. */
    public Integer overrideFor(String name) {
        if (colourOverrides == null || colourOverrides.isEmpty()) return null;

        for (Map.Entry<String, String> entry : colourOverrides.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) return parseHex(entry.getValue());
        }
        return null;
    }

    /** A hex colour, or null if the string is not one — a typo should not crash a chat line. */
    private static Integer parseHex(String value) {
        if (value == null) return null;
        try {
            return (int) (Long.parseLong(value.replace("#", "").trim(), 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- persistence

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FrogChat.ID + ".json");
    }

    static ChatConfig load() {
        Path path = file();
        ChatConfig loaded = new ChatConfig();

        if (Files.exists(path)) {
            try {
                ChatConfig parsed = GSON.fromJson(Files.readString(path), ChatConfig.class);
                if (parsed != null) loaded = parsed;
            } catch (IOException | RuntimeException e) {
                // A broken config should not stop the mod loading; defaults are a fine fallback and the
                // rewrite below repairs the file rather than leaving the player to hand-fix JSON.
                FrogChat.LOG.warn("config unreadable, using defaults", e);
            }
        }

        if (loaded.colourOverrides == null) loaded.colourOverrides = new LinkedHashMap<>();
        loaded.warnAboutBadColours();
        loaded.save();
        return loaded;
    }

    /**
     * Says so in the log when an override will not be used.
     *
     * <p>A mistyped colour is otherwise perfectly silent — the name simply keeps its hashed colour,
     * which looks exactly like the override not being read at all.
     */
    private void warnAboutBadColours() {
        for (Map.Entry<String, String> entry : colourOverrides.entrySet()) {
            if (parseHex(entry.getValue()) == null) {
                FrogChat.LOG.warn("colour override for '{}' is not hex: '{}' — ignoring it",
                        entry.getKey(), entry.getValue());
            }
        }
    }

    private void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this));
        } catch (IOException e) {
            FrogChat.LOG.warn("could not write config", e);
        }
    }

    /** How many overrides are actually usable, for the startup line. */
    int usableOverrides() {
        int n = 0;
        for (String value : colourOverrides.values()) {
            if (parseHex(value) != null) n++;
        }
        return n;
    }
}
