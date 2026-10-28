package org.orecruncher.dsurround.profiling;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

import java.util.List;

/**
 * A Java Flight Recorder event carrying one thing a recording's times depend on besides the machine (see
 * {@link ProfileFeature}), one for each, written as /dsprofile starts recording. Only loaded while recording: the
 * {@code jdk.jfr} module may not be there at all in a player's Java.
 */
@Name("dsurround.Feature")
@Label("Feature")
@Category("Dynamic Surroundings")
@Description("An optional hook's status, a feature standing aside for another mod, or a setting, as a Dynamic Surroundings profile was recorded with")
@StackTrace(false)
public final class FeatureEvent extends Event {

    @Label("Kind")
    @Description("hook, conflict or setting")
    String kind;

    @Label("Name")
    String name;

    @Label("State")
    String state;

    @Label("Usual")
    @Description("Whether it is as it is out of the box: the hook applied, the feature not standing aside, the setting at its default")
    boolean usual;

    static void write(List<ProfileFeature> features) {
        for (var feature : features) {
            var event = new FeatureEvent();
            event.kind = feature.kind();
            event.name = feature.name();
            event.state = feature.state();
            event.usual = feature.usual();
            event.commit();
        }
    }
}
