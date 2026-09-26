package dev.frogchat.hook;

import dev.frogchat.ChatLines;
import dev.frogchat.ChatMeta;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.FormattedCharSequence;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * The intake half of the mod: rewrites each chat line as it arrives, then tags the display lines
 * vanilla wraps it into.
 *
 * <p>26.3 split chat intake into three public doors — {@code addPlayerMessage},
 * {@code addServerSystemMessage} and {@code addClientSystemMessage} — but all three funnel into one
 * private {@code addMessage} that carries a {@link GuiMessageSource} saying which door it came
 * through. Hooking that single private method keeps one hook covering every line, and the source is
 * handed straight to {@link ChatLines#deliver}: it, not a regex over the text, is what tells the mod
 * a line came from a player.
 *
 * <p>Everything happens on arrival rather than at render time deliberately: a message is added once
 * and drawn every frame after that, so anything computed per-frame is computed thousands of times
 * for the same answer. It also means the rewritten text is what actually lives in the chat log, so
 * it survives scrolling, resizing and re-wrapping without further help.
 *
 * <p>The second injection is the other end of the same hand-off. The moment a message is split into
 * display lines is the moment both sides meet: the {@link GuiMessage} (whose content keys
 * {@link ChatMeta}'s intake record) and the very {@link FormattedCharSequence} instances the render
 * hooks are later handed. Tagging the lines here is what lets the render side do one identity lookup
 * instead of searching the log to rediscover which line it is drawing — and it catches every source
 * of lines, since fresh messages, re-wraps and filter changes all flow through
 * {@code addMessageToDisplayQueue}.
 */
@Mixin(ChatComponent.class)
public class ChatIntakeHook {

    /** The target, spelled once: 26.3's private four-argument intake. */
    @Unique
    private static final String ADD_MESSAGE = "addMessage(Lnet/minecraft/network/chat/Component;"
            + "Lnet/minecraft/network/chat/MessageSignature;"
            + "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;"
            + "Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V";

    @ModifyVariable(method = ADD_MESSAGE, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component frogchat$decorate(Component message, Component contentsArg,
                                        MessageSignature signature, GuiMessageSource source,
                                        GuiMessageTag tag) {
        // The styling itself lives in ChatLines, so the config screen's preview renders the very
        // same line this hook would.
        return ChatLines.deliver(message, source);
    }

    @ModifyVariable(method = "addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V",
            at = @At("STORE"), ordinal = 0)
    private List<FormattedCharSequence> frogchat$tagLines(List<FormattedCharSequence> lines,
                                                          GuiMessage message) {
        return ChatMeta.tagLines(message.content(), lines);
    }
}
