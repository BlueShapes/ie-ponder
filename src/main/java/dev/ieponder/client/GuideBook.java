package dev.ieponder.client;

import com.google.gson.Gson;
import dev.ieponder.GuideResources;
import dev.ieponder.Platform;
import net.minecraft.core.BlockPos;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Immutable teaching data, shared by the catalogue, translations and storyboards. */
public final class GuideBook {
    private GuideBook() {}

    private static final class Holder {
        private static final List<Lesson> ALL_LESSONS = load();
    }

    /** All authored lessons, in the guide's chapter order. */
    public static List<Lesson> allLessons() { return Holder.ALL_LESSONS; }

    /** Lessons whose optional dependencies are present in the current client. */
    public static List<Lesson> lessons() {
        return lessonsForMods(Platform.loadedModIds());
    }

    /** Pure filter kept separate from the runtime mod list so every combination is testable. */
    public static List<Lesson> lessonsForMods(java.util.Set<String> loadedMods) {
        Objects.requireNonNull(loadedMods);
        return allLessons().stream().filter(lesson -> loadedMods.containsAll(lesson.requiresMods())).toList();
    }

    private static List<Lesson> load() {
        try {
            var lessons = new java.util.ArrayList<Lesson>();
            lessons.addAll(readLessons("/assets/ieponder/guide/lessons.json"));
            lessons.addAll(readLessons("/assets/ieponder/guide/processing.json"));
            lessons.sort(Comparator.comparingInt(lesson -> GuideChapter.orderOf(lesson.chapter())));
            if (lessons.isEmpty()) throw new IllegalStateException("Immersive Engineering lesson catalogue is empty");
            if (lessons.stream().map(Lesson::id).distinct().count() != lessons.size())
                throw new IllegalStateException("Duplicate Immersive Engineering lesson ID");
            return List.copyOf(lessons);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load Immersive Engineering Ponder Guide lessons", exception);
        }
    }

    private static List<Lesson> readLessons(String path) throws Exception {
        var stream = Objects.requireNonNull(GuideBook.class.getResourceAsStream(path), "Missing Immersive Engineering lesson catalogue: " + path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return List.copyOf(Arrays.asList(new Gson().fromJson(reader, Lesson[].class)));
        }
    }

    public record Localized(String ja, String en) {
        public Localized {
            if (ja == null || ja.isBlank() || en == null || en.isBlank())
                throw new IllegalArgumentException("Both Japanese and English text are required");
        }
    }

    public record Node(String id, String block, List<Integer> pos, Map<String, String> state, String nbt) {
        public Node {
            Objects.requireNonNull(id);
            GuideResources.parse(block);
            pos = List.copyOf(pos);
            if (pos.size() != 3 || pos.get(0) < 0 || pos.get(0) > 8 || pos.get(1) < 1
                    || pos.get(1) > 6 || pos.get(2) < 0 || pos.get(2) > 8)
                throw new IllegalArgumentException("Node outside the 9 x 7 x 9 stage: " + id);
            state = state == null ? Map.of() : Map.copyOf(state);
            nbt = nbt == null ? "{}" : nbt;
        }
        public BlockPos position() { return new BlockPos(pos.get(0), pos.get(1), pos.get(2)); }
    }

    public record Flow(String from, String to, String item) {}

    /** An optional structure supplied by Immersive Engineering and expanded into scene nodes. */
    public record Structure(String template, List<Integer> origin) {
        public Structure {
            if (template == null || !template.matches("[a-z0-9_]+"))
                throw new IllegalArgumentException("Invalid Immersive Engineering structure template: " + template);
            origin = List.copyOf(origin);
            if (origin.size() != 3 || origin.get(0) < 0 || origin.get(0) > 8
                    || origin.get(1) < 1 || origin.get(1) > 6
                    || origin.get(2) < 0 || origin.get(2) > 8)
                throw new IllegalArgumentException("Invalid structure origin for template " + template + ": " + origin);
        }
    }

    /** Visual actors belong only to the Ponder world and appear with their anchor. */
    public record Actor(String id, String type, List<Double> pos, String showAt) {
        public Actor {
            Objects.requireNonNull(id);
            GuideResources.parse(type);
            pos = List.copyOf(pos);
            if (pos.size() != 3 || pos.stream().anyMatch(value -> !Double.isFinite(value)))
                throw new IllegalArgumentException("Invalid actor position: " + id);
            Objects.requireNonNull(showAt);
        }
    }

