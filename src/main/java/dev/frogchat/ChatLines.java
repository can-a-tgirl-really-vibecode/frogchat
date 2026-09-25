package dev.frogchat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What happens to chat lines, in two places.
 *
 * <p><b>Intake</b> ({@link #deliver}) runs once per message, from the hook on vanilla's single
 * private intake. 26.3 hands that intake a {@link GuiMessageSource} saying whether the line came
 * from a real player, and for player lines a <i>structured</i> component — the {@code chat.type.*}
 * translatable whose args are literally the sender's name and the body. So the sender is read out
 * of the structure rather than re-parsed out of flattened text, and the restyle/tint is applied to
 * the sender node itself, which keeps vanilla's own hover and click events (the entity tooltip and
 * the {@code /tell} suggestion) where a rebuilt-from-string line would lose them.
 *
 * <p><b>Render</b> ({@link #onLine}) runs inside vanilla's per-line draw call, with the consequences
 * that make that worth doing: the fade, the position and the mouse all arrive as vanilla worked them
 * out, so the face dims in step with its line and the hover test is a comparison, not a coordinate
 * conversion. Everything it needs beyond those arguments comes from {@link ChatMeta}, looked up by
 * the line's identity — no log scan, no regex, and nothing drawn for the queued-notice or
 * restricted-chat lines that pass through the same call with no log entry behind them.
 */
public final class ChatLines {

    /**
     * Vanilla's own {@code <name> body}, the shape player chat takes when it arrives as plain text
     * rather than as a {@code chat.type.*} translatable — plugin-relayed messages, and the config
     * screen's preview line.
     */
    private static final Pattern FROM_PLAYER =
            Pattern.compile("^<([A-Za-z0-9_]{1,16})>\\s(.*)$", Pattern.DOTALL);

    /** The restyle's punctuation between name and message, and message itself: quiet, so the name carries. */
    private static final int PUNCTUATION = 0x8A8A92;
    private static final int BODY = 0xE1E5EC;

    /** Face size, and the height of a glyph — the hover band is sized from the latter. */
    static final int FACE = 8;
    private static final int GLYPH = 9;

    private ChatLines() {}

    // ---------------------------------------------------------------- intake

    /**
     * One message on its way into the log: restyle/tint it if it came from a player, remember when
     * it arrived either way, and hand back the component the {@code GuiMessage} will be built from
     * — the meta is keyed by that very instance, which is what lets the render side find it again.
     * Called from the intake hook, nothing else.
     */
    public static Component deliver(Component message, GuiMessageSource source) {
        String sender = source == GuiMessageSource.PLAYER ? senderOf(message) : null;
        Component decorated = decorate(message, sender, FrogChat.config());
        ChatMeta.record(decorated, new ChatMeta.LineMeta(sender, System.currentTimeMillis()));
        return decorated;
    }

    /**
     * Tints one line as it will live in the chat log: the sender's name in their colour, and the
     * face's width reserved in leading spaces. With {@code restyleNames} the line is also rewritten
     * {@code <name> body} → {@code name: body}; without it the rest stays exactly as vanilla wrote
     * it. Anything not from a player passes through untouched.
     *
     * <p>Used by the config screen's live preview — the intake path resolves the sender itself, in
     * {@link #deliver} — so what the preview shows is what the mod does.
     */
    static Component decorate(Component message, ChatConfig cfg) {
        return decorate(message, senderOf(message), cfg);
    }

    /** As {@link #decorate(Component, ChatConfig)}, with the sender already resolved by the caller. */
    private static Component decorate(Component message, @Nullable String sender, ChatConfig cfg) {
        if (sender == null) return message;

        Component line = restyle(message, sender, cfg);

        // The face's width is reserved in leading spaces here at intake, not at render time: the
        // head is painted over this gap, and reserving on arrival means the gap survives re-wrapping.
        if (cfg.heads) {
            line = Component.literal(gapFor(FACE)).append(line);
        }
        return line;
    }

    /** Enough spaces to clear {@code px} pixels at the chat font's space width, plus one for breathing. */
    private static String gapFor(int px) {
        int space = Math.max(1, Minecraft.getInstance().font.width(" "));
        return " ".repeat(Math.max(1, (px + space - 1) / space) + 1);
    }

    /**
     * The sender's account name, or null if this is not player chat.
     *
     * <p>Reads the name out of the component's own structure where there is one. Servers can put a
     * team's prefix and suffix around the name, so the display string is resolved back to an online
     * account name (longest match wins, to prefer "Steven2" over "Steve") — the tint then hashes the
     * account name and stays stable regardless of team. While connected, a "sender" nobody online has
     * is no sender at all: vanilla's own chat-validation-error notice ships as a {@code chat.type.*}
     * line too, with boilerplate text where the name would be, and resolving to nobody is what keeps
     * it untouched.
     */
    private static @Nullable String senderOf(Component message) {
        if (message.getContents() instanceof TranslatableContents t && t.getArgs().length >= 2
                && t.getArgs()[0] instanceof Component name) {
            return accountName(name.getString());
        }
        Matcher fromPlayer = FROM_PLAYER.matcher(message.getString());
        return fromPlayer.matches() ? fromPlayer.group(1) : null;
    }

    /**
     * The online account name inside a possibly team-decorated display string, or null when nobody
     * online matches. Only while a server knows its players do we demand one: off any server — the
     * config screen's preview — the string is trusted as-is.
     */
    private static @Nullable String accountName(String decorated) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return decorated;

        String best = null;
        for (PlayerInfo player : mc.getConnection().getOnlinePlayers()) {
            String name = player.getProfile().name();
            if (name.equals(decorated)) return name;
            if (decorated.contains(name) && (best == null || name.length() > best.length())) best = name;
        }
        return best;
    }

    /**
     * The line with its sender tinted and, optionally, restyled.
     *
     * <p>Structured first: for a {@code chat.type.*} translatable the sender node is tinted and the
     * rest "repackaged" — same translation key, same body, click and hover events intact. The
     * string-rebuild fallback is for player chat that arrives as plain <code>&lt;name&gt; body</code>
     * text, where there is no structure to preserve.
     */
    private static Component restyle(Component message, String sender, ChatConfig cfg) {
        if (message.getContents() instanceof TranslatableContents t && t.getArgs().length >= 2
                && t.getArgs()[0] instanceof Component name && t.getArgs()[1] instanceof Component body) {
            if (cfg.restyleNames) {
                return Component.empty()
                        .append(tinted(name, sender, cfg))
                        .append(Component.literal(": ").withStyle(s -> s.withColor(PUNCTUATION)))
                        // A wrapper hushes the body to grey while leaving any styles inside it —
                        // vanilla's link colouring, say — to win where they set their own.
                        .append(Component.empty().withStyle(s -> s.withColor(BODY)).append(body));
            }
            if (cfg.nameColours) {
                Object[] args = t.getArgs().clone();
                args[0] = tinted(name, sender, cfg);
                return Component.translatable(t.getKey(), args);
            }
            return message;
        }

        Matcher fromPlayer = FROM_PLAYER.matcher(message.getString());
        if (!fromPlayer.matches()) return message;

        if (cfg.restyleNames) {
            MutableComponent name = Component.literal(fromPlayer.group(1));
            if (cfg.nameColours) name.withStyle(s -> s.withColor(NameTint.of(name.getString(), cfg)));
            return Component.empty()
                    .append(name)
                    .append(Component.literal(": ").withStyle(s -> s.withColor(PUNCTUATION)))
                    .append(Component.literal(fromPlayer.group(2)).withStyle(s -> s.withColor(BODY)));
        }
        if (cfg.nameColours) {
            String name = fromPlayer.group(1);
            return Component.literal("<")
                    .append(Component.literal(name).withStyle(s -> s.withColor(NameTint.of(name, cfg))))
                    .append(Component.literal("> " + fromPlayer.group(2)));
        }
        return message;
    }

    /** The given name node with the sender's colour, keeping whatever else it already said. */
    private static MutableComponent tinted(Component name, String sender, ChatConfig cfg) {
        MutableComponent tinted = name.copy();
        if (cfg.nameColours) tinted.withStyle(s -> s.withColor(NameTint.of(sender, cfg)));
        return tinted;
    }

    // ---------------------------------------------------------------- render

    /**
     * @param localMouse the cursor in line-local space, or null when chat is closed and there is no
     *                   meaningful cursor to test against
     * @param hoveredStyle the style vanilla found under the cursor on this line, or null — the
     *                     timestamp yields whenever it carries a hover event, because vanilla's own
     *                     tooltip (advancements, entity cards) refuses to replace one already set,
     *                     and setting ours first would swallow it
     */
    public static void onLine(GuiGraphicsExtractor g, Font font, int y, float alpha,
                              FormattedCharSequence content, @Nullable Vector2f localMouse,
                              int screenMouseX, int screenMouseY, @Nullable Style hoveredStyle) {
        ChatConfig cfg = FrogChat.config();
        if (!cfg.heads && !cfg.hoverTimestamps) return;

        ChatMeta.LineMeta meta = ChatMeta.metaFor(content);
        if (meta == null) return;

        if (cfg.heads && meta.sender() != null) drawFace(g, meta.sender(), y, alpha);

        boolean hoverTaken = hoveredStyle != null && hoveredStyle.getHoverEvent() != null;
        if (cfg.hoverTimestamps && localMouse != null && !hoverTaken && hovering(localMouse, y)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                            Component.literal(MessageClock.clockText(meta.arrivedMillis()))
                                    .withStyle(s -> s.withColor(0xE1E5EC)),
                            Component.literal(MessageClock.ageText(meta.arrivedMillis()))
                                    .withStyle(s -> s.withColor(0x8A8A92))),
                    screenMouseX, screenMouseY);
        }
    }

    /**
     * Whether the cursor is on this line.
     *
     * <p>The band is the glyph height plus a pixel either side rather than the full line height: with
     * generous line spacing the gap between lines belongs to neither of them, and pointing into it is
     * not pointing at a message. The width check repeats vanilla's own arithmetic
     * ({@code getWidth(option) / scale}) over the same public options, rather than reaching for the
     * chat pane's private copy of it.
     */
    private static boolean hovering(Vector2f localMouse, int y) {
        Minecraft mc = Minecraft.getInstance();
        int width = Mth.ceil(ChatComponent.getWidth(mc.options.chatWidth().get())
                / mc.options.chatScale().get().floatValue());
        return localMouse.x >= 0 && localMouse.x <= width
                && localMouse.y >= y - 1 && localMouse.y < y + GLYPH + 1;
    }

    /** Puts the sender's face in the gap their line reserved for it, at the line's own opacity. */
    private static void drawFace(GuiGraphicsExtractor g, String sender, int y, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        int a = Math.round(Math.clamp(alpha, 0f, 1f) * 255f);
        if (a <= 0) return;

        PlayerInfo player = mc.getConnection().getPlayerInfo(sender);
        if (player == null) return;

        PlayerFaceExtractor.extractRenderState(g, player.getSkin().body().texturePath(), 0, y, FACE,
                true, false, (a << 24) | 0xFFFFFF);
    }
}
