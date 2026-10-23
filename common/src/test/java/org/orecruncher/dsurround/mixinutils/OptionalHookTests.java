package org.orecruncher.dsurround.mixinutils;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class OptionalHookTests {

    private static final String TARGET = "net/minecraft/client/renderer/LevelRenderer";

    // A class with a method calling the named methods of the class
    private static ClassNode classCalling(String... names) {
        var node = new ClassNode();
        node.name = TARGET;
        var method = new MethodNode(Opcodes.ACC_PUBLIC, "renderLevel", "()V", null, null);
        for (var name : names)
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, name, "()V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(method);
        return node;
    }

    @Test
    void aHandlerRenamedByMixinIsFound() {
        assertTrue(OptionalHook.calls(classCalling("modifyExpressionValue$zza000$dsurround$tintSkyColor"), "dsurround$tintSkyColor"));
        assertTrue(OptionalHook.calls(classCalling("dsurround$tintSkyColor"), "dsurround$tintSkyColor"));
    }

    @Test
    void aHandlerNotCalledIsMissing() {
        assertFalse(OptionalHook.calls(classCalling("renderSky"), "dsurround$tintSkyColor"));
        // Only the end of the name counts: another handler sharing the start of it isn't this one
        assertFalse(OptionalHook.calls(classCalling("handler$zza000$dsurround$stormRainLevel"), "dsurround$stormRainLevelDrawn"));
    }

    @Test
    void aHandlerWithSugarIsCalledThroughItsBridge() {
        // The game's code calls MixinExtras' bridge, which calls the handler
        var node = classCalling("wrapOperation$zhh000$dsurround$afterSectionLayer$mixinextras$bridge$139");
        assertTrue(OptionalHook.calls(node, "dsurround$afterSectionLayer"));
    }

    @Test
    void theBridgeCallingTheHandlerDoesntCount() {
        // MixinExtras makes the bridge even when the injection found nowhere to go
        var node = classCalling("renderSectionLayer");
        var bridge = new MethodNode(Opcodes.ACC_PRIVATE, "wrapOperation$zhh000$dsurround$afterSectionLayer$mixinextras$bridge$139", "()V", null, null);
        bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, "wrapOperation$zhh000$dsurround$afterSectionLayer", "()V", false));
        bridge.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(bridge);
        assertFalse(OptionalHook.calls(node, "dsurround$afterSectionLayer"));
    }

    @Test
    void callsToAnotherClassDontCount() {
        var node = classCalling();
        node.methods.getFirst().instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "other/Owner", "dsurround$tintSkyColor", "()V", false));
        assertFalse(OptionalHook.calls(node, "dsurround$tintSkyColor"));
    }

    @Test
    void aHookIsMissingIfAnyOfItsHandlersIs() {
        var particleEngine = classCalling("handler$zzb000$dsurround$markWindBlown");
        var particle = classCalling("localvar$zzc000$dsurround$driftX");
        var targets = Map.of(
                "org.orecruncher.dsurround.mixins.core.MixinParticleEngine", particleEngine,
                "org.orecruncher.dsurround.mixins.core.MixinParticle", particle);
        assertEquals(OptionalHook.Status.MISSING, OptionalHook.statusOf(OptionalHook.WIND_BLOWN_PARTICLES, targets::get));
    }

    @Test
    void aHookWithAllItsHandlersCalledApplied() {
        var particleEngine = classCalling("handler$zzb000$dsurround$markWindBlown");
        var particle = classCalling("localvar$zzc000$dsurround$driftX", "localvar$zzc001$dsurround$driftZ");
        var targets = Map.of(
                "org.orecruncher.dsurround.mixins.core.MixinParticleEngine", particleEngine,
                "org.orecruncher.dsurround.mixins.core.MixinParticle", particle);
        assertEquals(OptionalHook.Status.APPLIED, OptionalHook.statusOf(OptionalHook.WIND_BLOWN_PARTICLES, targets::get));
    }

    @Test
    void aHookWhoseClassWasntSeenIsUnknown() {
        assertEquals(OptionalHook.Status.UNKNOWN, OptionalHook.statusOf(OptionalHook.SKY_TINT, name -> null));
    }

    @Test
    void everyHandlerIsAnOptionalInjectorInItsMixin() throws Exception {
        // A handler renamed or moved would quietly be reported missing; a required one would never be
        for (var hook : OptionalHook.values()) {
            for (var site : hook.sites()) {
                var mixin = Class.forName(site.mixinClassName(), false, getClass().getClassLoader());
                for (var name : site.handlers()) {
                    var handler = Arrays.stream(mixin.getDeclaredMethods())
                            .filter(m -> m.getName().equals(name))
                            .findFirst();
                    assertTrue(handler.isPresent(), () -> site + ": " + name);
                    assertEquals(0, require(handler.get()), () -> site + ": " + name);
                }
            }
        }
    }

    // The require of the method's injector annotation
    private static int require(Method method) throws Exception {
        for (Annotation annotation : method.getDeclaredAnnotations()) {
            try {
                return (int) annotation.annotationType().getMethod("require").invoke(annotation);
            } catch (NoSuchMethodException ignored) {
                // Not an injector
            }
        }
        throw new AssertionError("No injector annotation on " + method);
    }

    @Test
    void everyHookAndItsDsconflictsMessageIsTranslated() throws Exception {
        try (var stream = OptionalHookTests.class.getResourceAsStream("/assets/dsurround/lang/en_us.json")) {
            assertNotNull(stream);
            var lang = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var hook : OptionalHook.values())
                assertTrue(lang.has(hook.translationKey()), hook::name);
            assertTrue(lang.has("dsurround.command.dsconflicts.hook"));
        }
    }

    @Test
    void nothingIsReportedBeforeTheCheck() {
        for (var hook : OptionalHook.values()) {
            assertEquals(OptionalHook.Status.UNKNOWN, hook.status(), hook::name);
            assertTrue(hook.describe().isEmpty(), hook::name);
        }
    }
}
