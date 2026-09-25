package dev.frogchat.hook;

import dev.frogchat.ChatLines;
import dev.frogchat.MessageClock;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rewrites a chat line on its way in, and notes when it got here.
 *
 * <p>Done on arrival rather than at render time deliberately: a message is added once and drawn every
 * frame after that, so anything computed per-frame is computed thousands of times for the same answer.
 * It also means the rewritten text is what actually lives in the chat log, so it survives scrolling,
 * resizing and re-wrapping without any further help.
 *
 * <p>26.3 split chat intake into three public doors — {@code addPlayerMessage},
 * {@code addServerSystemMessage} and {@code addClientSystemMessage} — but all three funnel into one
 * private {@code addMessage} that carries a {@link GuiMessageSource} saying which door it came through.
 * Hooking that single private method is what keeps one hook covering every line, rather than three that
 * have to agree with each other.
 */
@Mixin(ChatComponent.class)
public class ChatDecorateHook {

    /** The target, spelled once: 26.3's private four-argument intake. */
    @Unique
    private static final String ADD_MESSAGE = "addMessage(Lnet/minecraft/network/chat/Component;"
            + "Lnet/minecraft/network/chat/MessageSignature;"
            + "Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;"
            + "Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V";

    @Inject(method = ADD_MESSAGE, at = @At("HEAD"))
    private void frogchat$stampArrival(Component message, MessageSignature signature,
                                       GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        // Here rather than anywhere later because this is the one moment the GUI tick the line will
        // remember and the real clock are both in hand. See MessageClock.
        MessageClock.stamp();
    }

    @ModifyVariable(method = ADD_MESSAGE, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component frogchat$decorate(Component message) {
        // The styling itself lives in ChatLines, so the config screen's preview renders the very same
        // line this hook would.
        return ChatLines.decorate(message);
    }
}
