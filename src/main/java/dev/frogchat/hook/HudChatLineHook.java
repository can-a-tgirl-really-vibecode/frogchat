package dev.frogchat.hook;

import dev.frogchat.ChatLines;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Faces on chat lines while chat is closed.
 *
 * <p>26.3 draws chat through one of two {@code ChatGraphicsAccess} implementations chosen by the display
 * mode — this one on the HUD, its focused sibling while the chat screen is open. Hooking the pair of
 * them, at the single method that draws one line, is what lets the mod add to vanilla's chat instead of
 * replacing it: the fade, the position and the message log all stay vanilla's, and the head is simply
 * one more thing drawn at the coordinates vanilla just worked out.
 *
 * <p>No hover here. The cursor is captured during play, so there is nothing meaningful to point with.
 *
 * <p>The target is package-private, hence {@code targets} by name rather than a class literal.
 */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess")
public class HudChatLineHook {

    @Shadow @Final private GuiGraphicsExtractor graphics;

    @Inject(method = "handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z", at = @At("HEAD"))
    private void frogchat$decorateLine(int y, float alpha, FormattedCharSequence content,
                                       CallbackInfoReturnable<Boolean> cir) {
        ChatLines.onLine(graphics, Minecraft.getInstance().font, y, alpha, content, null, 0, 0);
    }
}
