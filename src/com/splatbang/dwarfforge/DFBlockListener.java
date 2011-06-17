package com.splatbang.dwarfforge;

import java.lang.Runnable;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class DFBlockListener extends BlockListener implements Runnable {

    private DwarfForge plugin;

    public DFBlockListener(DwarfForge plugin) {
        this.plugin = plugin;
    }

    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
    }

    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
    }

    public static boolean isLavaBelow(Block block) {
        Block below = block.getRelative(BlockFace.DOWN);
        return (below.getType() == Material.LAVA) || (below.getType() == Material.STATIONARY_LAVA);
    }

    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!event.isCancelled() && block.getType() == Material.FURNACE) {
            if (isLavaBelow(block)) {
                plugin.logInfo(player.getName() + " placed furnace above lava.");
            }
            else {
                plugin.logInfo(player.getName() + " placed furnace above something other than lava.");
            }
        }
    }

    public void run() {
        //plugin.logInfo("DFBlockListener furnace task fired.");
    }

}

