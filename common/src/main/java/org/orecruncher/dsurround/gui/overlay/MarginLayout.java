package org.orecruncher.dsurround.gui.overlay;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.orecruncher.dsurround.config.MarginPosition;
import org.orecruncher.dsurround.lib.di.Cacheable;

import java.util.function.Predicate;

/**
 * Places the compass and pocket watch in a side or corner of the screen, when they are shown there rather than
 * above the crosshair. Together they sit side by side (top and bottom center) or one above the other (the sides
 * and corners), compass first. Each overlay reports its size as it ticks (0 when not in the margin) and asks for
 * its center as it renders.
 */
@Cacheable
public final class MarginLayout {

    // Space between the screen's edge and the display, and between the compass and the watch
    static final float EDGE_GAP = 6F;
    static final float ITEM_GAP = 6F;
    // Bottom center sits above the hot bar and the health, armor and food bars
    static final float HOT_BAR_CLEARANCE = 60F;

    private float compassWidth;
    private float compassHeight;
    private float watchWidth;
    private float watchHeight;

    void setCompass(float width, float height) {
        this.compassWidth = width;
        this.compassHeight = height;
    }

    void setWatch(float width, float height) {
        this.watchWidth = width;
        this.watchHeight = height;
    }

    Placement place(MarginPosition position, float guiWidth, float guiHeight) {
        return place(position, guiWidth, guiHeight, this.compassWidth, this.compassHeight, this.watchWidth, this.watchHeight);
    }

    /**
     * The centers of the compass and the watch, in GUI pixels. A width of 0 means that one isn't shown.
     */
    static Placement place(MarginPosition position, float guiWidth, float guiHeight,
                           float compassWidth, float compassHeight, float watchWidth, float watchHeight) {
        float gap = compassWidth > 0 && watchWidth > 0 ? ITEM_GAP : 0F;
        boolean sideBySide = position.sideBySide();
        float groupWidth = sideBySide ? compassWidth + gap + watchWidth : Math.max(compassWidth, watchWidth);
        float groupHeight = sideBySide ? Math.max(compassHeight, watchHeight) : compassHeight + gap + watchHeight;

        float left = switch (position.horizontal()) {
            case -1 -> EDGE_GAP;
            case 1 -> guiWidth - EDGE_GAP - groupWidth;
            default -> (guiWidth - groupWidth) / 2F;
        };
        float top = switch (position.vertical()) {
            case -1 -> EDGE_GAP;
            case 1 -> guiHeight - (sideBySide ? HOT_BAR_CLEARANCE : EDGE_GAP) - groupHeight;
            default -> (guiHeight - groupHeight) / 2F;
        };

        if (sideBySide) {
            float y = top + groupHeight / 2F;
            return new Placement(left + compassWidth / 2F, y, left + compassWidth + gap + watchWidth / 2F, y);
        }

        // One above the other, each against the edge of the screen it is beside
        return new Placement(
                alignInColumn(position, left, groupWidth, compassWidth), top + compassHeight / 2F,
                alignInColumn(position, left, groupWidth, watchWidth), top + compassHeight + gap + watchHeight / 2F);
    }

    private static float alignInColumn(MarginPosition position, float left, float columnWidth, float width) {
        return switch (position.horizontal()) {
            case -1 -> left + width / 2F;
            case 1 -> left + columnWidth - width / 2F;
            default -> left + columnWidth / 2F;
        };
    }

    /**
     * Whether something matching is in the hot bar or the off hand
     */
    static boolean carries(Player player, Predicate<ItemStack> matches) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++)
            if (matches.test(inventory.getItem(slot)))
                return true;
        return matches.test(player.getOffhandItem());
    }

    record Placement(float compassX, float compassY, float watchX, float watchY) {
    }
}
