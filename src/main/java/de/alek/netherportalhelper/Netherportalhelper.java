package de.alek.netherportalhelper;

import de.alek.netherportalhelper.config.ConfigManager;
import de.alek.netherportalhelper.hud.HUDOverlay;
import de.alek.netherportalhelper.util.PortalTracker;
import de.alek.netherportalhelper.util.TargetStore;
import de.alek.netherportalhelper.util.PortalManager;
import de.alek.netherportalhelper.screen.PortalScreen;
import de.alek.netherportalhelper.render.PortalWorldRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

public class Netherportalhelper implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("netherportalhelper");
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("netherportalhelper", "controls")
    );

    private static KeyMapping freezeKey;
    private static KeyMapping toggleKey;
    private static KeyMapping plannerKey;
    private static KeyMapping stopKey;

    @Override
    public void onInitializeClient() {
        try {
            ConfigManager.load(LOGGER);
            PortalManager.initialize(new TargetStore(FabricLoader.getInstance().getConfigDir().resolve("netherportalhelper-targets.json")));

            freezeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "key.netherportalhelper.freeze",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_P,
                    KEY_CATEGORY
            ));
            toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "key.netherportalhelper.toggle",
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_F6,
                    KEY_CATEGORY
            ));
            plannerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "key.netherportalhelper.planner", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, KEY_CATEGORY));

            stopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "key.netherportalhelper.stop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY));
            HudElementRegistry.attachElementAfter(
                    VanillaHudElements.BOSS_BAR,
                    Identifier.fromNamespaceAndPath("netherportalhelper", "main_hud"),
                    (graphics, tickCounter) -> HUDOverlay.render(graphics)
            );
            PortalWorldRenderer.register();

            ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
                try {
                    PortalManager.startSession(connectionScope(client));
                } catch (IOException exception) {
                    LOGGER.error("Could not restore portal target", exception);
                    if (client.player != null) client.player.sendOverlayMessage(
                            Component.translatable("msg.netherportalhelper.target_load_failed").withStyle(ChatFormatting.RED));
                }
                HUDOverlay.resetCompassState();
                HUDOverlay.showHints();
            });
            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
                PortalManager.endSession();
                HUDOverlay.resetCompassState();
            });

            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (PortalTracker.isActive() && (PortalTracker.getTargetPos() == null || PortalTracker.getTargetDimension() == null)) {
                    PortalTracker.clear();
                    HUDOverlay.resetCompassState();
                }

                while (plannerKey.consumeClick()) {
                    if (client.player != null && client.level != null) client.gui.setScreen(new PortalScreen(null));
                }

                while (toggleKey.consumeClick()) {
                    boolean wasVisible = ConfigManager.get().hudVisible;
                    ConfigManager.get().hudVisible = !wasVisible;
                    if (!ConfigManager.save(LOGGER)) {
                        ConfigManager.get().hudVisible = wasVisible;
                        if (client.player != null) client.player.sendOverlayMessage(Component.translatable("msg.netherportalhelper.save_failed"));
                        continue;
                    }
                    HUDOverlay.showHints();

                    if (client.player != null) {
                        client.player.sendOverlayMessage(
                                Component.translatable(
                                        ConfigManager.get().hudVisible ? "msg.netherportalhelper.enabled" : "msg.netherportalhelper.disabled"
                                ).withStyle(ConfigManager.get().hudVisible ? ChatFormatting.GREEN : ChatFormatting.RED)
                        );
                    }
                }

                while (freezeKey.consumeClick()) {
                    if (client.player == null || client.level == null) {
                        return;
                    }

                    ResourceKey<Level> currentDim = client.level.dimension();

                    if (!PortalTracker.isPortalDimension(currentDim)) {
                        client.player.sendOverlayMessage(Component.translatable("hud.netherportalhelper.not_possible").withStyle(ChatFormatting.RED));
                        continue;
                    }

                    try {
                        var captured = PortalManager.capture(client, Component.translatable("planner.netherportalhelper.default_name",
                                PortalManager.targets().size() + 1).getString());
                        String feedback = captured.duplicate()
                                ? (captured.positionFallback() ? "capture_duplicate_position" : "capture_duplicate")
                                : (captured.positionFallback() ? "capture_position" : "capture_portal");
                        client.player.sendOverlayMessage(Component.translatable("planner.netherportalhelper." + feedback)
                                .withStyle(captured.duplicate() ? ChatFormatting.GRAY : ChatFormatting.GREEN));
                    } catch (IOException exception) {
                        reportSaveFailure(client, exception);
                    } catch (IllegalArgumentException exception) {
                        client.player.sendOverlayMessage(Component.translatable("planner.netherportalhelper.invalid_input")
                                .withStyle(ChatFormatting.RED));
                    }
                }
                while (stopKey.consumeClick()) {
                    if (!PortalTracker.isActive() || client.player == null) continue;
                    try {
                        PortalManager.unlock();
                        client.player.sendOverlayMessage(Component.translatable("msg.netherportalhelper.unlocked")
                                .withStyle(ChatFormatting.GRAY));
                    } catch (IOException exception) {
                        reportSaveFailure(client, exception);
                    }
                }
            });
        } catch (RuntimeException exception) {
            LOGGER.error("Nether Portal Helper initialization failed", exception);
            throw exception;
        }
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    private static String connectionScope(Minecraft client) {
        if (client.getSingleplayerServer() != null) {
            return TargetStore.worldKey(client.getSingleplayerServer().getWorldPath(LevelResource.ROOT));
        }
        if (client.getCurrentServer() != null) return TargetStore.serverKey(client.getCurrentServer().ip);
        return null;
    }

    private static void reportSaveFailure(Minecraft client, IOException exception) {
        LOGGER.error("Could not save portal library", exception);
        if (client.player != null) client.player.sendOverlayMessage(
                Component.translatable("msg.netherportalhelper.target_save_failed").withStyle(ChatFormatting.RED));
    }

    public static String getFreezeKeyName() {
        if (freezeKey == null) return "?";
        return formatKeyName(freezeKey.getTranslatedKeyMessage().getString());
    }

    public static String getHideKeyName() {
        if (toggleKey == null) return "?";
        return formatKeyName(toggleKey.getTranslatedKeyMessage().getString());
    }

    public static String getPlannerKeyName() {
        return plannerKey == null ? "?" : formatKeyName(plannerKey.getTranslatedKeyMessage().getString());
    }

    private static String formatKeyName(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "?";
        }

        String normalized = raw
                .replace("Button ", "M")
                .replace("Mouse ", "M")
                .replace("Maustaste ", "M");

        if (normalized.length() > 12) {
            return normalized.substring(0, 12);
        }
        return normalized;
    }
}
