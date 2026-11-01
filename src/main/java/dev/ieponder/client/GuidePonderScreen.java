package dev.ieponder.client;

import dev.ieponder.mixin.PonderUIAccessor;
import net.createmod.catnip.gui.NavigatableSimiScreen;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** All lessons use Ponder's own arrows, next-up prompt and history navigation. */
public final class GuidePonderScreen extends PonderUI {
    private final String compiledLocale = Minecraft.getInstance().getLanguageManager().getSelected();

    public GuidePonderScreen(GuideBook.Lesson lesson) {
        super(PonderIndex.getSceneAccess().compile(GuidePlugin.course()));
        int index = GuideBook.lessons().indexOf(lesson);
        if (index < 0) throw new IllegalArgumentException("Unknown guide lesson: " + lesson.id());
        var access = (PonderUIAccessor) (Object) this;
        access.ieponder$setIndex(index);
        access.ieponder$getLazyIndex().startWithValue(index);
    }

    @Override protected String getBreadcrumbTitle() {
        return Component.translatable("ieponder.title").getString();
    }

    @Override protected void replay() {
        // Ponder's Shift+Replay reloads only one item's scenes. A multi-item course
        // must retain its whole list and the selected position when replaying.
        ((PonderUIAccessor) (Object) this).ieponder$setIdentifyMode(false);
        getActiveScene().begin();
    }

    @Override public boolean isEquivalentTo(NavigatableSimiScreen other) {
        return other instanceof GuidePonderScreen guide
                && compiledLocale.equals(guide.compiledLocale)
                && getActiveScene().getId().equals(guide.getActiveScene().getId());
    }

    public static void open(GuideBook.Lesson lesson) {
        var screen = new GuidePonderScreen(lesson);
        // Catnip's transition framebuffer is intended for its own navigatable screens.
        // Ordinary options and chat screens use the same history without that animation.
        if (Minecraft.getInstance().screen instanceof NavigatableSimiScreen)
            ScreenOpener.transitionTo(screen);
        else
            ScreenOpener.open(screen);
    }
}
