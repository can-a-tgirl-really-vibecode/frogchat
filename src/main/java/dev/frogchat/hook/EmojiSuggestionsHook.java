package dev.frogchat.hook;

import dev.frogchat.Emojis;
import dev.frogchat.FrogChat;

import com.mojang.brigadier.suggestion.Suggestions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Emojis in the tab-completion dropdown, for plain chat text.
 *
 * <p>Non-command chat completes against one collection on vanilla's side — player names plus
 * whatever the server added, which is why connected players "show up if you write something".
 * This hook appends the emoji shortcodes to that same collection, but only when the word under
 * the cursor starts with {@code :} (matching how vanilla filters the entries anyway), so they
 * appear exactly like player names: type {@code :so}, the list offers {@code :sob:}.
 *
 * <p>Vanilla only ever pops that list on Tab for plain chat text (auto-suggestions are a
 * command-only thing), so the list is shown as the emoji is being typed — type {@code :so} and
 * {@code :sob:} is right there, with Tab, arrows, click or mouse wheel to pick it. The second
 * injection widens the dropdown by one emoji's width when it contains any, so the sprite
 * {@link EmojiSuggestionListHook} paints beside the shortcode stays inside the box.
 */
@Mixin(CommandSuggestions.class)
public abstract class EmojiSuggestionsHook {

    @Shadow private EditBox input;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow private CommandSuggestions.@Nullable SuggestionsList suggestions;
    @Shadow private Minecraft minecraft;

    @Shadow
    public abstract void showSuggestions(boolean immediateNarration);

    @ModifyVariable(method = "updateCommandInfo()V", at = @At("STORE"), ordinal = 0)
    private Collection<String> frogchat$addEmojis(Collection<String> suggestions) {
        String word = frogchat$lastWord();
        if (!FrogChat.config().emoji || !word.startsWith(":")) return suggestions;

        // Offer only what already matches, capped — vanilla measures and sorts the whole list on
        // every keystroke, so handing it all ~1900 shortcodes each time would stutter.
        Collection<String> withEmojis = new ArrayList<>(suggestions);
        Emojis.shortcodes().stream().filter(s -> s.startsWith(word)).limit(6).forEach(withEmojis::add);
        return withEmojis;
    }

    /** The word under the cursor (everything since the last space). */
    private String frogchat$lastWord() {
        String typed = this.input.getValue().substring(0, this.input.getCursorPosition());
        return typed.substring(typed.lastIndexOf(' ') + 1);
    }

    /** Pop the dropdown while an emoji is being typed, the way Discord does. */
    @Inject(method = "updateCommandInfo()V", at = @At("TAIL"))
    private void frogchat$autoShowEmojis(CallbackInfo ci) {
        if (this.suggestions == null && FrogChat.config().emoji
                && this.minecraft.options.autoSuggestions().get()
                && frogchat$lastWord().startsWith(":")) {
            showSuggestions(false);
        }
    }

    // ModifyArg at the constructor call, not ModifyVariable on maxSuggestionWidth's first store:
    // the Math.max re-store inside the loop is a separate instruction and would overwrite the
    // widening, leaving the sprite EmojiSuggestionListHook paints poking past the box's edge.
    // Index 3 because SuggestionsList is an inner class: the constructor's real args are
    // (CommandSuggestions this$0, int x, int y, int width, List, boolean) — width is 3, not 2.
    @ModifyArg(method = "showSuggestions(Z)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/CommandSuggestions$SuggestionsList;"
                            + "<init>(Lnet/minecraft/client/gui/components/CommandSuggestions;III"
                            + "Ljava/util/List;Z)V"),
            index = 3)
    private int frogchat$widenForEmojis(int maxSuggestionWidth) {
        Suggestions suggestions = this.pendingSuggestions == null ? null : this.pendingSuggestions.getNow(null);
        if (suggestions != null && suggestions.getList().stream()
                .anyMatch(s -> Emojis.spriteFor(s.getText()) != null)) {
            // Room for the sprite EmojiSuggestionListHook paints (10px) plus a little breathing space.
            return maxSuggestionWidth + 12;
        }
        return maxSuggestionWidth;
    }
}
