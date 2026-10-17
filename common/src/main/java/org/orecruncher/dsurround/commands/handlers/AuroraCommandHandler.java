package org.orecruncher.dsurround.commands.handlers;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.AuroraStyle;
import org.orecruncher.dsurround.effects.aurora.AuroraActivity;
import org.orecruncher.dsurround.effects.aurora.AuroraColour;
import org.orecruncher.dsurround.effects.aurora.RealisticAurora;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.processing.AuroraHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /dsaurora: shows tonight's realistic aurora, and holds how active it is, where in its night it is and its colour
 * character, for seeing each.
 */
public class AuroraCommandHandler {

    private static final String KEY = "dsurround.command.dsaurora.";

    public static List<Component> status() {
        return status(handler(), ContainerManager.resolve(Configuration.class).auroraOptions.style);
    }

    public static Component activity(AuroraActivity activity) {
        handler().holdActivity(activity);
        return Component.translatable(KEY + "activity.success", name(activity));
    }

    public static Component phase(RealisticAurora.Phase phase) {
        handler().holdPhase(phase);
        return Component.translatable(KEY + "phase.success", name(phase));
    }

    public static Component colour(AuroraColour colour) {
        handler().holdColour(colour);
        return Component.translatable(KEY + "colour.success", name(colour));
    }

    public static Component clear() {
        return Component.translatable(KEY + (handler().clearHeld() ? "clear.success" : "clear.notset"));
    }

    static List<Component> status(AuroraHandler handler, AuroraStyle style) {
        var lines = new ArrayList<Component>();
        if (style != AuroraStyle.REALISTIC) {
            lines.add(Component.translatable(KEY + "status.classic"));
            return lines;
        }

        var aurora = handler.realistic();
        var moment = handler.moment();
        if (aurora.isPresent() && moment.isPresent()) {
            var nearest = aurora.get().arcs().getFirst().distance() - moment.get().approach();
            lines.add(Component.translatable(KEY + "status", name(aurora.get().activity()), name(moment.get().phase()),
                    name(aurora.get().colour()), aurora.get().arcs().size(), Math.round(nearest)));
        } else {
            lines.add(Component.translatable(KEY + "status.none", handler.status()));
        }

        var activity = handler.heldActivity();
        var phase = handler.heldPhase();
        var colour = handler.heldColour();
        if (activity.isPresent() || phase.isPresent() || colour.isPresent()) {
            var none = Component.translatable(KEY + "status.notheld");
            lines.add(Component.translatable(KEY + "status.held",
                    activity.map(a -> (Component) Component.literal(name(a))).orElse(none),
                    phase.map(p -> (Component) Component.literal(name(p))).orElse(none),
                    colour.map(c -> (Component) Component.literal(name(c))).orElse(none)));
        }
        return lines;
    }

    private static String name(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static AuroraHandler handler() {
        return ContainerManager.resolve(AuroraHandler.class);
    }
}
