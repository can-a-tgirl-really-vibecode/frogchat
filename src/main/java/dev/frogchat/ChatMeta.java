package dev.frogchat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Metadata delegates carried through vanilla's message wrapping. This avoids side maps and lets
 * resize/filter re-wraps naturally produce newly tagged display lines.
 */
public final class ChatMeta {

    private ChatMeta() {}

    /**
     * @param sender        the sender's account name, or null for anything not from a player — system
     *                      notices get timestamps but neither a head nor a tint
     * @param arrivedMillis wall-clock arrival, for the hover tooltip
     */
    public record LineMeta(@Nullable String sender, long arrivedMillis) {}

    /** Adds metadata without changing how vanilla reads or renders the component. */
    public static Component tag(Component message, LineMeta meta) {
        return new TaggedComponent(message, meta);
    }

    /**
     * Adds the message metadata to each display line produced by vanilla, and marks the line the
     * message begins on.
     *
     * <p>The metadata is per <i>message</i>, and every line of a wrapped message shares it — so the
     * sender it names is not on its own a licence to draw a head. A long message wraps into several
     * lines that all report the same sender, and only the first of them actually starts with the
     * name. {@code lines} arrives in visual order straight from {@code GuiMessage.splitLines}, so
     * that first line is index 0.
     *
     * <p>Not {@code GuiMessage.Line.endOfEntry()}, which looks like it would say the same thing and
     * does not: the display log is built bottom-up, so that flag marks the line a message
     * <i>ends</i> on — the bottom one, furthest from the name.
     */
    public static List<FormattedCharSequence> tagLines(Component message, List<FormattedCharSequence> lines) {
        if (!(message instanceof TaggedComponent tagged)) return lines;
        return IntStream.range(0, lines.size())
                .<FormattedCharSequence>mapToObj(i -> new TaggedLine(lines.get(i), tagged.meta(), i == 0))
                .toList();
    }

    /**
     * Whether this display line is the one its message begins on — the only line carrying the
     * sender's name, and so the only one a head belongs in front of.
     */
    public static boolean isFirstLine(FormattedCharSequence line) {
        return line instanceof TaggedLine tagged && tagged.firstLine();
    }

    /**
     * The meta for a line being drawn, or null for things that are not log lines at all — the "N
     * messages queued" notice and the restricted-chat prompt pass through the same draw call and are
     * skipped without needing to be recognised individually.
     */
    public static @Nullable LineMeta metaFor(FormattedCharSequence line) {
        return line instanceof TaggedLine tagged ? tagged.meta() : null;
    }

    private record TaggedComponent(Component delegate, LineMeta meta) implements Component {
        @Override public Style getStyle() { return delegate.getStyle(); }
        @Override public ComponentContents getContents() { return delegate.getContents(); }
        @Override public List<Component> getSiblings() { return delegate.getSiblings(); }
        @Override public FormattedCharSequence getVisualOrderText() { return delegate.getVisualOrderText(); }
    }

    private record TaggedLine(FormattedCharSequence delegate, LineMeta meta, boolean firstLine)
            implements FormattedCharSequence {
        @Override public boolean accept(FormattedCharSink output) { return delegate.accept(output); }
    }
}
