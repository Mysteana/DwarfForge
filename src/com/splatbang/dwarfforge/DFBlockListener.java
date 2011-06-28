package com.splatbang.dwarfforge;

import java.lang.Runnable;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.material.FurnaceAndDispenser;
import org.bukkit.plugin.PluginManager;


public class DFBlockListener extends BlockListener implements Runnable {

    private static final int INVALID_TASK = -1;
    private static final short SECS = 20;
    private static final short MINS = 60 * SECS;

    private static final short ZERO_DURATION = 0;
    private static final short TASK_DURATION = 2 * SECS;   // should be less than burn duration
    private static final short BURN_DURATION =25 * MINS;   // must be less than max short

    private static final int RAW_SLOT = 0;
    private static final int FUEL_SLOT = 1;
    private static final int REFINED_SLOT = 2;

    private static final List<BlockFace> dirs = Arrays.asList(
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    private static final Material[] smeltables = {
        Material.DIAMOND_ORE,
        Material.IRON_ORE,
        Material.GOLD_ORE,
        Material.SAND,
        Material.COBBLESTONE,
        Material.CLAY_BALL,
        Material.PORK,
        Material.RAW_FISH,
        Material.LOG,
        Material.CACTUS,
    };

    private DwarfForge main;
    private ArrayList<Block> forges = new ArrayList<Block>();
    private int task = INVALID_TASK;


    public void enable(DwarfForge main) {
        this.main = main;

        registerEvents();
        startTask();
    }

    public void disable() {
        stopTask();
    }

    private void registerEvents() {
        PluginManager manager = main.getServer().getPluginManager();

        manager.registerEvent(Event.Type.BLOCK_PLACE,  this, Event.Priority.Normal, main);
        manager.registerEvent(Event.Type.BLOCK_BREAK,  this, Event.Priority.Normal, main);
        manager.registerEvent(Event.Type.BLOCK_DAMAGE, this, Event.Priority.Normal, main);
        manager.registerEvent(Event.Type.BLOCK_IGNITE, this, Event.Priority.Normal, main);
    }

    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled())
            return;

        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        Material type = block.getType();

        boolean attemptToBuildForge = false;

        if (type == Material.FURNACE || type == Material.BURNING_FURNACE) {
            Block below = block.getRelative(BlockFace.DOWN);
            attemptToBuildForge =
                isDwarfForge(below)
                || (below.getType() == Material.LAVA)
                || (below.getType() == Material.STATIONARY_LAVA);
        }
        else if (type == Material.LAVA || type == Material.STATIONARY_LAVA) {
            Block above = block.getRelative(BlockFace.UP);
            attemptToBuildForge =
                (above.getType() == Material.FURNACE)
                || (above.getType() == Material.BURNING_FURNACE);
        }

        if (!attemptToBuildForge)
            return;

