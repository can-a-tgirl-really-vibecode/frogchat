package dev.frogchat;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.List;
import java.util.Optional;

/**
 * One chat line as the player themself sends it, drawn with the exact styling the mod applies to
 * real lines, under a live copy of the settings (see the constructor). Being a config-list entry
 * means it re-renders every frame, so toggling a row in this category is visible in it immediately
 * and nothing fake ever lands in the real chat log.
 *
 * <p>Not interactive: a click or a focus halt on a preview would do nothing.
 */
final class ChatPreviewEntry extends AbstractConfigListEntry<Void> {

    private static final String SAMPLE = "Trans Rights are Human Rights!";

    private final ChatConfig view;
    private final Runnable refresh;

    /**
     * @param view    settings to render with — a standalone copy, so a Cancel in the screen never
     *                touches the live config
     * @param refresh copies the screen entries' current values into {@code view}; Cloth only runs
     *                save consumers on Done, so without this the preview would show the saved
     *                settings, not the ones on screen
     */
    ChatPreviewEntry(ChatConfig view, Runnable refresh) {
        super(Component.literal("Chat preview"), false);
        setEditable(false);
        this.view = view;
        this.refresh = refresh;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int index, int y, int x, int entryWidth,
            int entryHeight, int mouseX, int mouseY, boolean hovered, float a) {
        Minecraft mc = Minecraft.getInstance();
        refresh.run();
        ChatConfig cfg = view;

        // The suggestion of a chat pane: dim background while open-chat widgets are not underneath.
        g.fill(x, y + 1, x + entryWidth, y + entryHeight - 1, 0x40000000);

        Component line = ChatLines.decorate(Component.literal("<" + mc.getUser().getName() + "> " + SAMPLE), cfg);
        int textY = y + (entryHeight - 9) / 2;

        if (cfg.heads) drawFace(g, mc, x, textY);
        g.text(mc.font, line.getVisualOrderText(), x, textY, 0xFFFFFFFF, true);

        // Hover timestamps get their live demo too, on the line they would be for.
        if (cfg.hoverTimestamps && hovered) {
            long now = System.currentTimeMillis();
            g.setComponentTooltipForNextFrame(mc.font, List.of(
                            Component.literal(MessageClock.clockText(now))
                                    .withStyle(s -> s.withColor(0xE1E5EC)),
                            Component.literal(MessageClock.ageText(now))
                                    .withStyle(s -> s.withColor(0x8A8A92))),
                    mouseX, mouseY);
        }
    }

    /**
     * The local player's face at the line's left edge — the real skin while connected (the same lookup
     * the chat line itself would make), the default skin at the title menu.
     */
    private void drawFace(GuiGraphicsExtractor g, Minecraft mc, int x, int y) {
        PlayerSkin skin = mc.getConnection() != null && mc.getConnection().getPlayerInfo(mc.getUser().getProfileId()) != null
                ? mc.getConnection().getPlayerInfo(mc.getUser().getProfileId()).getSkin()
                : DefaultPlayerSkin.get(mc.getUser().getProfileId());
        PlayerFaceExtractor.extractRenderState(g, skin.body().texturePath(), x, y, ChatLines.FACE,
                true, false, 0xFFFFFFFF);
    }

    @Override
    public int getItemHeight() {
        return 20;
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of();
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of();
    }

    @Override
    public Void getValue() {
        return null;
    }

    @Override
    public Optional<Void> getDefaultValue() {
        return Optional.empty();
    }
}
