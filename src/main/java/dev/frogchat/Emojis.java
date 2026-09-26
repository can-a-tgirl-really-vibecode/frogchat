package dev.frogchat;

import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code :shortcode:} emojis, on vanilla's two existing mechanisms.
 *
 * <p><b>Rendering</b> is the object text component and an atlas sprite, the same way vanilla puts
 * player faces in text ({@code /fetchprofile}). The emoji are plain PNGs under
 * {@code assets/frogchat/textures/gui/sprites/emoji/} (Twemoji, CC-BY 4.0), which the vanilla GUI
 * atlas stitches in automatically, so {@code Component.object(new AtlasSprite(GUI, ...))} draws
 * them inline with no renderer of our own. The shortcode stays the fallback for flattened text —
 * narrators, copies, and clients without the mod all still read {@code :sob:}.
 *
 * <p><b>Completion</b> rides the non-command suggestion list, the same list connected players show
 * up in: {@link dev.frogchat.hook.EmojiSuggestionsHook} adds the shortcodes to it whenever the
 * word being typed starts with {@code :}.
 *
 * <p>The shortcode table itself lives in {@code assets/frogchat/emoji.txt}, generated from
 * emojibase's GitHub shortcodes (hexcode = Twemoji file name), one {@code name=codepoint} per
 * line.
 */
public final class Emojis {

    private static final Pattern SHORTCODE = Pattern.compile(":([a-z0-9_+-]+):");

    /** shortcode → texture file name (the Twemoji hexcode). */
    private static final Map<String, String> CODES = load();

    /** Every shortcode, colon-wrapped, in table (alphabetical) order. */
    private static final List<String> SHORTCODES =
            CODES.keySet().stream().map(name -> ":" + name + ":").toList();

    private static Map<String, String> load() {
        Map<String, String> codes = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Emojis.class.getResourceAsStream("/assets/" + FrogChat.ID + "/emoji.txt"), StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                int sep = line.indexOf('=');
                if (sep > 0) codes.put(line.substring(0, sep), line.substring(sep + 1));
            }
        } catch (Exception e) {
            FrogChat.LOG.warn("emoji table unreadable, emojis disabled", e);
        }
        return codes;
    }

    private Emojis() {}

    /** Every shortcode, colon-wrapped, alphabetical. */
    public static List<String> shortcodes() {
        return SHORTCODES;
    }

    /** The GUI-atlas sprite for a colon-wrapped shortcode like {@code :sob:}, or null. */
    public static @Nullable Identifier spriteFor(String wrapped) {
        if (wrapped.length() < 3 || wrapped.charAt(0) != ':' || wrapped.charAt(wrapped.length() - 1) != ':') return null;
        return sprite(wrapped.substring(1, wrapped.length() - 1));
    }

    private static @Nullable Identifier sprite(String shortcode) {
        String codepoint = CODES.get(shortcode);
        return codepoint == null ? null : Identifier.fromNamespaceAndPath(FrogChat.ID, "emoji/" + codepoint);
    }

    /**
     * The component with every {@code :shortcode:} literal swapped for its inline sprite, anywhere
     * in the tree — plain text, but also the arguments of the {@code chat.type.*} translatables
     * vanilla wraps messages in. Structure and styles are preserved, so hover/click events and the
     * sender metadata FrogChat reads out of the tree all survive.
     */
    public static Component replace(Component component) {
        ComponentContents contents = component.getContents();
        MutableComponent out;
        if (contents instanceof PlainTextContents plain) {
            out = replaceLiteral(plain.text());
        } else if (contents instanceof TranslatableContents t) {
            Object[] args = t.getArgs().clone();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component arg) args[i] = replace(arg);
            }
            out = Component.translatableWithFallback(t.getKey(), t.getFallback(), args);
        } else {
            out = component.plainCopy();
        }
        out.setStyle(component.getStyle());
        for (Component sibling : component.getSiblings()) {
            out.append(replace(sibling));
        }
        return out;
    }

    /** One literal with its known shortcodes replaced; unknown ones are left as typed. */
    private static MutableComponent replaceLiteral(String text) {
        Matcher matcher = SHORTCODE.matcher(text);
        MutableComponent out = Component.empty();
        int last = 0;
        while (matcher.find()) {
            Identifier sprite = sprite(matcher.group(1));
            if (sprite == null) continue;
            out.append(Component.literal(text.substring(last, matcher.start())));
            // The shortcode itself as the fallback, so flattened text still reads ":sob:".
            out.append(Component.object(new AtlasSprite(AtlasIds.GUI, sprite), Component.literal(matcher.group())));
            last = matcher.end();
        }
        if (last == 0) return Component.literal(text);
        return out.append(Component.literal(text.substring(last)));
    }
}
