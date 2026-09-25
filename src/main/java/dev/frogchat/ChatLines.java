package dev.frogchat;

import dev.frogchat.hook.ChatInternals;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import org.joml.Vector2f;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What happens to one chat line as vanilla draws it: a face in the gap, and a timestamp if pointed at.
 *
 * <p>This runs from inside vanilla's own per-line call rather than from a renderer of ours, which is the
 * whole reason the mod no longer replaces the chat pane. The consequences are worth spelling out, because
 * they are the things a re-implementation always gets subtly wrong:
 *
 * <ul>
 *   <li>The <b>fade</b> is vanilla's. {@code alpha} arrives as an argument, already multiplied by the
 *       chat-opacity option, so a face dims out in step with the line it belongs to instead of hanging
 *       around at full strength on a line that has gone.</li>
 *   <li>The <b>position</b> is vanilla's. By this point the pose carries the chat scale and the message
 *       indent, so {@code x = 0} is the left edge of the text and {@code y} is the line's own y. Nothing
 *       here re-derives a layout that could drift from the real one.</li>
 *   <li>The <b>mouse</b> is vanilla's. The focused path hands over the cursor already inverted through
 *       that same pose, so the hover test is a comparison rather than a coordinate conversion.</li>
 * </ul>
 */
public final class ChatLines {

    /**
     * A sender at the head of a line, after the gap a head reserved.
     *
     * <p>Both shapes on purpose: {@code name:} is what the colouring rewrites lines into, and
     * {@code <name>} is what they still look like when colouring is switched off, so a head follows the
     * sender either way.
     */
    private static final Pattern SENDER = Pattern.compile("^ *<?([A-Za-z0-9_]{1,16})[:>]");

    /** Face size, and the height of a glyph — the hover band is sized from the latter. */
    private static final int FACE = 8;
    private static final int GLYPH = 9;

    private ChatLines() {}

    /**
     * @param localMouse the cursor in line-local space, or null when chat is closed and there is no
     *                   meaningful cursor to test against
     */
    public static void onLine(GuiGraphicsExtractor g, Font font, int y, float alpha,
                              FormattedCharSequence content, Vector2f localMouse,
                              int screenMouseX, int screenMouseY) {
        ChatConfig cfg = FrogChat.config();
        if (!cfg.heads && !cfg.hoverTimestamps) return;

        ChatInternals chat = chat();
        if (chat == null) return;

        // Vanilla draws two things through this same path that are not chat lines — the "N messages
        // queued" notice and the restricted-chat prompt. Neither is in the message log, so neither
        // matches here, and both are skipped without needing to be recognised individually.
        GuiMessage.Line line = lineFor(chat, content);
        if (line == null) return;

        if (cfg.heads) drawFace(g, content, y, alpha);

        if (cfg.hoverTimestamps && localMouse != null && hovering(chat, localMouse, y)) {
            long when = MessageClock.at(line.addedTime());
            g.setComponentTooltipForNextFrame(font, List.of(
                            Component.literal(MessageClock.clockText(when))
                                    .withStyle(s -> s.withColor(0xE1E5EC)),
                            Component.literal(MessageClock.ageText(when))
                                    .withStyle(s -> s.withColor(0x8A8A92))),
                    screenMouseX, screenMouseY);
        }
    }

    /**
     * Whether the cursor is on this line.
     *
     * <p>The band is the glyph height plus a pixel either side rather than the full line height: with
     * generous line spacing the gap between lines belongs to neither of them, and pointing into it is
     * not pointing at a message.
     */
    private static boolean hovering(ChatInternals chat, Vector2f localMouse, int y) {
        return localMouse.x >= 0 && localMouse.x <= chat.frogchat$width()
                && localMouse.y >= y - 1 && localMouse.y < y + GLYPH + 1;
    }

    /** Puts the sender's face in the gap their line reserved for it, at the line's own opacity. */
    private static void drawFace(GuiGraphicsExtractor g, FormattedCharSequence content, int y, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        int a = Math.round(Math.clamp(alpha, 0f, 1f) * 255f);
        if (a <= 0) return;

        Matcher matcher = SENDER.matcher(flatten(content));
        if (!matcher.find()) return;

        PlayerInfo sender = mc.getConnection().getPlayerInfo(matcher.group(1));
        if (sender == null) return;

        PlayerFaceExtractor.extractRenderState(g, sender.getSkin().body().texturePath(), 0, y, FACE,
                true, false, (a << 24) | 0xFFFFFF);
    }

    /**
     * The message log entry this drawn sequence came from, by identity.
     *
     * <p>Identity rather than text: the same words can be said twice, and the log holds the very
     * {@code FormattedCharSequence} being handed to us, so {@code ==} is both exact and free. A scan of
     * the log per drawn line is quadratic on paper and nothing in practice — only a page of lines is
     * ever drawn, and each step is a reference comparison.
     */
    private static GuiMessage.Line lineFor(ChatInternals chat, FormattedCharSequence content) {
        for (GuiMessage.Line line : chat.frogchat$lines()) {
            if (line.content() == content) return line;
        }
        return null;
    }

    private static ChatInternals chat() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null || mc.gui.hud == null) return null;
        return (ChatInternals) (Object) mc.gui.hud.getChat();
    }

    private static String flatten(FormattedCharSequence line) {
        StringBuilder out = new StringBuilder();
        line.accept((index, style, codePoint) -> {
            out.appendCodePoint(codePoint);
            return true;
        });
        return out.toString();
    }
}