        if (!main.permission.allow(player, "dwarfforge.create")) {
            event.setCancelled(true);
            player.sendMessage("Ye have not the strength of the Dwarfs to create such a forge.");
        }
    }

    public void onBlockDamage(BlockDamageEvent event) {
        if (event.isCancelled())
            return;

        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (isDwarfForge(block)) {
            if (main.permission.allow(player, "dwarfforge.use")) {
                if (isBurning(block))
                    douse(block);
                else
                    ignite(block);
            }
            else {
                player.sendMessage("Ye have not the will of the Dwarfs to use such a forge.");
            }
        }
    }

    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled())
            return;

        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (isDwarfForge(block)) {
            if (main.permission.allow(player, "dwarfforge.destroy")) {
                douse(block);
            }
            else {
                event.setCancelled(true);
                player.sendMessage("Ye have not the might of the Dwarfs to destroy such a forge.");
            }
        }
    }

    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.isCancelled())
            return;

        if (event.getCause() == IgniteCause.LAVA) {
            Block block = event.getBlock();

            // If there is any Dwarf Forge within 3 radius, cancel the event.
            // Yes, it's possible other exposed lava also nearby caused the
            // event, but let's assume the Dwarfs are protecting the area around
            // the Dwarf forge sufficiently.
            for (int dx = -3; dx <= 3; ++dx) {
                for (int dy = -3; dy <= 3; ++dy) {
                    for (int dz = -3; dz <= 3; ++dz) {
                        Block check = block.getRelative(dx, dy, dz);
                        if (isDwarfForge(check)) {
                            // Protect the block; cancel the ignite event.
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }
    }

    // A Dwarf Forge is:
    // 1. A furnace directly above lava.
    // 2. A furnace directly above a Dwarf Forge.
    private boolean isDwarfForge(Block block) {
        if (! (block.getState() instanceof Furnace))
            return false;

        Block below = block.getRelative(BlockFace.DOWN);
        return (below.getType() == Material.LAVA)
            || (below.getType() == Material.STATIONARY_LAVA)
            || (isDwarfForge(below));
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

    private BlockFace nextFace(BlockFace forward) {
        return dirs.get((dirs.indexOf(forward) + 1) % dirs.size());
    }

    private BlockFace prevFace(BlockFace forward) {
        return dirs.get((dirs.indexOf(forward) + 3) % dirs.size());
    }

    private Block getOutputChest(Block forge) {
        Furnace state = (Furnace) forge.getState();
        BlockFace forward = ((FurnaceAndDispenser) state.getData()).getFacing();

        // If there is a chest immediately stage-left of this forge, return it.
        Block left = forge.getRelative(prevFace(forward));
        if (left.getState().getType() == Material.CHEST)
            return left;

        // If not, but there is a forge below, use its output chest.
        Block below = forge.getRelative(BlockFace.DOWN);
        if (isDwarfForge(below))
            return getOutputChest(below);

        // No output chest.
        return null;
    }

    // Blocks are unloaded into chest stage-left of the forge (i.e. face "previous" to forward).
    private void unload(Block forge) {
        Furnace state = (Furnace) forge.getState();

        Block output = getOutputChest(forge);
        if (output != null) {
            Chest chest = (Chest) output.getState();

            Inventory forgeInv = state.getInventory();
            Inventory chestInv = chest.getInventory();

            ItemStack item = forgeInv.getItem(REFINED_SLOT);
            int preCount = item.getAmount();

            // Remove the item from the furnace.
            forgeInv.clear(REFINED_SLOT);

            // Add the smelted item to the chest.
            HashMap<Integer,ItemStack> remains = chestInv.addItem(item);

            // Did everything fit?
            if (!remains.isEmpty()) {
                // NO. Put back what remains into the refined slot.
                forgeInv.setItem(REFINED_SLOT, remains.get(0));
            }

            int postCount = forgeInv.getItem(REFINED_SLOT).getAmount();

            // Turn redstone power on.
            if (postCount != preCount)
                redstoneOn(forge);
        }
    }

    private Block getInputChest(Block forge) {
        Furnace state = (Furnace) forge.getState();
        BlockFace forward = ((FurnaceAndDispenser) state.getData()).getFacing();

        // If there is a chest immediately stage-right of this forge, return it.
        Block right = forge.getRelative(nextFace(forward));
        if (right.getState().getType() == Material.CHEST)
            return right;

        // If not, but there is a forge below, use its input chest.
        Block below = forge.getRelative(BlockFace.DOWN);
        if (isDwarfForge(below))
            return getInputChest(below);

        // No input chest.
        return null;
    }

    // Blocks are loaded from chest stage-right of the forge (i.e. face "next" from forward).
    private void refill(Block forge) {
        Furnace state = (Furnace) forge.getState();

        Block input = getInputChest(forge);
        if (input != null) {
            Chest chest = (Chest) input.getState();

            Inventory forgeInv = state.getInventory();
            Inventory chestInv = chest.getInventory();

            // Find from chest a smeltable/cookable item.
            for (int i = 0; i < smeltables.length; ++i) {
                if (chestInv.contains(smeltables[i])) {
                    int slot = chestInv.first(smeltables[i]);
                    ItemStack item = chestInv.getItem(slot);

                    forgeInv.setItem(RAW_SLOT, chestInv.getItem(slot));
                    chestInv.clear(slot);
                    break;
                }
            }
        }
    }

    private void redstoneOff(Block forge) {
        Furnace state = (Furnace) forge.getState();
        BlockFace forward = ((FurnaceAndDispenser) state.getData()).getFacing();

        // Check for a lever behind the forge.
        Block behind = forge.getRelative(forward.getOppositeFace());
        if (behind.getType() == Material.LEVER) {
            // Turn the lever to OFF setting.
            behind.setData((byte) (behind.getData() & ~0x8));    // clear ON bit
        }
    }

    private void redstoneOn(Block forge) {
        Furnace state = (Furnace) forge.getState();
        BlockFace forward = ((FurnaceAndDispenser) state.getData()).getFacing();

        // Check for a lever behind the forge.
        Block behind = forge.getRelative(forward.getOppositeFace());
        if (behind.getType() == Material.LEVER) {
            // Turn the lever to ON setting.
            behind.setData((byte) (behind.getData() | 0x8));    // set ON bit
        }
    }

    private void startTask() {
        task = main.getServer().getScheduler()
            .scheduleSyncRepeatingTask(main, this, 0, TASK_DURATION);
    }

    private void stopTask() {
        if (task != INVALID_TASK) {
            main.getServer().getScheduler().cancelTask(task);
            task = INVALID_TASK;
        }
    }

    public void run() {
        Iterator<Block> it = forges.iterator();
        while (it.hasNext()) {
            Block forge = it.next();

            // It's possible for blocks in this list to change in such a way that
            // they are no longer forges (but didn't get doused via onBreakBlock
            // above). A similar thing can occur if the forge is untouched but the
            // lava has been removed. In any of these cases, we need to remove
            // the remembered forge and forget it.
            if (!isDwarfForge(forge)) {
                it.remove();    // Remove from forges list before dousing it.

                // Not a forge but still a furnace? Douse it.
                if (forge.getState() instanceof Furnace) {
                    douse(forge);
                }

                continue;
            }

            // Keep the forge burning.
            ignite(forge);

            Furnace state = (Furnace) forge.getState();

            // Turn redstone power off.
            redstoneOff(forge);

            // Do we need to move out refined materials?
            ItemStack refined = state.getInventory().getItem(REFINED_SLOT);
            if (refined != null && refined.getType() != Material.AIR) {
                unload(forge);
            }

            // Do we need to find more raw materials?
            ItemStack raw = state.getInventory().getItem(RAW_SLOT);
            if (raw == null || raw.getType() == Material.AIR) {
                refill(forge);
            }
        }
    }

}

