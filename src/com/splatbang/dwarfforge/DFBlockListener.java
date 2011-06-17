package com.splatbang.dwarfforge;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.material.MaterialData;

public class DFBlockListener extends BlockListener {

    private DwarfForge plugin;

    private static final short SECS = 20;
    private static final short MINS = 60 * SECS;
    private static final short DURATION = 25 * MINS;    // < 32767: max short

    public DFBlockListener(DwarfForge plugin) {
        this.plugin = plugin;
    }

    public void onBlockDamage(BlockDamageEvent event) {
        Block block = event.getBlock();

        if (isDwarfForge(block)) {
            ((Furnace) block.getState()).setBurnTime(DURATION);
        }
        
    }

    private boolean isDwarfForge(Block block) {
        if (! (block.getState() instanceof Furnace))
            return false;

        Block below = block.getRelative(BlockFace.DOWN);
        return (below.getType() == Material.LAVA)
            || (below.getType() == Material.STATIONARY_LAVA);
    }

}

