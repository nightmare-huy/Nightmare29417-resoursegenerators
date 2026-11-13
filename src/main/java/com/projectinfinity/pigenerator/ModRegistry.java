package com.projectinfinity.pigenerator;

import com.projectinfinity.pigenerator.block.GeneratorBlock;
import com.projectinfinity.pigenerator.blockentity.GeneratorBlockEntity;
import com.projectinfinity.pigenerator.item.ResourceFilterItem;
import com.projectinfinity.pigenerator.item.UpgradeItem;
import com.projectinfinity.pigenerator.menu.FilterMenu;
import com.projectinfinity.pigenerator.menu.GeneratorMenu;
import com.projectinfinity.pigenerator.upgrade.UpgradeType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** All registry entries of the mod: 8 blocks and their items, the filter, 12 upgrades, the block entity type, the two menus and the creative tab. */
public final class ModRegistry {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, PiGenerator.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, PiGenerator.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PiGenerator.MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, PiGenerator.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PiGenerator.MODID);

    /** generator_tier_1 ... generator_tier_8 */
    public static final Map<GeneratorTier, RegistryObject<GeneratorBlock>> GENERATORS;
    public static final Map<GeneratorTier, RegistryObject<BlockItem>> GENERATOR_ITEMS;

    static {
        Map<GeneratorTier, RegistryObject<GeneratorBlock>> blocks = new EnumMap<>(GeneratorTier.class);
        Map<GeneratorTier, RegistryObject<BlockItem>> items = new EnumMap<>(GeneratorTier.class);
        for (GeneratorTier tier : GeneratorTier.values()) {
            RegistryObject<GeneratorBlock> block = BLOCKS.register(tier.getRegistryName(), () -> new GeneratorBlock(tier));
            blocks.put(tier, block);
            items.put(tier, ITEMS.register(tier.getRegistryName(),
                    () -> new BlockItem(block.get(), new Item.Properties().rarity(tier.getRarity()))));
        }
        GENERATORS = Collections.unmodifiableMap(blocks);
        GENERATOR_ITEMS = Collections.unmodifiableMap(items);
    }

    /** Decides which outputs a generator makes, see {@link ResourceFilterItem}. */
    public static final RegistryObject<ResourceFilterItem> RESOURCE_FILTER =
            ITEMS.register("resource_filter", ResourceFilterItem::new);

    /** speed / efficiency / energy upgrades, tiers 1..4 of each, in creative tab order. */
    public static final List<RegistryObject<UpgradeItem>> UPGRADES;

    static {
        List<RegistryObject<UpgradeItem>> upgrades = new ArrayList<>();
        for (UpgradeType type : UpgradeType.values()) {
            for (int tier = 1; tier <= UpgradeType.TIERS; tier++) {
                int upgradeTier = tier;
                upgrades.add(ITEMS.register(UpgradeItem.registryName(type, tier), () -> new UpgradeItem(type, upgradeTier)));
            }
        }
        UPGRADES = Collections.unmodifiableList(upgrades);
    }

    /** One block entity type shared by all tiers; the tier is taken from the block. */
    public static final RegistryObject<BlockEntityType<GeneratorBlockEntity>> GENERATOR_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("generator", () -> BlockEntityType.Builder.of(
                    GeneratorBlockEntity::new,
                    GENERATORS.values().stream().map(RegistryObject::get).toArray(Block[]::new)
            ).build(null));

    /** Screen of a generator; the extra data is the tier level. */
    public static final RegistryObject<MenuType<GeneratorMenu>> GENERATOR_MENU =
            MENU_TYPES.register("generator", () -> IForgeMenuType.create(GeneratorMenu::new));

    /** Screen of the resource filter in the main hand; the extra data is its hotbar slot. */
    public static final RegistryObject<MenuType<FilterMenu>> FILTER_MENU =
            MENU_TYPES.register("resource_filter", () -> IForgeMenuType.create(FilterMenu::new));

    public static final RegistryObject<CreativeModeTab> CREATIVE_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.pi_generator"))
                    .icon(() -> new ItemStack(GENERATOR_ITEMS.get(GeneratorTier.TIER_8).get()))
                    .displayItems((parameters, output) -> {
                        GENERATOR_ITEMS.values().forEach(item -> output.accept(item.get()));
                        output.accept(RESOURCE_FILTER.get());
                        UPGRADES.forEach(upgrade -> output.accept(upgrade.get()));
                    })
                    .build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        MENU_TYPES.register(modBus);
        CREATIVE_TABS.register(modBus);
    }

    private ModRegistry() {
    }
}
