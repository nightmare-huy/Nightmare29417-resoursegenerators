package com.projectinfinity.pigenerator.client;

import com.projectinfinity.pigenerator.PiGenerator;
import com.projectinfinity.pigenerator.block.GeneratorBlock;
import com.projectinfinity.pigenerator.blockentity.GeneratorBlockEntity;
import com.projectinfinity.pigenerator.menu.GeneratorMenu;
import com.projectinfinity.pigenerator.upgrade.UpgradeType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generator screen: energy bar, FE per tick, state, progress of the current operation, the filter slot,
 * the three upgrade slots and the output buffer.
 *
 * <p>The positions match {@code generate_gui()} in {@code scripts/generate_resources.py}.</p>
 */
public class GeneratorScreen extends AbstractContainerScreen<GeneratorMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(PiGenerator.MODID, "textures/gui/generator.png");

    private static final int ENERGY_X = 9, ENERGY_Y = 18, ENERGY_WIDTH = 14, ENERGY_HEIGHT = 48;
    private static final int ENERGY_FILL_U = 176, ENERGY_FILL_V = 0;
    private static final int PROGRESS_X = 31, PROGRESS_Y = 54, PROGRESS_WIDTH = 60, PROGRESS_HEIGHT = 10;
    private static final int PROGRESS_FILL_U = 176, PROGRESS_FILL_V = 48;
    private static final int TEXT_X = 30, TEXT_Y = 18, TEXT_LINE = 11;
    /** Operations shorter than this many ticks are shown as a full progress bar (the bar would only flicker). */
    private static final int MIN_ANIMATED_TICKS = 4;
    private static final int TEXT_COLOUR = 0x404040;

    public GeneratorScreen(GeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelY = GeneratorMenu.INVENTORY_Y - 12;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        int energy = (int) Math.ceil((double) menu.getEnergy() * ENERGY_HEIGHT / Math.max(1, menu.getCapacity()));
        energy = Math.max(0, Math.min(ENERGY_HEIGHT, energy));
        if (energy > 0) { // filled from the bottom
            graphics.blit(TEXTURE, leftPos + ENERGY_X, topPos + ENERGY_Y + ENERGY_HEIGHT - energy,
                    ENERGY_FILL_U, ENERGY_FILL_V + ENERGY_HEIGHT - energy, ENERGY_WIDTH, energy);
        }

        int progress = progressWidth();
        if (progress > 0) {
            graphics.blit(TEXTURE, leftPos + PROGRESS_X, topPos + PROGRESS_Y, PROGRESS_FILL_U, PROGRESS_FILL_V,
                    progress, PROGRESS_HEIGHT);
        }
    }

    private int progressWidth() {
        int cycle = Math.max(1, menu.getCycleTicks());
        if (cycle / menu.getSpeed() < MIN_ANIMATED_TICKS) {
            return menu.getState() == GeneratorBlockEntity.State.WORKING ? PROGRESS_WIDTH : 0;
        }
        return Math.max(0, Math.min(PROGRESS_WIDTH, menu.getProgress() * PROGRESS_WIDTH / cycle));
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawTitle(graphics);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_COLOUR, false);

        int width = imageWidth - 6 - TEXT_X;
        graphics.drawString(font, fit(width, true, "gui.pi_generator.energy", menu.getEnergy(), menu.getCapacity()),
                TEXT_X, TEXT_Y, TEXT_COLOUR, false);
        graphics.drawString(font, fit(width, true, "gui.pi_generator.usage", menu.getEnergyPerTick()),
                TEXT_X, TEXT_Y + TEXT_LINE, TEXT_COLOUR, false);
        GeneratorBlockEntity.State state = menu.getState();
        graphics.drawString(font, Component.translatable("gui.pi_generator.state." + state.name().toLowerCase(Locale.ROOT)),
                TEXT_X, TEXT_Y + 2 * TEXT_LINE, stateColour(state), false);
    }

    /** The block name, or a short "Resource Generator (tier N)" where the name is wider than the window. */
    private void drawTitle(GuiGraphics graphics) {
        Component text = title;
        if (font.width(text) > imageWidth - 2 * titleLabelX) {
            text = Component.translatable("gui.pi_generator.title", menu.getTier().getLevel());
        }
        graphics.drawString(font, text, titleLabelX, titleLabelY, TEXT_COLOUR, false);
    }

    private static int stateColour(GeneratorBlockEntity.State state) {
        return switch (state) {
            case WORKING -> 0x1E7B1E;
            case BUFFER_FULL -> 0x9A5B00;
            case NO_ENERGY, NO_OUTPUTS -> 0xB02020;
        };
    }

    /** The text with full numbers ("1,234,567") if it fits into the width, with short ones ("1.23M") otherwise. */
    private Component fit(int width, boolean tryFull, String key, long... numbers) {
        Object[] arguments = new Object[numbers.length];
        for (int i = 0; i < numbers.length; i++) {
            arguments[i] = tryFull ? GeneratorBlock.formatNumber(numbers[i]) : shortNumber(numbers[i]);
        }
        Component text = Component.translatable(key, arguments);
        return tryFull && font.width(text) > width ? fit(width, false, key, numbers) : text;
    }

    /** 950 -> "950", 1234567 -> "1.23M", 8000000000 -> "8.00G" */
    static String shortNumber(long value) {
        if (value < 10_000) {
            return String.valueOf(value);
        }
        String[] units = {"k", "M", "G", "T"};
        double scaled = value;
        int unit = -1;
        while (scaled >= 1000 && unit < units.length - 1) {
            scaled /= 1000;
            unit++;
        }
        return String.format(Locale.ROOT, scaled >= 100 ? "%.0f%s" : scaled >= 10 ? "%.1f%s" : "%.2f%s", scaled, units[unit]);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        if (isHovering(ENERGY_X, ENERGY_Y, ENERGY_WIDTH, ENERGY_HEIGHT, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.pi_generator.tooltip.energy",
                    GeneratorBlock.formatNumber(menu.getEnergy()), GeneratorBlock.formatNumber(menu.getCapacity())));
            lines.add(Component.translatable("gui.pi_generator.usage", GeneratorBlock.formatNumber(menu.getEnergyPerTick()))
                    .withStyle(ChatFormatting.GRAY));
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_WIDTH, PROGRESS_HEIGHT, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.pi_generator.tooltip.progress", menu.getProgress(), menu.getCycleTicks()));
            lines.add(Component.translatable("gui.pi_generator.tooltip.speed", menu.getSpeed())
                    .withStyle(UpgradeType.SPEED.getColor()));
            lines.add(Component.translatable("gui.pi_generator.tooltip.output", menu.getOutputMultiplier())
                    .withStyle(UpgradeType.EFFICIENCY.getColor()));
        } else if (hoveredSlot != null && !hoveredSlot.hasItem()) {
            // what belongs into the empty filter / upgrade slots
            if (hoveredSlot.index == GeneratorMenu.filterSlot()) {
                lines.add(Component.translatable("gui.pi_generator.slot.filter"));
            }
            for (UpgradeType type : UpgradeType.values()) {
                if (hoveredSlot.index == GeneratorMenu.upgradeSlot(type)) {
                    lines.add(Component.translatable("gui.pi_generator.slot." + type.getName()).withStyle(type.getColor()));
                }
            }
        }
        if (!lines.isEmpty()) {
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }
}
