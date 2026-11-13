package com.projectinfinity.pigenerator;

import com.mojang.serialization.MapCodec;
import com.projectinfinity.pigenerator.block.EnergyStorageBlock;
import com.projectinfinity.pigenerator.block.GeneratorBlock;
import com.projectinfinity.pigenerator.blockentity.EnergyStorageBlockEntity;
import com.projectinfinity.pigenerator.blockentity.GeneratorBlockEntity;
import com.projectinfinity.pigenerator.item.CoreItem;
import com.projectinfinity.pigenerator.item.FilterData;
import com.projectinfinity.pigenerator.item.ResourceFilterItem;
import com.projectinfinity.pigenerator.item.UpgradeItem;
import com.projectinfinity.pigenerator.menu.EnergyStorageMenu;
import com.projectinfinity.pigenerator.menu.FilterMenu;
import com.projectinfinity.pigenerator.menu.GeneratorMenu;
import com.projectinfinity.pigenerator.recipe.CoreRecipesCondition;
import com.projectinfinity.pigenerator.recipe.RecipeSerializerExistsCondition;
import com.projectinfinity.pigenerator.upgrade.UpgradeType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * All registry entries of the mod: 8 generator blocks, the energy storage block and their items, the filter,
 * 12 upgrades, 3 cores, two data components, two block entity types, three menus, the two recipe conditions
 * and the creative tab.
 */
