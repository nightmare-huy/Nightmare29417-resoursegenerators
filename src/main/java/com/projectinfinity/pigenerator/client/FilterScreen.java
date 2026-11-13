package com.projectinfinity.pigenerator.client;

import com.projectinfinity.pigenerator.PiGenerator;
import com.projectinfinity.pigenerator.menu.FilterMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Resource filter screen: 18 ghost slots with the listed items and a button that switches between
 * "everything except the listed" and "only the listed".
 *
 * <p>The button is drawn from the screen's own sheet, see {@code filter_button()} in
 * {@code scripts/generate_resources.py}.</p>
 */
public class FilterScreen extends AbstractContainerScreen<FilterMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(PiGenerator.MODID, "textures/gui/resource_filter.png");
    private static final int BUTTON_X = 8, BUTTON_Y = 58, BUTTON_WIDTH = 160, BUTTON_HEIGHT = 20;
    /** Button sprites below the window: except, except (hovered), only, only (hovered). */
    private static final int BUTTON_V = 174;
    /** The left part of the button holds the mode icon, the label is centred in the rest. */
    private static final int BUTTON_ICON_WIDTH = 20;
    private static final int TEXT_COLOUR = 0x404040;

    private Button modeButton;
    /** The mode the button currently shows; {@code null} before the first update. */
    private Boolean shownMode;

    public FilterScreen(FilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 174;
        inventoryLabelY = FilterMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        modeButton = addRenderableWidget(new ModeButton(leftPos + BUTTON_X, topPos + BUTTON_Y));
        shownMode = null;
        updateButton();
    }

    /** The server switches the mode and sends the changed filter stack back, see {@link FilterMenu#clickMenuButton}. */
    private void toggleMode() {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FilterMenu.BUTTON_TOGGLE_MODE);
        }
    }

    private void updateButton() {
        boolean whitelist = menu.isWhitelist();
        if (shownMode == null || shownMode != whitelist) {
            shownMode = whitelist;
            modeButton.setMessage(Component.translatable(
                    whitelist ? "gui.pi_generator.filter.only" : "gui.pi_generator.filter.except"));
            modeButton.setTooltip(Tooltip.create(Component.translatable(
                    whitelist ? "gui.pi_generator.filter.only_hint" : "gui.pi_generator.filter.except_hint")));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButton();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT_COLOUR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_COLOUR, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        // how the ghost slots work, shown over an empty one
        if (menu.getCarried().isEmpty() && hoveredSlot != null && hoveredSlot.index < FilterMenu.GHOST_SLOTS
                && !hoveredSlot.hasItem()) {
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.pi_generator.filter.slot_add"),
                    Component.translatable("gui.pi_generator.filter.slot_shift").withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.pi_generator.filter.slot_remove").withStyle(ChatFormatting.GRAY)),
                    mouseX, mouseY);
        }
    }

    /** The mode button: its plate and icon come from the sheet and follow the mode of the filter. */
    private class ModeButton extends Button {
        ModeButton(int x, int y) {
            super(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, Component.empty(), button -> toggleMode(), DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int state = (menu.isWhitelist() ? 2 : 0) + (isHoveredOrFocused() ? 1 : 0);
            graphics.blit(TEXTURE, getX(), getY(), 0, BUTTON_V + state * BUTTON_HEIGHT, width, height);
            graphics.drawCenteredString(font, getMessage(), getX() + (BUTTON_ICON_WIDTH + width) / 2,
                    getY() + (height - 8) / 2, 0xFFFFFF);
        }
    }
}
