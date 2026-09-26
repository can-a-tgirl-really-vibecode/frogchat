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
 * <p>The file is the source of truth; the Cloth Config screen writes back here on Done. The file is
 * written back on load with any keys a newer version added, so an old config picks up new defaults
 * instead of silently missing them.
 */
public final class ChatConfig {

    /** Give each sender their own colour. Vanilla's {@code <name> body} stays as it is. */
    public boolean nameColours = true;

    /**
     * Restyle player chat as {@code name: body}, with the punctuation and message hushed. Off keeps
     * vanilla's {@code <name> body}. Independent of {@link #nameColours} — the two combine, but
     * neither needs the other.
     */
    public boolean restyleNames = false;

    /** Where automatic name colours come from. */
    public NameColourSource nameColourSource = NameColourSource.HASH;

    /** Where automatic name colours come from. */
    public enum NameColourSource {
        /** Hashed from the name — the pastel every client agrees on. */
        HASH,
        /**
         * Copied from the locator bar, so a name matches its dot. Players with no dot — too far
         * away, or locator bar off — get the colour their dot would have, and anybody not on the
         * server at all keeps the hashed colour.
         */
        LOCATOR
    }

    /**
     * Pin every automatic name colour to the pastel — saturation and value fixed, only the hue
     * varies — so no name can end up unreadable, whatever the source. Off means full-strength
     * colours: vivid hashes in {@link NameColourSource#HASH HASH} mode, the dot exactly as the bar
     * shows it in {@link NameColourSource#LOCATOR LOCATOR} mode.
     */
    public boolean pastelColours = true;

    /** The sender's face beside their line. */
    public boolean heads = true;

    /** Pointing at a line while chat is open shows when it arrived. */
    public boolean hoverTimestamps = true;

    /** Render {@code :shortcode:} emojis inline, and offer them in tab completion. */
    public boolean emoji = true;

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
                // A broken config should not stop the mod loading, but one unparseable value (a typo'd
                // enum, a stray quote) must not cost the player everything else in the file: the
                // rewrite below would otherwise bury their settings under factory defaults. Keep the
                // original alongside instead, so it can be hand-fixed at leisure.
                FrogChat.LOG.warn("config unreadable, using defaults", e);
                try {
                    Files.move(path, path.resolveSibling(path.getFileName() + ".broken"));
                } catch (IOException moveFailed) {
                    FrogChat.LOG.warn("could not set the broken config aside; leaving it untouched", moveFailed);
                }
            }
        }

        if (loaded.colourOverrides == null) loaded.colourOverrides = new LinkedHashMap<>();
        if (loaded.nameColourSource == null) loaded.nameColourSource = NameColourSource.HASH;
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

    /** Writes the current values back to {@code config/frogchat.json}. Public for the config screen. */
    public void save() {
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