public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PiGenerator.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PiGenerator.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PiGenerator.MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, PiGenerator.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PiGenerator.MODID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, PiGenerator.MODID);
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, PiGenerator.MODID);

    /** generator_tier_1 ... generator_tier_8 */
    public static final Map<GeneratorTier, DeferredBlock<GeneratorBlock>> GENERATORS;
    public static final Map<GeneratorTier, DeferredItem<BlockItem>> GENERATOR_ITEMS;

    static {
        Map<GeneratorTier, DeferredBlock<GeneratorBlock>> blocks = new EnumMap<>(GeneratorTier.class);
        Map<GeneratorTier, DeferredItem<BlockItem>> items = new EnumMap<>(GeneratorTier.class);
        for (GeneratorTier tier : GeneratorTier.values()) {
            DeferredBlock<GeneratorBlock> block = BLOCKS.register(tier.getRegistryName(), () -> new GeneratorBlock(tier));
            blocks.put(tier, block);
            items.put(tier, ITEMS.register(tier.getRegistryName(),
                    () -> new BlockItem(block.get(), new Item.Properties().rarity(tier.getRarity()))));
        }
        GENERATORS = Collections.unmodifiableMap(blocks);
        GENERATOR_ITEMS = Collections.unmodifiableMap(items);
    }

    /** Data of a resource filter stack: {@code pi_generator:filter}, see {@link FilterData}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FilterData>> FILTER_DATA =
            DATA_COMPONENTS.registerComponentType("filter",
                    builder -> builder.persistent(FilterData.CODEC).networkSynchronized(FilterData.STREAM_CODEC));

    /** Decides which outputs a generator makes, see {@link ResourceFilterItem}. */
    public static final DeferredItem<ResourceFilterItem> RESOURCE_FILTER =
            ITEMS.register("resource_filter", ResourceFilterItem::new);

    /** speed / efficiency / energy upgrades, tiers 1..4 of each, in creative tab order. */
    public static final List<DeferredItem<UpgradeItem>> UPGRADES;

    static {
        List<DeferredItem<UpgradeItem>> upgrades = new ArrayList<>();
        for (UpgradeType type : UpgradeType.values()) {
            for (int tier = 1; tier <= UpgradeType.TIERS; tier++) {
                int upgradeTier = tier;
                upgrades.add(ITEMS.register(UpgradeItem.registryName(type, tier), () -> new UpgradeItem(type, upgradeTier)));
            }
        }
        UPGRADES = Collections.unmodifiableList(upgrades);
    }

    /** Components of the tier 6 / 7 / 8 generators for packs without Avaritia, see {@link CoreItem}. */
    public static final DeferredItem<CoreItem> CRYSTAL_CORE =
            ITEMS.register("crystal_core", () -> new CoreItem(6, Rarity.RARE));
    public static final DeferredItem<CoreItem> NEUTRON_CORE =
            ITEMS.register("neutron_core", () -> new CoreItem(7, Rarity.EPIC));
    public static final DeferredItem<CoreItem> INFINITY_CORE =
            ITEMS.register("infinity_core", () -> new CoreItem(8, Rarity.EPIC));

    /** Energy storage block: unlimited capacity, one kind, see {@link EnergyStorageBlock}. */
    public static final DeferredBlock<EnergyStorageBlock> ENERGY_STORAGE =
            BLOCKS.register("energy_storage", () -> new EnergyStorageBlock());
    public static final DeferredItem<BlockItem> ENERGY_STORAGE_ITEM = ITEMS.register("energy_storage",
            () -> new BlockItem(ENERGY_STORAGE.get(), new Item.Properties().rarity(Rarity.EPIC)));

    /**
     * Energy kept in an energy storage item: {@code pi_generator:energy}, a decimal string because the amount
     * has no upper limit, e.g. {@code "12345678901234567890"}.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BigInteger>> STORED_ENERGY =
            DATA_COMPONENTS.registerComponentType("energy", builder -> builder
                    .persistent(Codec.STRING.comapFlatMap(ModRegistry::parseEnergy, BigInteger::toString))
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.map(BigInteger::new, BigInteger::toString)));

    private static DataResult<BigInteger> parseEnergy(String text) {
        try {
            BigInteger value = new BigInteger(text.trim());
            return value.signum() >= 0 ? DataResult.success(value) : DataResult.error(() -> "Negative energy: " + text);
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Not a whole number: " + text);
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergyStorageBlockEntity>> ENERGY_STORAGE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("energy_storage", () -> BlockEntityType.Builder.of(
                    EnergyStorageBlockEntity::new, ENERGY_STORAGE.get()).build(null));

    /** One block entity type shared by all tiers; the tier is taken from the block. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneratorBlockEntity>> GENERATOR_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("generator", () -> BlockEntityType.Builder.of(
                    GeneratorBlockEntity::new,
                    GENERATORS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)
            ).build(null));

    /** Screen of a generator; the extra data is the tier level. */
    public static final DeferredHolder<MenuType<?>, MenuType<GeneratorMenu>> GENERATOR_MENU =
            MENU_TYPES.register("generator", () -> IMenuTypeExtension.create(GeneratorMenu::new));

    /** Screen of the resource filter in the main hand; the extra data is its hotbar slot. */
    public static final DeferredHolder<MenuType<?>, MenuType<FilterMenu>> FILTER_MENU =
            MENU_TYPES.register("resource_filter", () -> IMenuTypeExtension.create(FilterMenu::new));

    /** Screen of the energy storage block; the extra data (the block position) is not used. */
    public static final DeferredHolder<MenuType<?>, MenuType<EnergyStorageMenu>> ENERGY_STORAGE_MENU =
            MENU_TYPES.register("energy_storage", () -> IMenuTypeExtension.create(EnergyStorageMenu::new));

    /** Recipe conditions, used in the {@code "neoforge:conditions"} list of the recipe files. */
    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<RecipeSerializerExistsCondition>> RECIPE_SERIALIZER_EXISTS =
            CONDITIONS.register("recipe_serializer_exists", () -> RecipeSerializerExistsCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<CoreRecipesCondition>> CORE_RECIPES =
            CONDITIONS.register("core_recipes", () -> CoreRecipesCondition.CODEC);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.pi_generator"))
                    .icon(() -> new ItemStack(GENERATOR_ITEMS.get(GeneratorTier.TIER_8).get()))
                    .displayItems((parameters, output) -> {
                        GENERATOR_ITEMS.values().forEach(item -> output.accept(item.get()));
                        output.accept(ENERGY_STORAGE_ITEM.get());
                        output.accept(RESOURCE_FILTER.get());
                        UPGRADES.forEach(upgrade -> output.accept(upgrade.get()));
                        output.accept(CRYSTAL_CORE.get());
                        output.accept(NEUTRON_CORE.get());
                        output.accept(INFINITY_CORE.get());
                    })
                    .build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        DATA_COMPONENTS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        MENU_TYPES.register(modBus);
        CONDITIONS.register(modBus);
        CREATIVE_TABS.register(modBus);
    }

    /**
     * Energy input and item output of the generators, on all six sides and for the {@code null} side:
     * cables, Flux points, ME import / storage buses, pipes and hoppers all find them here.
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, GENERATOR_BLOCK_ENTITY.get(),
                (generator, side) -> generator.getEnergyStorage());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, GENERATOR_BLOCK_ENTITY.get(),
                (generator, side) -> generator.getOutputView());
        // the energy storage block answers per side: input, output or nothing (side switched off)
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ENERGY_STORAGE_BLOCK_ENTITY.get(),
                EnergyStorageBlockEntity::getEnergyView);
    }

    private ModRegistry() {
    }
}