    public record Callout(String item, String anchor, String action, String pointing, List<Double> offset, boolean sneak) {
        public Callout {
            GuideResources.parse(item);
            Objects.requireNonNull(anchor);
            action = action == null ? "none" : action;
            if (!List.of("none", "right_click", "left_click", "scroll").contains(action))
                throw new IllegalArgumentException("Invalid callout action: " + action);
            pointing = pointing == null ? "down" : pointing.toLowerCase(Locale.ROOT);
            if (!List.of("up", "down", "left", "right").contains(pointing))
                throw new IllegalArgumentException("Invalid callout direction: " + pointing);
            offset = offset == null ? List.of(0.0, 0.25, 0.0) : List.copyOf(offset);
            if (offset.size() != 3 || offset.stream().anyMatch(value -> !Double.isFinite(value) || Math.abs(value) > 2))
                throw new IllegalArgumentException("Invalid callout offset: " + offset);
            if (sneak && action.equals("none")) throw new IllegalArgumentException("Sneak requires a callout action");
        }
    }

    public record Step(Localized text, List<String> show, List<String> focus, List<String> hide,
                       Flow flow, boolean warning, Map<String, String> nbt, List<Callout> callouts) {
        public Step {
            Objects.requireNonNull(text);
            show = show == null ? List.of() : List.copyOf(show);
            focus = focus == null ? List.of() : List.copyOf(focus);
            hide = hide == null ? List.of() : List.copyOf(hide);
            nbt = nbt == null ? Map.of() : Map.copyOf(nbt);
            callouts = callouts == null ? List.of() : List.copyOf(callouts);
            if (callouts.size() > 3) throw new IllegalArgumentException("Too many callouts in one step");
        }
    }

    public record Lesson(String id, String chapter, Localized title, Localized summary, String icon,
                         List<String> requiresMods, List<String> components, Structure structure,
                         List<Node> nodes, List<Step> steps, List<Actor> entities) {
        public Lesson {
            if (id == null || !id.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid lesson ID");
            Objects.requireNonNull(chapter);
            Objects.requireNonNull(title);
            Objects.requireNonNull(summary);
            GuideResources.parse(icon);
            requiresMods = requiresMods == null ? List.of() : List.copyOf(requiresMods);
            if (requiresMods.stream().anyMatch(mod -> mod == null || !mod.matches("[a-z0-9_.-]+")))
                throw new IllegalArgumentException("Invalid required mod ID for lesson: " + id);
            components = List.copyOf(components);
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            steps = steps == null ? List.of() : List.copyOf(steps);
            if (structure != null) {
                var expanded = GuideStructures.expand(structure, nodes, steps);
                nodes = expanded.nodes();
                steps = expanded.steps();
            }
            entities = entities == null ? List.of() : List.copyOf(entities);
            if (entities.stream().map(Actor::id).distinct().count() != entities.size())
                throw new IllegalArgumentException("Duplicate actor in " + id);
            for (var actor : entities) requireNode(nodes, actor.showAt());
            if (nodes.isEmpty() || steps.isEmpty()) throw new IllegalArgumentException("Empty lesson: " + id);
            if (nodes.stream().map(Node::id).distinct().count() != nodes.size())
                throw new IllegalArgumentException("Duplicate node in " + id);
            var nodeList = nodes;
            var visible = new java.util.HashSet<String>();
            for (var step : steps) {
                step.show().forEach(n -> requireNode(nodeList, n));
                step.focus().forEach(n -> requireNode(nodeList, n));
                step.hide().forEach(n -> requireNode(nodeList, n));
                step.nbt().keySet().forEach(n -> requireNode(nodeList, n));
                visible.removeAll(step.hide());
                visible.addAll(step.show());
                for (var callout : step.callouts()) {
                    requireNode(nodeList, callout.anchor());
                    if (!visible.contains(callout.anchor()))
                        throw new IllegalArgumentException("Callout points to a hidden node: " + id + "/" + callout.anchor());
                }
                if (step.flow() != null) {
                    requireNode(nodes, step.flow().from());
                    requireNode(nodes, step.flow().to());
                }
            }
        }
        public Node node(String id) { return requireNode(nodes, id); }
        private static Node requireNode(List<Node> nodes, String id) {
            return nodes.stream().filter(n -> n.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown scene node: " + id));
        }
    }
}
