package dev.ieponder.validation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge event bridge for the shared validation harness. */
@EventBusSubscriber(modid = "ieponder", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GuideValidationEvents {
    private GuideValidationEvents() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (!GuideValidation.initialize()) return;
            NeoForge.EVENT_BUS.addListener(GuideValidationEvents::onClientTick);
            NeoForge.EVENT_BUS.addListener(GuideValidationEvents::onAfterFrame);
            NeoForge.EVENT_BUS.addListener(GuideValidationEvents::onScreenInit);
        });
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        GuideValidation.tick();
    }

    private static void onAfterFrame(RenderFrameEvent.Post event) {
        GuideValidation.rendered();
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        if (ValidationPlatform.isOptionsScreen(event.getScreen())) {
            // Simulate another guide using the former fixed bottom-left slot.
            event.addListener(net.minecraft.client.gui.components.Button.builder(
                    net.minecraft.network.chat.Component.literal("T"), button -> {})
                    .bounds(8, event.getScreen().height - 28, 20, 20).build());
        }
    }
}
