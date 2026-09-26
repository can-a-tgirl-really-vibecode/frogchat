package dev.frogchat.hook;

import dev.frogchat.Emojis;
import dev.frogchat.FrogChat;

import com.mojang.brigadier.suggestion.Suggestion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Paints the emoji itself beside its shortcode in the completion dropdown.
 *
 * <p>Vanilla renders the list as plain strings, so the sprite is drawn on top after the text, in
 * the space {@link EmojiSuggestionsHook} widened the dropdown by. Positioning repeats vanilla's own
 * row arithmetic: rows are 12&nbsp;tall from the box's top-left, and the visible window is
 * {@code rect}'s height divided by 12 (the box is sized to it in the first place).
 */
@Mixin(targets = "net.minecraft.client.gui.components.CommandSuggestions$SuggestionsList")
public abstract class EmojiSuggestionListHook {

    @Shadow private Rect2i rect;
    @Shadow private List<Suggestion> suggestionList;
    @Shadow private int offset;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V", at = @At("TAIL"))
    private void frogchat$drawEmojis(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (!FrogChat.config().emoji) return;

        int rows = this.rect.getHeight() / 12;
        for (int i = 0; i < rows; i++) {
            Suggestion suggestion = this.suggestionList.get(i + this.offset);
            Identifier sprite = Emojis.spriteFor(suggestion.getText());
            if (sprite == null) continue;

            int x = this.rect.getX() + 1 + Minecraft.getInstance().font.width(suggestion.getText()) + 2;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, this.rect.getY() + 1 + 12 * i, 10, 10);
        }
    }
}
