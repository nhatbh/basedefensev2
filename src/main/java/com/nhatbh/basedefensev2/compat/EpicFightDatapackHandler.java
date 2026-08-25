package com.nhatbh.basedefensev2.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Ensures that external boss datapacks (such as bosses_of_miracle's fix_efn.zip)
 * are properly loaded and enabled in the server's PackRepository so Epic Fight
 * mob patches and capabilities attach to boss entities correctly.
 */
@Mod.EventBusSubscriber(modid = "basedefensev2")
public class EpicFightDatapackHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        if (server == null) return;

        try {
            Path datapackDir = server.getWorldPath(LevelResource.DATAPACK_DIR);
            Path fixEfnDir = datapackDir.resolve("fix_efn");

            if (Files.exists(fixEfnDir)) {
                PackRepository repository = server.getPackRepository();
                repository.reload();

                List<String> selectedPacks = new ArrayList<>(repository.getSelectedIds());
                String packId = "file/fix_efn";
                if (!selectedPacks.contains(packId)) {
                    if (repository.getPack(packId) != null) {
                        selectedPacks.add(packId);
                        repository.setSelected(selectedPacks);
                        LOGGER.info("[BaseDefenseV2] Auto-enabled Bosses of Miracle Epic Fight datapack ({})", packId);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[BaseDefenseV2] Could not verify/enable fix_efn datapack: {}", e.getMessage());
        }
    }
}
