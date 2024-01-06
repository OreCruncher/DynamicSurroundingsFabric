package org.orecruncher.dsurround.commands;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;

public class ScriptCommandHandler {

    public static Component execute(String script) {
        var result = ContainerManager.resolve(IConditionEvaluator.class).eval(new Script(script));
        return Component.literal(result.toString());
    }
}
