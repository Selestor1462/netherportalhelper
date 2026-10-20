package de.alek.netherportalhelper.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.Locale;

/** Explicit source-dimension format shared by manual input and the clipboard. */
public final class PortalCoordinates {
    private PortalCoordinates() { }

    public static Input parse(String text) {
        if (text == null) throw new IllegalArgumentException("Missing coordinates");
        String[] parts = text.strip().split("[\\s,;]+");
        if (parts.length != 4) throw new IllegalArgumentException("Expected dimension X Y Z");
        ResourceKey<Level> dimension = switch (parts[0].toLowerCase(Locale.ROOT)) {
            case "overworld", "minecraft:overworld" -> Level.OVERWORLD;
            case "nether", "minecraft:the_nether" -> Level.NETHER;
            default -> throw new IllegalArgumentException("Expected overworld or nether");
        };
        BlockPos source = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        validate(source);
        return new Input(source, dimension);
    }

    public static String format(PortalTarget target) {
        BlockPos position = target.source();
        return (target.sourceDimension() == Level.NETHER ? "nether" : "overworld") + " "
                + position.getX() + " " + position.getY() + " " + position.getZ();
    }

    private static void validate(BlockPos position) {
        if (Math.abs((long) position.getX()) > 29_999_984 || Math.abs((long) position.getZ()) > 29_999_984
                || position.getY() < -2032 || position.getY() > 2031) {
            throw new IllegalArgumentException("Coordinates are outside supported world bounds");
        }
    }

    public record Input(BlockPos source, ResourceKey<Level> sourceDimension) { }
}
