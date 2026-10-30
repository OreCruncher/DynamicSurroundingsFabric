package org.orecruncher.dsurround.lib.config.compat;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FittingMultiLineTextWidget;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Asks whether to apply a preset, listing what it would change. The list scrolls: a preset can change dozens of
 * settings, more than vanilla's confirm screen has room for.
 */
public class PresetConfirmScreen extends Screen {

    private static final int SPACING = 8;
    private static final int LIST_WIDTH = 320;
    private static final int BUTTON_WIDTH = 150;

    private final Component message;
    private final @Nullable Component changes;
    private final BooleanConsumer callback;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private MultiLineTextWidget messageWidget;
    private @Nullable FittingMultiLineTextWidget changesWidget;

    /**
     * @param message  shown above the list
     * @param changes  the list, a line each, or null if the preset would change nothing (then only Back is offered)
     * @param callback given true if the player chose to apply the preset, false otherwise
     */
    public PresetConfirmScreen(Component title, Component message, @Nullable Component changes, BooleanConsumer callback) {
        super(title);
        this.message = message;
        this.changes = changes;
        this.callback = callback;
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(this.title, this.font);

        var contents = this.layout.addToContents(LinearLayout.vertical().spacing(SPACING));
        contents.defaultCellSetting().alignHorizontallyCenter();
        this.messageWidget = contents.addChild(new MultiLineTextWidget(this.message, this.font).setCentered(true));
        if (this.changes != null)
            this.changesWidget = contents.addChild(new FittingMultiLineTextWidget(0, 0, LIST_WIDTH, 0, this.changes, this.font));

        var buttons = this.layout.addToFooter(LinearLayout.horizontal().spacing(SPACING));
        if (this.changes != null) {
            buttons.addChild(Button.builder(CommonComponents.GUI_YES, b -> this.callback.accept(true)).width(BUTTON_WIDTH).build());
            buttons.addChild(Button.builder(CommonComponents.GUI_NO, b -> this.callback.accept(false)).width(BUTTON_WIDTH).build());
        } else {
            // Nothing to apply: only a way back
            buttons.addChild(Button.builder(CommonComponents.GUI_BACK, b -> this.callback.accept(false)).width(BUTTON_WIDTH).build());
        }

        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        var width = Math.min(LIST_WIDTH, this.width - 2 * SPACING);
        this.messageWidget.setMaxWidth(width);
        // The list takes the room the message leaves, and no more than its lines need. minimizeHeight() only shrinks
        // a list that fits without a scroll bar, so it is given all the room first.
        if (this.changesWidget != null) {
            this.changesWidget.setWidth(width);
            var room = this.layout.getContentHeight() - this.messageWidget.getHeight() - 3 * SPACING;
            this.changesWidget.setHeight(Math.max(0, room));
            this.changesWidget.minimizeHeight();
        }
        this.layout.arrangeElements();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        this.callback.accept(false);
    }

    @Override
    public Component getNarrationMessage() {
        var narration = CommonComponents.joinForNarration(super.getNarrationMessage(), this.message);
        return this.changes == null ? narration : CommonComponents.joinForNarration(narration, this.changes);
    }
}
