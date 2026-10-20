package de.alek.netherportalhelper.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.Strictness;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Portal libraries per connection. Independent of the live Minecraft client. */
public final class TargetStore {
    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.STRICT).setPrettyPrinting().create();
    private final Path file;

    public TargetStore(Path file) {
        this.file = file.toAbsolutePath();
    }

    public Optional<SavedTarget> load(String scope) throws IOException {
        Library library = loadLibrary(scope);
        return library.targets().stream().filter(target -> target.id().equals(library.activeId()))
                .findFirst().map(target -> new SavedTarget(target.source(), target.sourceDimension(),
                        target.destination(), target.destinationDimension()));
    }

    public void save(String scope, SavedTarget target) throws IOException {
        requireScope(scope);
        Map<String, Library> libraries = readLibraries();
        Library current = libraries.getOrDefault(scope, Library.empty());
        if (target == null) {
            libraries.put(scope, new Library(current.targets(), null));
        } else {
            UUID id = current.activeId() == null ? legacyId(scope) : current.activeId();
            PortalTarget previous = current.targets().stream().filter(saved -> saved.id().equals(id)).findFirst().orElse(null);
            PortalTarget converted = new PortalTarget(id, previous == null ? "Portal" : previous.name(),
                    target.source(), target.sourceDimension(), target.destination(), null, Direction.Axis.X);
            List<PortalTarget> updated = new ArrayList<>(current.targets());
            updated.removeIf(saved -> saved.id().equals(id));
            updated.add(converted);
            libraries.put(scope, new Library(updated, id));
        }
        writeLibraries(libraries);
    }

    public Library loadLibrary(String scope) throws IOException {
        if (scope == null) return Library.empty();
        return readLibraries().getOrDefault(scope, Library.empty());
    }

    public void saveLibrary(String scope, Library library) throws IOException {
        requireScope(scope);
        if (library == null) throw new IllegalArgumentException("Portal library is required");
        Map<String, Library> libraries = readLibraries();
        libraries.put(scope, library);
        writeLibraries(libraries);
    }

    private static void requireScope(String scope) {
        if (scope == null || scope.isBlank()) throw new IllegalArgumentException("Missing world or server identity");
    }

    private void writeLibraries(Map<String, Library> libraries) throws IOException {
        JsonObject document = new JsonObject();
        document.addProperty("version", 2);
        JsonObject scopes = new JsonObject();
        for (Map.Entry<String, Library> entry : libraries.entrySet()) {
            JsonObject library = new JsonObject();
            if (entry.getValue().activeId() != null) library.addProperty("activeId", entry.getValue().activeId().toString());
            JsonArray targets = new JsonArray();
            for (PortalTarget target : entry.getValue().targets()) {
                JsonObject saved = new JsonObject();
                saved.addProperty("id", target.id().toString());
                saved.addProperty("name", target.name());
                saved.add("source", point(target.source(), target.sourceDimension()));
                saved.add("calculatedDestination", point(target.calculatedDestination(), target.destinationDimension()));
                if (target.buildY() != null) saved.addProperty("buildY", target.buildY());
                saved.addProperty("axis", target.axis().name());
                targets.add(saved);
            }
            library.add("targets", targets);
            scopes.add(entry.getKey(), library);
        }
        document.add("targets", scopes);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "portal-targets-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(document), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Map<String, Library> readLibraries() throws IOException {
        if (!Files.exists(file)) return new LinkedHashMap<>();
        try {
            JsonObject document = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            int version = integer(document, "version");
            if ((version != 1 && version != 2) || !document.get("targets").isJsonObject()) {
                throw new IllegalArgumentException("Unsupported portal target file");
            }
            Map<String, Library> libraries = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : document.getAsJsonObject("targets").entrySet()) {
                requireScope(entry.getKey());
                JsonObject saved = entry.getValue().getAsJsonObject();
                if (version == 1) {
                    JsonObject source = saved.getAsJsonObject("source");
                    JsonObject destination = saved.getAsJsonObject("destination");
                    ResourceKey<Level> sourceDimension = dimension(source);
                    requireCounterpart(sourceDimension, dimension(destination));
                    PortalTarget migrated = new PortalTarget(legacyId(entry.getKey()), "Portal", position(source),
                            sourceDimension, position(destination), null, Direction.Axis.X);
                    libraries.put(entry.getKey(), new Library(List.of(migrated), migrated.id()));
                } else {
                    List<PortalTarget> targets = new ArrayList<>();
                    for (JsonElement element : saved.getAsJsonArray("targets")) {
                        JsonObject target = element.getAsJsonObject();
                        JsonObject source = target.getAsJsonObject("source");
                        JsonObject destination = target.getAsJsonObject("calculatedDestination");
                        ResourceKey<Level> sourceDimension = dimension(source);
                        requireCounterpart(sourceDimension, dimension(destination));
                        Integer buildY = target.has("buildY") && !target.get("buildY").isJsonNull()
                                ? integer(target, "buildY") : null;
                        targets.add(new PortalTarget(UUID.fromString(string(target, "id")), string(target, "name"),
                                position(source), sourceDimension, position(destination), buildY,
                                Direction.Axis.valueOf(string(target, "axis"))));
                    }
                    UUID activeId = saved.has("activeId") && !saved.get("activeId").isJsonNull()
                            ? UUID.fromString(string(saved, "activeId")) : null;
                    libraries.put(entry.getKey(), new Library(targets, activeId));
                }
            }
            return libraries;
        } catch (RuntimeException exception) {
            throw new IOException("Cannot read portal targets; original file preserved", exception);
        }
    }

    private static UUID legacyId(String scope) {
        return UUID.nameUUIDFromBytes(("netherportalhelper:portal:" + scope).getBytes(StandardCharsets.UTF_8));
    }

    private static void requireCounterpart(ResourceKey<Level> source, ResourceKey<Level> destination) {
        if (destination != PortalTracker.getCounterpartDimension(source)) throw new IllegalArgumentException("Invalid counterpart dimension");
    }

    private static JsonObject point(BlockPos position, ResourceKey<Level> dimension) {
        JsonObject object = new JsonObject();
        object.addProperty("dimension", dimension == Level.NETHER ? "nether" : "overworld");
        object.addProperty("x", position.getX());
        object.addProperty("y", position.getY());
        object.addProperty("z", position.getZ());
        return object;
    }

    private static BlockPos position(JsonObject point) {
        return new BlockPos(integer(point, "x"), integer(point, "y"), integer(point, "z"));
    }

    private static int integer(JsonObject point, String key) {
        var value = point.getAsJsonPrimitive(key);
        if (!value.isNumber()) throw new IllegalArgumentException("Coordinate must be a number");
        return value.getAsBigDecimal().intValueExact();
    }

    private static ResourceKey<Level> dimension(JsonObject point) {
        return switch (string(point, "dimension")) {
            case "nether" -> Level.NETHER;
            case "overworld" -> Level.OVERWORLD;
            default -> throw new IllegalArgumentException("Unsupported portal dimension");
        };
    }

    private static String string(JsonObject object, String key) {
        var value = object.getAsJsonPrimitive(key);
        if (!value.isString()) throw new IllegalArgumentException("Expected text for " + key);
        return value.getAsString();
    }

    public record Library(List<PortalTarget> targets, UUID activeId) {
        public Library {
            if (targets == null || targets.stream().anyMatch(target -> target == null)) {
                throw new IllegalArgumentException("Portal targets are required");
            }
            targets = List.copyOf(targets);
            HashSet<UUID> ids = new HashSet<>();
            for (PortalTarget target : targets) {
                if (!ids.add(target.id())) throw new IllegalArgumentException("Duplicate portal identity");
            }
            if (activeId != null && !ids.contains(activeId)) throw new IllegalArgumentException("Active portal must exist in the library");
        }

        public static Library empty() {
            return new Library(List.of(), null);
        }
    }

    public static String serverKey(String address) {
        String normalized = address.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(":25565")) normalized = normalized.substring(0, normalized.length() - 6);
        return identity("server:", normalized);
    }

    public static String worldKey(Path directory) {
        return identity("world:", directory.toAbsolutePath().normalize().toString());
    }

    private static String identity(String prefix, String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return prefix + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record SavedTarget(BlockPos source, ResourceKey<Level> sourceDimension,
                              BlockPos destination, ResourceKey<Level> destinationDimension) {
        public SavedTarget {
            if (source == null || destination == null || !PortalTracker.isPortalDimension(sourceDimension)
                    || destinationDimension != PortalTracker.getCounterpartDimension(sourceDimension)) {
                throw new IllegalArgumentException("Invalid portal pair");
            }
            source = source.immutable();
            destination = destination.immutable();
        }
    }
}
