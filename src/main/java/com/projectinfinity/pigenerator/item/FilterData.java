package com.projectinfinity.pigenerator.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * What a {@link ResourceFilterItem resource filter} holds, stored on the item stack as the data component
 * {@code pi_generator:filter}: <pre>{whitelist: true, items: ["minecraft:coal", ...]}</pre>
 *
 * @param whitelist {@code true}: only the listed items are generated, {@code false}: everything except them
 * @param items     registry ids of the listed items, in the order they were added
 */
public record FilterData(boolean whitelist, List<ResourceLocation> items) {
    /** A new filter: "everything except" with an empty list, i.e. it lets everything through. */
    public static final FilterData EMPTY = new FilterData(false, List.of());

    public static final Codec<FilterData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("whitelist", false).forGetter(FilterData::whitelist),
            ResourceLocation.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(FilterData::items)
    ).apply(instance, FilterData::new));

    public static final StreamCodec<ByteBuf, FilterData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, FilterData::whitelist,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), FilterData::items,
            FilterData::new);

    public FilterData {
        items = List.copyOf(items); // components must be immutable
    }

    public FilterData withWhitelist(boolean newWhitelist) {
        return new FilterData(newWhitelist, items);
    }

    public FilterData withItems(List<ResourceLocation> newItems) {
        return new FilterData(whitelist, newItems);
    }
}
