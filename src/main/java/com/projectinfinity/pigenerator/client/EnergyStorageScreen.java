package com.projectinfinity.pigenerator.client;

import com.projectinfinity.pigenerator.PiGenerator;
import com.projectinfinity.pigenerator.block.EnergyStorageBlock;
import com.projectinfinity.pigenerator.block.GeneratorBlock;
import com.projectinfinity.pigenerator.energy.EnergyFormat;
import com.projectinfinity.pigenerator.energy.SideMode;
import com.projectinfinity.pigenerator.menu.EnergyStorageMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.math.BigInteger;

/**
 * Energy storage screen: a dark display with the stored energy and the energy received / given out per tick,
 * and one button per side that switches it between input, output and off.
 *
 * <p>The positions match {@code energy_storage_sheet()} in {@code scripts/generate_resources.py}.</p>
 */
public class EnergyStorageScreen extends AbstractContainerScreen<EnergyStorageMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(PiGenerator.MODID, "textures/gui/energy_storage.png");
    /** Text lines of the display; they must stay inside ENERGY_STORAGE_DISPLAY of the script. */
    private static final int TEXT_X = 12, TEXT_Y = 21, TEXT_LINE = 10, TEXT_WIDTH = 152;
    private static final int BUTTON_X = 7, BUTTON_Y = 58, BUTTON_WIDTH = 80, BUTTON_HEIGHT = 20, BUTTON_GAP = 2;
    private static final int TEXT_COLOUR = 0x404040;
    private static final int STORED_COLOUR = 0xFFE066, RECEIVED_COLOUR = 0x55E0F0, SENT_COLOUR = 0xFFB030;
    /** Rows of buttons: up / down, north / south, west / east. */
    private static final Direction[] BUTTON_ORDER = {
            Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private final Button[] buttons = new Button[BUTTON_ORDER.length];
    /** The modes the buttons currently show; {@code null} before the first update. */
    private final SideMode[] shownModes = new SideMode[BUTTON_ORDER.length];

    public EnergyStorageScreen(EnergyStorageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 130;
    }

    @Override
    protected void init() {
        super.init();
        for (int index = 0; index < BUTTON_ORDER.length; index++) {
            Direction side = BUTTON_ORDER[index];
            int x = leftPos + BUTTON_X + (index % 2) * (BUTTON_WIDTH + BUTTON_GAP);
            int y = topPos + BUTTON_Y + (index / 2) * (BUTTON_HEIGHT + BUTTON_GAP);
            buttons[index] = addRenderableWidget(Button.builder(Component.empty(), button -> switchSide(side))
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.translatable("gui.pi_generator.energy_storage.side_hint")))
                    .build());
            shownModes[index] = null;
        }
        updateButtons();
    }

    /** The server switches the side and sends the new modes back, see {@link EnergyStorageMenu#clickMenuButton}. */
    private void switchSide(Direction side) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, side.ordinal());
        }
    }

    private void updateButtons() {
        for (int index = 0; index < BUTTON_ORDER.length; index++) {
            SideMode mode = menu.getMode(BUTTON_ORDER[index]);
            if (shownModes[index] != mode) {
                shownModes[index] = mode;
                buttons[index].setMessage(EnergyStorageBlock.describeSide(BUTTON_ORDER[index], mode));
            }
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
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

        BigInteger energy = menu.getEnergy();
        Component stored = Component.translatable("gui.pi_generator.energy_storage.stored", EnergyFormat.full(energy));
        if (font.width(stored) > TEXT_WIDTH) {
            stored = Component.translatable("gui.pi_generator.energy_storage.stored", EnergyFormat.compact(energy));
        }
        graphics.drawString(font, stored, TEXT_X, TEXT_Y, STORED_COLOUR, false);
        graphics.drawString(font, rate("gui.pi_generator.energy_storage.received", menu.getReceivedPerTick()),
                TEXT_X, TEXT_Y + TEXT_LINE, RECEIVED_COLOUR, false);
        graphics.drawString(font, rate("gui.pi_generator.energy_storage.sent", menu.getSentPerTick()),
                TEXT_X, TEXT_Y + 2 * TEXT_LINE, SENT_COLOUR, false);
    }

    /** The line with the full number ("1,234,567") if it fits into the display, with a short one otherwise. */
    private Component rate(String key, long value) {
        Component text = Component.translatable(key, GeneratorBlock.formatNumber(value));
        return font.width(text) > TEXT_WIDTH ? Component.translatable(key, GeneratorScreen.shortNumber(value)) : text;
    }
}
