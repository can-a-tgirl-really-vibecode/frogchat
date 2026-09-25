package dev.frogchat.hook;

import dev.frogchat.ChatConfig;
import dev.frogchat.FrogChat;
import dev.frogchat.MessageClock;
import dev.frogchat.NameTint;

import net.minecraft.client.Minecraft;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** Vanilla's own {@code <name> body}, which is what tells us a line came from a player. */
    @Unique
    private static final Pattern FROM_PLAYER =
            Pattern.compile("^<([A-Za-z0-9_]{1,16})>\\s(.*)$", Pattern.DOTALL);

    /** The punctuation between name and message, and the message itself: quiet, so the name carries. */
    @Unique private static final int PUNCTUATION = 0x8A8A92;
    @Unique private static final int BODY = 0xE1E5EC;

    /** Pixels a face occupies, which is what the reserved gap has to clear. */
    @Unique private static final int FACE = 8;

    @Inject(method = ADD_MESSAGE, at = @At("HEAD"))
    private void frogchat$stampArrival(Component message, MessageSignature signature,
                                       GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        // Here rather than anywhere later because this is the one moment the GUI tick the line will
        // remember and the real clock are both in hand. See MessageClock.
        MessageClock.stamp();
    }

    @ModifyVariable(method = ADD_MESSAGE, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component frogchat$decorate(Component message) {
        ChatConfig cfg = FrogChat.config();

        Matcher fromPlayer = FROM_PLAYER.matcher(message.getString());
        boolean player = fromPlayer.matches();
        if (!player) return message;

        Component line = message;
        if (cfg.nameColours) {
            String name = fromPlayer.group(1);
            int tint = NameTint.of(name);
            line = Component.empty()
                    .append(Component.literal(name).withStyle(s -> s.withColor(tint)))
                    .append(Component.literal(": ").withStyle(s -> s.withColor(PUNCTUATION)))
                    .append(Component.literal(fromPlayer.group(2)).withStyle(s -> s.withColor(BODY)));
        }

        // Reserve the face's width in spaces here, not at render time: the head is painted over this gap,
        // and a line that reserved nothing would have its own text underneath it. Reserved on arrival so
        // it survives re-wrapping, which is also why it cannot be decided by the code that draws faces.
        if (cfg.heads) {
            line = Component.literal(frogchat$gapFor(FACE)).append(line);
        }
        return line;
    }

    /** Enough spaces to clear {@code px} pixels at the chat font's space width, plus one for breathing. */
    @Unique
    private String frogchat$gapFor(int px) {
        int space = Math.max(1, Minecraft.getInstance().font.width(" "));
        return " ".repeat(Math.max(1, (px + space - 1) / space) + 1);
    }
}
