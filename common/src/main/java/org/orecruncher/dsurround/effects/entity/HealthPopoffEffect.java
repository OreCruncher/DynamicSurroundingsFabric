package org.orecruncher.dsurround.effects.entity;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;

/**
 * Pops a number off an entity when its health changes: red for damage, green for healing, and for a heavy blow (at
 * least {@link #CRITICAL_SHARE} of its health at most) a comic book word in gold above it. Not for the player's own
 * body in first person. Drawn by {@link HealthPopoffs}.
 * <p>
 * Health is synced to the client for every living entity, so this works for mobs and other players alike. It keeps
 * watching while the entity dies, so the killing blow shows too.
 */
public class HealthPopoffEffect extends EntityEffectBase {

    private static final IRandomizer RANDOM = Randomizer.current();

    // A blow of at least this share of the entity's maximum health gets a crit word
    static final float CRITICAL_SHARE = 0.4F;
    // The crit words: dsurround.critword.0 to one less than this
    static final int CRIT_WORDS = 84;
    static final String CRIT_WORD_PREFIX = "dsurround.critword.";
    // Where the number starts: this far above the top of the entity, and the word this far above the number, in blocks
    private static final double ABOVE = 0.5D;
    private static final double WORD_ABOVE = 0.5D;

    private static final int DAMAGE_COLOR = ColorPalette.MC_RED.getValue();
    private static final int HEAL_COLOR = ColorPalette.MC_GREEN.getValue();
    private static final int CRITICAL_COLOR = ColorPalette.MC_GOLD.getValue();

    private final Configuration.EntityEffects config;
    private float lastHealth;

    public HealthPopoffEffect(Configuration.EntityEffects config) {
        this.config = config;
    }

    @Override
    public void activate(final EntityEffectInfo info) {
        this.lastHealth = info.getEntity().getHealth();
    }

    @Override
    public void tick(final EntityEffectInfo info) {
        this.update(info);
    }

    @Override
    public void tickDying(final EntityEffectInfo info) {
        // The killing blow lands on the tick the entity starts dying
        this.update(info);
    }

    private void update(final EntityEffectInfo info) {
        var entity = info.getEntity();
        float health = entity.getHealth();
        if (health == this.lastHealth)
            return;
        float change = health - this.lastHealth;
        this.lastHealth = health;

        if (info.isCurrentPlayer(entity) && GameUtils.isFirstPersonView())
            return;

        var popoff = Change.of(change, entity.getMaxHealth());
        var box = entity.getBoundingBox();
        double y = box.maxY + ABOVE;
        float driftX = RANDOM.nextFloat() * 2F - 1F;
        float driftZ = RANDOM.nextFloat() * 2F - 1F;
        HealthPopoffs.add(String.valueOf(popoff.amount()), popoff.heal() ? HEAL_COLOR : DAMAGE_COLOR,
                entity.getX(), y, entity.getZ(), driftX, driftZ);
        if (popoff.critical() && this.config.showCritWords)
            HealthPopoffs.add(critWord(), CRITICAL_COLOR, entity.getX(), y + WORD_ABOVE, entity.getZ(), driftX, driftZ);
    }

    private static String critWord() {
        return I18n.get(CRIT_WORD_PREFIX + RANDOM.nextInt(CRIT_WORDS)) + "!";
    }

    /**
     * What a change of health shows: a whole number of at least 1, whether it is healing, and whether it is a heavy
     * enough blow for a crit word.
     */
    record Change(int amount, boolean heal, boolean critical) {

        static Change of(float change, float maxHealth) {
            int rounded = Mth.ceil(change);
            int amount = Math.max(1, Math.abs(rounded));
            boolean heal = rounded > 0;
            return new Change(amount, heal, !heal && amount >= maxHealth * CRITICAL_SHARE);
        }
    }
}
