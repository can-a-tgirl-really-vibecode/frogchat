package dev.frogchat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;

import org.jspecify.annotations.Nullable;

import java.util.List;

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

    /** Adds the message metadata to each display line produced by vanilla. */
    public static List<FormattedCharSequence> tagLines(Component message, List<FormattedCharSequence> lines) {
        if (!(message instanceof TaggedComponent tagged)) return lines;
        return lines.stream()
                .<FormattedCharSequence>map(line -> new TaggedLine(line, tagged.meta()))
                .toList();
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

    private record TaggedLine(FormattedCharSequence delegate, LineMeta meta)
            implements FormattedCharSequence {
        @Override public boolean accept(FormattedCharSink output) { return delegate.accept(output); }
    }
}
