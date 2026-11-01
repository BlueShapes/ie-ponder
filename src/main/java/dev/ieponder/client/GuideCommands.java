package dev.ieponder.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/** Local navigation commands; no packets or world mutations. */
final class GuideCommands {
    private static final SimpleCommandExceptionType REQUIRES_WORLD = new SimpleCommandExceptionType(
            Component.translatable("ieponder.command.requires_world"));
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(value ->
            Component.translatable("ieponder.command.unknown", value));

    private GuideCommands() {}

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerAlias(dispatcher, "ponder-ie");
        registerAlias(dispatcher, "ieponder");
    }

    private static void registerAlias(CommandDispatcher<CommandSourceStack> dispatcher, String name) {
        dispatcher.register(Commands.literal(name)
                .executes(context -> {
                    requireWorld();
                    GuideNavigation.openIndex();
                    return 1;
                })
                .then(Commands.literal("item")
                        .executes(context -> openItem(""))
                        .then(Commands.argument("item", StringArgumentType.greedyString())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        itemSuggestions(), builder))
                                .executes(context -> openItem(
                                        StringArgumentType.getString(context, "item")))))
                .then(Commands.literal("search")
                        .then(Commands.argument("query", StringArgumentType.greedyString())
                                .executes(context -> openSearch(StringArgumentType.getString(context, "query")))))
                .then(Commands.argument("chapterOrLesson", StringArgumentType.greedyString())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                chapterAndLessonSuggestions(), builder))
                        .executes(context -> openChapterOrLesson(
                                StringArgumentType.getString(context, "chapterOrLesson")))));
    }

    private static int openChapterOrLesson(String query) throws CommandSyntaxException {
        requireWorld();
        var lesson = GuideNavigation.findLessonForId(query);
        if (lesson == null) lesson = GuideNavigation.findLessonForChapter(query);
        if (lesson == null) throw UNKNOWN.create(query);
        GuideNavigation.openLesson(lesson);
        return 1;
    }

    private static int openItem(String query) throws CommandSyntaxException {
        requireWorld();
        var lesson = GuideNavigation.findLessonForItem(query);
        if (lesson == null) throw UNKNOWN.create(query);
        GuideNavigation.openLesson(lesson);
        return 1;
    }

    private static int openSearch(String query) throws CommandSyntaxException {
        requireWorld();
        var lesson = GuideNavigation.findLessonForSearch(query);
        if (lesson == null) throw UNKNOWN.create(query);
        GuideNavigation.openLesson(lesson);
        return 1;
    }

    private static void requireWorld() throws CommandSyntaxException {
        if (Minecraft.getInstance().level == null) throw REQUIRES_WORLD.create();
    }

    private static Set<String> chapterAndLessonSuggestions() {
        Set<String> result = new LinkedHashSet<>();
        for (var chapter : GuideChapter.active(GuideBook.lessons())) {
            result.add(Integer.toString(chapter.ordinal() + 1));
            result.add(chapter.id());
            result.add(chapter.en());
            result.add(chapter.label().getString());
        }
        GuideBook.lessons().forEach(lesson -> result.add(lesson.id()));
        return result;
    }

    private static Set<String> itemSuggestions() {
        Set<String> result = new LinkedHashSet<>();
        for (var lesson : GuideBook.lessons()) {
            result.add(lesson.icon());
            result.addAll(lesson.components());
        }
        return result;
    }
}
