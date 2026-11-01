package dev.ieponder.client;

import dev.ieponder.GuideConstants;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;

public final class GuideClient {
    private GuideClient() {}

    static void setup() {
        PonderIndex.addPlugin(new GuidePlugin());
    }

    static void tick() {
        var client = Minecraft.getInstance();
        if (client.screen instanceof PonderUI ui && ui.getActiveScene().isFinished()
                && ui.getActiveScene().getId().getNamespace().equals(GuideConstants.MOD_ID)) {
            GuideProgress.complete(ui.getActiveScene().getId().getPath());
        }
    }

}
