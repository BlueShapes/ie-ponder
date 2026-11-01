package dev.ieponder.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.createmod.ponder.api.level.PonderLevel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Minecraft-version-specific setup used only by the opt-in validation harness. */
final class ValidationPlatform {
    private static final String FIXTURE_BUTTON_LABEL = "Unrelated validation fixture";

    private ValidationPlatform() {}

    static void hideWindow(Minecraft client) {
        GLFW.glfwHideWindow(client.getWindow().getWindow());
    }

    static void recoverInitialScreen(Minecraft client) {
        // NeoForge's startup screen flow opens the title screen directly.
    }

    static Screen createOptionsScreen(Minecraft client) {
        return new ValidationOptionsScreen(client);
    }

    static boolean isOptionsScreen(Screen screen) {
        return screen instanceof OptionsScreen;
    }

    static int fixtureButtonCount(Screen screen) {
        return (int) screen.children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast)
                .filter(widget -> widget.getMessage().getString().equals(FIXTURE_BUTTON_LABEL)).count();
    }

    static int minecraftWidgetCount(Screen screen) {
        return (int) screen.children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast)
                .filter(widget -> !widget.getMessage().getString().equals(FIXTURE_BUTTON_LABEL))
                .filter(widget -> widget.getClass().getName().startsWith("net.minecraft.client.gui.components."))
                .count();
    }

    static void createFreshLevel(Minecraft client) {
        var rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
        client.createWorldOpenFlows().createFreshLevel("guide-validation-" + System.currentTimeMillis(),
                new LevelSettings("IE Guide Validation", GameType.CREATIVE, false,
                        Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                new WorldOptions(1L, false, false), registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), client.screen);
    }

    static CompoundTag saveWithoutMetadata(BlockEntity blockEntity, RegistryAccess registryAccess) {
        return blockEntity.saveWithoutMetadata(registryAccess);
    }

    static void loadWorld(Minecraft client, String name) {
        client.createWorldOpenFlows().openWorld(name, client::stop);
    }

    static double averageTickMillis(Minecraft client) {
        return client.getSingleplayerServer().getAverageTickTimeNanos() / 1e6;
    }

    static ModelData getModelData(PonderLevel level, BlockPos position) {
        return level.getModelData(position);
    }

    private static final class ValidationOptionsScreen extends OptionsScreen {
        private ValidationOptionsScreen(Minecraft client) { super(null, client.options); }

        @Override protected void init() {
            super.init();
            addRenderableWidget(Button.builder(Component.literal(FIXTURE_BUTTON_LABEL), ignored -> {})
                    .bounds(width - 164, height - 28, 156, 20).build());
        }
    }
}
