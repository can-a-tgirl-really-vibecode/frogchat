package dev.frogchat.hook;

import dev.frogchat.ChatLines;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

import org.joml.Vector2f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Faces and hover timestamps while the chat screen is open.
 *
 * <p>The focused half of the pair described in {@link HudChatLineHook}, and the one that can do hover,
 * because this implementation already works out where the cursor is: {@code localMousePos} is the mouse
 * inverted through the chat pose, which vanilla computes for its own link hovering. Borrowing it means
 * the hover test is a straight comparison against the line's y, with no second copy of the chat layout
 * to drift out of agreement with the real one.
 *
 * <p>{@code globalMouseX/Y} are the screen coordinates the tooltip is positioned at — a tooltip is drawn
 * later, outside this pose, so it wants the untransformed cursor.
 */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess")
public class OpenChatLineHook {

    @Shadow @Final private GuiGraphicsExtractor graphics;
    @Shadow @Final private Font font;
    @Shadow @Final private Vector2f localMousePos;
    @Shadow @Final private int globalMouseX;
    @Shadow @Final private int globalMouseY;

    @Inject(method = "handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z", at = @At("HEAD"))
    private void frogchat$decorateLine(int y, float alpha, FormattedCharSequence content,
                                       CallbackInfoReturnable<Boolean> cir) {
        ChatLines.onLine(graphics, font, y, alpha, content, localMousePos, globalMouseX, globalMouseY);
    }
}
