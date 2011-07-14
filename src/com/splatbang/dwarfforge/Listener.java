/*
    Copyright (C) 2011 by Matthew D Moss

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
    THE SOFTWARE.
*/

package com.splatbang.dwarfforge;

import java.io.EOFException;
import java.io.File;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.Runnable;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.material.FurnaceAndDispenser;


public class Listener implements Runnable {
    private static final int INVALID_TASK = -1;
    static final short SECS = 20;
    static final short MINS = 60 * SECS;

    private static final short ZERO_DURATION = 1;           // Yeah, I should explain this...
    private static final short TASK_DURATION = 1 * SECS;    // This should be less than burn duration.
    private static final short BURN_DURATION =25 * MINS;    // This must be less than max short.

    private static final int RAW_SLOT = 0;
    private static final int FUEL_SLOT = 1;
    private static final int REFINED_SLOT = 2;

    private static final List<BlockFace> DIRS = Arrays.asList(
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    private static final Set<Material> SMELTABLES = new HashSet<Material>(Arrays.asList(
        new Material[] {
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
        }));

    static DwarfForge main;
    private DFListener[] listeners = {
        new DFBlockListener(),
        new DFInventoryListener()
    };

    static HashSet<Location> activeForges = new HashSet<Location>();
    private int task = INVALID_TASK;


    public void enable(DwarfForge main) {
        this.main = main;

        for (DFListener listener : listeners) {
            listener.onEnable(main);
        }

        startTask();

        restoreActiveForges();
    }

    public void disable() {
        saveActiveForges();

        stopTask();

        for (DFListener listener : listeners) {
            listener.onDisable();
        }
    }

    private void startTask() {
        task = main.queueRepeatingTask(this, TASK_DURATION);
    }

    private void stopTask() {
        if (task != INVALID_TASK) {
            main.cancelRepeatingTask(task);
            task = INVALID_TASK;
        }
    }

    @Override
    public void run() {
        boolean forceSave = false;

        Iterator<Location> it = activeForges.iterator();
        while (it.hasNext()) {
            Block forge = it.next().getBlock();

            // It's possible for blocks in this list to change in such a way that
            // they are no longer forges. Remove the remembered forge to forget it.
            if (!isDwarfForge(forge)) {
                it.remove();

                // No longer a forge, but still a burning furnace?
                if (isBurning(forge)) {
                    douse(forge);
                }

                forceSave = true;

                // Not a forge; do nothing else to this particular block.
                continue;
            }

            // Keep the forge burning.
            ignite(forge);
        }

        if (forceSave) {
            saveActiveForges();
        }
    }

    static boolean isDwarfForge(Block block) {
        // Can't be a Dwarf Forge if it isn't a furnace.
        if (!isBlockOfType(block, Material.FURNACE, Material.BURNING_FURNACE))
            return false;

        // Is lava or another Dwarf Forge below this one?
        Block below = block.getRelative(BlockFace.DOWN);
        return isBlockOfType(below, Material.LAVA, Material.STATIONARY_LAVA)
            || isDwarfForge(below);
    }

    static void ignite(Block forge) {
        Furnace state = (Furnace) forge.getState();

        // Setting the block type causes the furnace to drop
        // inventory. Hence, we save and restore the inventory
        // around the type change.
        if (!isBurning(forge)) {
            Furnace priorState = state;
            ItemStack[] stuff = saveInventory(priorState);
            clearInventory(priorState); // Needed to avoid duping, etc.

            forge.setType(Material.BURNING_FURNACE);

            state = (Furnace) forge.getState();
            restoreInventory(state, stuff);
            state.setData(priorState.getData());
        }

        state.setBurnTime(BURN_DURATION);
        state.update();

        // Anytime we (re-)ignite the furnace, we can attempt to reload
        // raw materials.
        reload(forge);
    }

    static void douse(Block forge) {
        // Easy way to douse a forge is simply to set a "zero" duration.
        Furnace state = (Furnace) forge.getState();
        state.setBurnTime(ZERO_DURATION);
        state.update();
    }

    static boolean isBurning(Block block) {
        return isBlockOfType(block, Material.BURNING_FURNACE);
    }

    static void clearInventory(Furnace furnace) {
        furnace.getInventory().clear();
    }

    static ItemStack[] saveInventory(Furnace furnace) {
        return furnace.getInventory().getContents();
    }

    static void restoreInventory(Furnace furnace, ItemStack[] stuff) {
        furnace.getInventory().setContents(stuff);
    }

    static BlockFace nextFace(BlockFace forward) {
        return DIRS.get((DIRS.indexOf(forward) + 1) % DIRS.size());
    }

    static BlockFace prevFace(BlockFace forward) {
        return DIRS.get((DIRS.indexOf(forward) + 3) % DIRS.size());
    }

    static BlockFace getForward(Block forge) {
        Furnace state = (Furnace) forge.getState();
        return ((FurnaceAndDispenser) state.getData()).getFacing();
    }

    static Block getForgeChest(Block forge, BlockFace dir) {
        // If the adjacent block is a chest, use it.
        Block adjacent = forge.getRelative(dir);
        if (isBlockOfType(adjacent, Material.CHEST))
            return adjacent;

        // If there is a forge below, use its chest.
        Block below = forge.getRelative(BlockFace.DOWN);
        if (isDwarfForge(below))
            return getForgeChest(below, dir);

        // If there is a forge adjacent (in provided direction) and it
        // has a chest, use it.
        if (isDwarfForge(adjacent))
            return getForgeChest(adjacent, dir);

        // No chest.
        return null;
    }

    static Block getInputChest(Block forge) {
        // Look for a chest stage-right (i.e. "next" face);
        return getForgeChest(forge, nextFace(getForward(forge)));
    }

    static Block getOutputChest(Block forge) {
        // Look for a chest stage-left (i.e. "prev" face).
        return getForgeChest(forge, prevFace(getForward(forge)));
    }

    // Blocks are unloaded into chest stage-left of the forge (i.e. face "previous" to forward).
    static void unload(final Block forge) {
        Furnace state = (Furnace) forge.getState();

        Block output = getOutputChest(forge);
        if (output != null) {
            BetterChest chest = new BetterChest( (Chest) output.getState() );

            Inventory forgeInv = state.getInventory();
            Inventory chestInv = chest.getInventory();

            ItemStack item = forgeInv.getItem(REFINED_SLOT);

            // Remove the item from the furnace.
            forgeInv.clear(REFINED_SLOT);

            // Add the smelted item to the chest.
            HashMap<Integer,ItemStack> remains = chestInv.addItem(item);

            // Did everything fit?
            if (!remains.isEmpty()) {
                // NO. Put back what remains into the refined slot.
                forgeInv.setItem(REFINED_SLOT, remains.get(0));
            }
        }
    }

    // Blocks are loaded from chest stage-right of the forge (i.e. face "next" from forward).
    static void reload(Block forge) {
        Furnace state = (Furnace) forge.getState();
        Inventory forgeInv = state.getInventory();

        // If the raw material slot in the furnace is occupied, can't reload.
        ItemStack raw = forgeInv.getItem(RAW_SLOT);
        if (raw != null && raw.getType() != Material.AIR)
            return;

        // If there is no input chest, can't reload.
        Block input = getInputChest(forge);
        if (input == null)
            return;

        BetterChest chest = new BetterChest( (Chest) input.getState() );
        Inventory chestInv = chest.getInventory();

        // Find the first smeltable item in the chest.
        ItemStack[] allItems = chestInv.getContents();
        for (ItemStack items : allItems) {
            if (items == null)
                continue;

            if (SMELTABLES.contains(items.getType())) {
                // Remove from the chest.
                chestInv.clear(chestInv.first(items));
                // Add to the furnace.
                forgeInv.setItem(RAW_SLOT, items);
                // Reload is done!
                return;
            }
        }
    }

    static boolean isBlockOfType(Block block, Material... types) {
        for (Material type : types) {
            if (block.getType() == type)
                return true;
        }
        return false;
    }

    static void saveActiveForges() {
        try {
            File fout = new File(main.getDataFolder(), "active_forges");
            DataOutputStream out = new DataOutputStream(new FileOutputStream(fout));
            for (Location loc : activeForges) {
                out.writeUTF(loc.getWorld().getName());
                out.writeDouble(loc.getX());
                out.writeDouble(loc.getY());
                out.writeDouble(loc.getZ());
            }
            out.close();
        }
        catch (Exception e) {
            main.logSevere("Could not save active forges to file: " + e);
        }
    }

    static void restoreActiveForges() {
        try {
            File fin = new File(main.getDataFolder(), "active_forges");
            DataInputStream in = new DataInputStream(new FileInputStream(fin));
            int count = 0;
            while (true) {
                try {
                    String name = in.readUTF();
                    double x = in.readDouble();
                    double y = in.readDouble();
                    double z = in.readDouble();
                    activeForges.add(new Location(main.getServer().getWorld(name), x, y, z));
                    count += 1;
                }
                catch (EOFException e) {
                    break;
                }
            }
            in.close();
            main.logInfo("Restored " + count + " active, running forges.");
        }
        catch (Exception e) {
            main.logSevere("Something went wrong with file while restoring forges: " + e);
        }
    }

    static void toggleForge(Block forge) {
        if (isBurning(forge)) {
            douse(forge);
            activeForges.remove(forge.getLocation());
            saveActiveForges();
        }
        else {
            ignite(forge);
            activeForges.add(forge.getLocation());
            saveActiveForges();
        }
    }

}

