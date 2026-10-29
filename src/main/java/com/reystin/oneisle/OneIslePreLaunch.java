package com.reystin.oneisle;

import com.reystin.oneisle.client.IslandDraft;
import com.reystin.oneisle.client.ShareCode;
import com.reystin.oneisle.client.WorldPackBuilder;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dedicated servers build the world's pack list before mod initializers run, so a pack
 * written from SERVER_STARTING is only seen on the second boot. This pre-launch pass
 * writes it into the world folder first, letting the generated pack shape the very
 * first world creation.
 */
public final class OneIslePreLaunch implements PreLaunchEntrypoint {
    private static final Logger LOGGER = LoggerFactory.getLogger(OneIslePreLaunch.class);

    @Override
    public void onPreLaunch() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) {
            return;
        }
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("oneisle.json");
        if (!Files.exists(configPath)) {
            return;
        }
        try {
            Path gameDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
            Path datapackDir = universeDir(gameDir).resolve(levelName(gameDir)).resolve("datapacks");
            Path targetPack = datapackDir.resolve(WorldPackBuilder.FILE_NAME);
            if (Files.exists(targetPack)) {
                return;
            }
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            IslandDraft draft = ShareCode.decodeJson(json);
            Files.createDirectories(datapackDir);
            WorldPackBuilder.export(datapackDir, draft);
            LOGGER.info("OneIsle: Automatically generated island datapack at {} from config/oneisle.json", targetPack);
        } catch (Exception e) {
            LOGGER.error("OneIsle: Failed to generate island datapack from config/oneisle.json", e);
        }
    }

    private static Path universeDir(Path gameDir) {
        String[] args = ProcessHandle.current().info().arguments().orElseGet(() -> new String[0]);
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--universe") && i + 1 < args.length) {
                return gameDir.resolve(args[i + 1]).normalize();
            }
            if (arg.startsWith("--universe=")) {
                return gameDir.resolve(arg.substring("--universe=".length())).normalize();
            }
        }
        return gameDir;
    }

    private static String levelName(Path gameDir) {
        Path propertiesPath = gameDir.resolve("server.properties");
        if (!Files.exists(propertiesPath)) {
            return "world";
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(propertiesPath)) {
            properties.load(in);
        } catch (Exception e) {
            LOGGER.warn("OneIsle: Could not read server.properties, assuming level-name=world", e);
            return "world";
        }
        String levelName = properties.getProperty("level-name");
        return levelName == null || levelName.isBlank() ? "world" : levelName;
    }
}
