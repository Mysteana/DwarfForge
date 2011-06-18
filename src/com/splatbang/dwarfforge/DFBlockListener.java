package com.splatbang.dwarfforge;

import java.lang.Runnable;
import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;


public class DFBlockListener extends BlockListener implements Runnable {

    private static final int INVALID_TASK = -1;
    private static final short SECS = 20;
    private static final short MINS = 60 * SECS;

    private static final short ZERO_DURATION = 0;
    private static final short TASK_DURATION = 20 * MINS;   // should be less than burn duration
    private static final short BURN_DURATION = 25 * MINS;   // must be less than max short

    private DwarfForge plugin;
    private ArrayList<Block> forges = new ArrayList<Block>();
    private int task = INVALID_TASK;


    public DFBlockListener(DwarfForge plugin) {
        this.plugin = plugin;
    }

    public void onBlockDamage(BlockDamageEvent event) {
        Block block = event.getBlock();

        if (isDwarfForge(block)) {
            if (isBurning(block))
                douse(block);
            else
                ignite(block);
        }
    }

    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (isDwarfForge(block)) {
            douse(block);
        }
    }

    private boolean isDwarfForge(Block block) {
        if (! (block.getState() instanceof Furnace))
            return false;

        Block below = block.getRelative(BlockFace.DOWN);
        return (below.getType() == Material.LAVA)
            || (below.getType() == Material.STATIONARY_LAVA);
    }

    private boolean isBurning(Block forge) {
        return (forge.getType() == Material.BURNING_FURNACE);
    }

    private ItemStack[] saveInventory(Furnace furnace) {
        Inventory inv = furnace.getInventory();
        ItemStack[] stuff = inv.getContents();
        inv.clear();
        return stuff;
    }

    private void restoreInventory(Furnace furnace, ItemStack[] stuff) {
        furnace.getInventory().setContents(stuff);
    }

    private void ignite(Block forge) {
        Furnace state = (Furnace) forge.getState();

        if (!isBurning(forge)) {
            Furnace priorState = state;
            ItemStack[] stuff = saveInventory(priorState);

            forge.setType(Material.BURNING_FURNACE);

            if (!forges.contains(forge))
                forges.add(forge);

            state = (Furnace) forge.getState();
            restoreInventory(state, stuff);
            state.setData(priorState.getData());
        }

        state.setBurnTime(BURN_DURATION);
        state.update();
    }

    private void douse(Block forge) {
        Furnace state = (Furnace) forge.getState();

        if (isBurning(forge)) {
            Furnace priorState = state;
            ItemStack[] stuff = saveInventory(priorState);

            forge.setType(Material.FURNACE);

            if (forges.contains(forge))
                forges.remove(forge);

            state = (Furnace) forge.getState();
            restoreInventory(state, stuff);
            state.setData(priorState.getData());
        }

        state.setBurnTime(ZERO_DURATION);
        state.update();
    }

    public void startTask() {
        task = plugin.getServer().getScheduler()
            .scheduleSyncRepeatingTask(plugin, this, 0, TASK_DURATION);
    }

    public void stopTask() {
        if (task != INVALID_TASK) {
            plugin.getServer().getScheduler().cancelTask(task);
            task = INVALID_TASK;
        }
    }

    public void run() {
        for (Block forge : forges) {
            ignite(forge);
        }
    }

}

