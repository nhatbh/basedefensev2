package com.nhatbh.basedefensev2.stage.utils;

import com.mojang.logging.LogUtils;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.forge.ForgeAdapter;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.IOException;

public final class SchematicPaster {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SchematicPaster() {}

    public static boolean pasteSchematic(ServerLevel level, BlockVector3 origin, InputStream schematicStream, String formatAlias) {
        if (level == null) return false;
        if (schematicStream == null) return false;

        try {
            World weWorld = ForgeAdapter.adapt(level);

            ClipboardFormat format = ClipboardFormats.findByAlias(formatAlias);
            if (format == null) {
                LOGGER.error("Unknown schematic format alias: {}", formatAlias);
                return false;
            }

            Clipboard clipboard;
            try (ClipboardReader reader = format.getReader(schematicStream)) {
                clipboard = reader.read();
            }

            try (EditSession editSession = WorldEdit.getInstance()
                .newEditSessionBuilder()
                .world(weWorld)
                .build()) {

                ClipboardHolder holder = new ClipboardHolder(clipboard);
                Operation operation = holder
                    .createPaste(editSession)
                    .to(origin)
                    .ignoreAirBlocks(false)
                    .build();

                Operations.complete(operation);
            }

            LOGGER.info("Successfully pasted schematic into dimension {}", level.dimension().location());
            return true;
        } catch (Exception e) {
            LOGGER.error("Schematic paste error", e);
            return false;
        }
    }
}
