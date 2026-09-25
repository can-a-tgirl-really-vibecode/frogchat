package dev.frogchat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import net.fabricmc.loader.api.FabricLoader;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.Requirement;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Mod Menu entrypoint, only ever loaded when Mod Menu itself is installed. Builds the config screen
 * behind its config button with Cloth Config's {@code ConfigBuilder}: one category, a row per
 * {@link ChatConfig} setting, saved back to {@code config/frogchat.json} on Done. Cloth Config is a
 * soft dependency — without it the button opens a short note pointing at the config file instead.
 *
 * <p>Colour overrides stay file-only.
 */
public final class FrogChatModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return FrogChatModMenu::buildScreen;
        }
        return ClothMissingScreen::new;
    }

    private static Screen buildScreen(Screen parent) {
        ChatConfig config = FrogChat.config();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("FrogChat"))
                .setSavingRunnable(config::save);
        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("FrogChat"));

        BooleanListEntry nameColours = entries.startBooleanToggle(
                        Component.literal("Name colours"), config.nameColours)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Give each sender their own colour"))
                .setSaveConsumer(v -> config.nameColours = v)
                .build();
        Requirement nameColoursOn = Requirement.isTrue(nameColours);

        EnumListEntry<ChatConfig.NameColourSource> source = entries.startEnumSelector(
                        Component.literal("Name colour source"),
                        ChatConfig.NameColourSource.class, config.nameColourSource)
                .setDefaultValue(ChatConfig.NameColourSource.HASH)
                .setEnumNameProvider(v -> Component.literal(
                        v == ChatConfig.NameColourSource.LOCATOR ? "Locator bar" : "Name hash"))
                .setTooltip(Component.literal(
                        "Name hash: the pastel every client agrees on. "
                                + "Locator bar: copy the player's dot, team colour and all"))
                .setRequirement(nameColoursOn)
                .setSaveConsumer(v -> config.nameColourSource = v)
                .build();
        BooleanListEntry pastel = entries.startBooleanToggle(
                        Component.literal("Pastel name colours"), config.pastelColours)
                .setDefaultValue(true)
                .setTooltip(Component.literal(
                        "Light, readable pastels whatever the source — off for full-strength colours"))
                .setRequirement(nameColoursOn)
                .setSaveConsumer(v -> config.pastelColours = v)
                .build();
        BooleanListEntry restyle = entries.startBooleanToggle(
                        Component.literal("Restyle as name: body"), config.restyleNames)
                .setDefaultValue(false)
                .setTooltip(Component.literal(
                        "Rewrite <name> body as name: body — off keeps vanilla's look"))
                .setSaveConsumer(v -> config.restyleNames = v)
                .build();
        BooleanListEntry heads = entries.startBooleanToggle(
                        Component.literal("Player heads"), config.heads)
                .setDefaultValue(true)
                .setTooltip(Component.literal("The sender's face beside their line"))
                .setSaveConsumer(v -> config.heads = v)
                .build();
        BooleanListEntry hover = entries.startBooleanToggle(
                        Component.literal("Hover timestamps"), config.hoverTimestamps)
                .setDefaultValue(true)
                .setTooltip(Component.literal(
                        "Pointing at a line while chat is open shows when it arrived"))
                .setSaveConsumer(v -> config.hoverTimestamps = v)
                .build();

        // The preview renders off a copy of the settings synced from the rows' current values, so
        // it shows what the screen says — not what was last saved.
        ChatConfig view = new ChatConfig();
        view.colourOverrides = config.colourOverrides;

        // Up top so it stays on screen while the rows below change what it shows.
        general.addEntry(new ChatPreviewEntry(view, () -> {
            view.nameColours = nameColours.getValue();
            view.nameColourSource = source.getValue();
            view.pastelColours = pastel.getValue();
            view.restyleNames = restyle.getValue();
            view.heads = heads.getValue();
            view.hoverTimestamps = hover.getValue();
        }));
        general.addEntry(nameColours);
        general.addEntry(source);
        general.addEntry(pastel);
        general.addEntry(restyle);
        general.addEntry(heads);
        general.addEntry(hover);
        general.addEntry(entries.startTextDescription(
                        Component.literal("Colour overrides live in config/frogchat.json"))
                .build());

        return builder.build();
    }

    /** Fallback for the config button when Cloth Config is not installed. */
    private static final class ClothMissingScreen extends Screen {

        private final Screen parent;

        ClothMissingScreen(Screen parent) {
            super(Component.literal("FrogChat"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                    .bounds(this.width / 2 - 100, this.height / 2 + 20, 200, 20)
                    .build());
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            super.extractRenderState(graphics, mouseX, mouseY, a);
            graphics.centeredText(this.font,
                    Component.literal("The config screen needs Cloth Config installed."),
                    this.width / 2, this.height / 2 - 12, 0xFFFFFFFF);
            graphics.centeredText(this.font,
                    Component.literal("You can still edit config/frogchat.json by hand."),
                    this.width / 2, this.height / 2 + 2, 0xFFAAAAAA);
        }

        @Override
        public void onClose() {
            this.minecraft.gui.setScreen(parent);
        }
    }
}
