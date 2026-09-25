package dev.frogchat;

import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What the mod knows about a chat line, carried from intake to render.
 *
 * <p>Two facts are known at intake and nowhere else: who sent the message (parsed once, structurally,
 * out of the decorated {@code chat.type.*} component) and when it arrived (wall-clock, the moment the
 * intake hook runs). Rendering one line every frame needs both. Vanilla hands the renderer only the
 * wrapped {@link FormattedCharSequence}, so these maps bridge the gap by identity:
 *
 * <ul>
 *   <li>{@link #record} runs as a message is added, keyed by the {@link Component} — the very instance
 *       the {@code GuiMessage} is built around.</li>
 *   <li>{@link #register} runs when vanilla wraps that message into display lines, moving each line
 *       onto its own key. From then on the per-line draw hooks look up what they drew in O(1), with no
 *       scan of the message log and no re-parsing of already-decorated text.</li>
 * </ul>
 *
 * <p>Weak keys mean nothing is collected here that vanilla does not also hold: an entry dies with the
 * message or line it describes, and a re-wrap (resize, filter) simply re-registers fresh lines through
 * the same path.
 */
public final class ChatMeta {

    private ChatMeta() {}

    /**
     * @param sender        the sender's account name, or null for anything not from a player — system
     *                      notices get timestamps but neither a head nor a tint
     * @param arrivedMillis wall-clock arrival, for the hover tooltip
     */
    public record LineMeta(@Nullable String sender, long arrivedMillis) {}

    private static final Map<Component, LineMeta> BY_MESSAGE = new WeakHashMap<>();
    private static final Map<FormattedCharSequence, LineMeta> BY_LINE = new WeakHashMap<>();

    /** Notes a message as it is added, keyed by the component the {@code GuiMessage} will hold. */
    public static void record(Component message, LineMeta meta) {
        BY_MESSAGE.put(message, meta);
    }

    /** Moves a message's meta onto each of its freshly wrapped display lines. Called from the wrap hook. */
    public static void register(Component message, List<FormattedCharSequence> lines) {
        LineMeta meta = BY_MESSAGE.get(message);
        if (meta == null) return;
        for (FormattedCharSequence line : lines) {
            BY_LINE.put(line, meta);
        }
    }

    /**
     * The meta for a line being drawn, or null for things that are not log lines at all — the "N
     * messages queued" notice and the restricted-chat prompt pass through the same draw call and are
     * skipped without needing to be recognised individually.
     */
    public static @Nullable LineMeta metaFor(FormattedCharSequence line) {
        return BY_LINE.get(line);
    }
}
