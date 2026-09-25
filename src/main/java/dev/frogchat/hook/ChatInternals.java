package dev.frogchat.hook;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * The two private parts of vanilla chat the decoration has to read.
 *
 * <p>Deliberately only two. Back when the mod drew the chat pane itself this interface also reached for
 * the scale, the line height, the scrollbar position and the lines-per-page, because a renderer needs a
 * whole layout; drawing inside vanilla's own per-line call needs none of that, since the layout arrives
 * as arguments. They are gone rather than kept for later — an accessor naming a member nobody uses is a
 * crash on the next version that renames it, in exchange for nothing, which is exactly how {@code
 * isChatHidden} took the mod down on 26.3.
 *
 * <p>{@code getWidth} stays because the hover test needs to know where chat stops horizontally, and it
 * is vanilla's own arithmetic over the chat-width option rather than a second copy of it.
 */
@Mixin(ChatComponent.class)
public interface ChatInternals {

    /** The wrapped, display-ready lines, newest first — searched to match a drawn line to its message. */
    @Accessor("trimmedMessages")
    List<GuiMessage.Line> frogchat$lines();

    @Invoker("getWidth")
    int frogchat$width();
}
