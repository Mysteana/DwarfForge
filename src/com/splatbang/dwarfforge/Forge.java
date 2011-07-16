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


import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Furnace;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.FurnaceAndDispenser;


class Forge {

    private static final int RAW_SLOT = 0;
    private static final int FUEL_SLOT = 1;
    private static final int PRODUCT_SLOT = 2;

    private static final short ZERO_DURATION = 1;                 // Yeah, I should explain this...
    private static final short BURN_DURATION =25 * Utils.MINS;    // This must be less than max short.

    static HashSet<Location> active = new HashSet<Location>();


    static boolean isValid(Block block) {
        // Can't be a Forge if it isn't a furnace.
        if (!Utils.isBlockOfType(block, Material.FURNACE, Material.BURNING_FURNACE))
            return false;

        // Is lava or another Forge below this one?
        Block below = block.getRelative(BlockFace.DOWN);
        return Utils.isBlockOfType(below, Material.LAVA, Material.STATIONARY_LAVA)
            || Forge.isValid(below);
    }

    static boolean isBurning(Block block) {
        return Utils.isBlockOfType(block, Material.BURNING_FURNACE);
    }

    static void ignite(Block block) {
        Furnace state = (Furnace) block.getState();

        // Setting the block type causes the furnace to drop
        // inventory. Hence, we save and restore the inventory
        // around the type change.
        if (!isBurning(block)) {
            Furnace priorState = state;
            ItemStack[] stuff = saveInventory(priorState);
            clearInventory(priorState); // Needed to avoid duping, etc.

            block.setType(Material.BURNING_FURNACE);

            state = (Furnace) block.getState();
            restoreInventory(state, stuff);
            state.setData(priorState.getData());
        }

        state.setBurnTime(BURN_DURATION);
        state.update();

        // Anytime we (re-)ignite the furnace, we can attempt to reload
        // raw materials.
        loadRawMaterial(block);
    }

    static void douse(Block block) {
        // Easy way to douse a forge is simply to set a "zero" duration.
        Furnace state = (Furnace) block.getState();
        state.setBurnTime(ZERO_DURATION);
        state.update();
    }

    static void toggle(Block block) {
        if (isBurning(block)) {
            douse(block);
            active.remove(block.getLocation());
        }
        else {
            ignite(block);
            active.add(block.getLocation());
        }

        DwarfForge.saveActiveForges(active);
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

    static BlockFace getForward(Block block) {
        Furnace state = (Furnace) block.getState();
        return ((FurnaceAndDispenser) state.getData()).getFacing();
    }

    static Block getForgeChest(Block block, BlockFace dir) {
        // If the adjacent block is a chest, use it.
        Block adjacent = block.getRelative(dir);
        if (Utils.isBlockOfType(adjacent, Material.CHEST))
            return adjacent;

        // If there is a forge below, use its chest.
        Block below = block.getRelative(BlockFace.DOWN);
        if (Forge.isValid(below))
            return getForgeChest(below, dir);

        // If there is a forge adjacent (in provided direction) and it
        // has a chest, use it.
        if (Forge.isValid(adjacent))
            return getForgeChest(adjacent, dir);

        // No chest.
        return null;
    }

    static Block getInputChest(Block block) {
        // Look for a chest stage-right (i.e. "next" face);
        return getForgeChest(block, Utils.nextFace(getForward(block)));
    }

    static Block getOutputChest(Block block) {
        // Look for a chest stage-left (i.e. "prev" face).
        return getForgeChest(block, Utils.prevFace(getForward(block)));
    }

    static void loadRawMaterial(Block block) {
        Furnace state = (Furnace) block.getState();
        Inventory blockInv = state.getInventory();

        // If the raw material slot in the furnace is occupied, can't reload.
        ItemStack raw = blockInv.getItem(RAW_SLOT);
        if (raw != null && raw.getType() != Material.AIR)
            return;

        // If there is no input chest, can't reload.
        Block input = getInputChest(block);
        if (input == null)
            return;

        BetterChest chest = new BetterChest( (Chest) input.getState() );
        Inventory chestInv = chest.getInventory();

        // Find the first smeltable item in the chest.
        ItemStack[] allItems = chestInv.getContents();
        for (ItemStack items : allItems) {
            if (items == null)
                continue;

            if (Utils.canCook(items.getType())) {
                chestInv.clear(chestInv.first(items));  // Remove from the chest.
                blockInv.setItem(RAW_SLOT, items);      // Add to the furnace.
                return;
            }
        }
    }

    static void unloadProduct(final Block block) {
        Furnace state = (Furnace) block.getState();

        Block output = getOutputChest(block);
        if (output != null) {
            BetterChest chest = new BetterChest( (Chest) output.getState() );

            Inventory blockInv = state.getInventory();
            Inventory chestInv = chest.getInventory();

            ItemStack item = blockInv.getItem(PRODUCT_SLOT);

            // Remove the item from the furnace.
            blockInv.clear(PRODUCT_SLOT);

            // Add the smelted item to the chest.
            HashMap<Integer,ItemStack> remains = chestInv.addItem(item);

            // Did everything fit?
            if (!remains.isEmpty()) {
                // NO. Put back what remains into the product slot.
                blockInv.setItem(PRODUCT_SLOT, remains.get(0));
            }
        }
    }

    static void updateAll() {
        boolean forceSave = false;

        Iterator<Location> it = active.iterator();
        while (it.hasNext()) {
            Block block = it.next().getBlock();

            // It's possible for blocks to change such that they are no longer
            // considered forges. (TODO: How???) Forget the remembered forge.
            if (!isValid(block)) {
                it.remove();
                forceSave = true;

                // TODO: What's the "right thing" to do if it's still a burning furnace?
                // This may change once fuel support is added.
                if (isBurning(block)) {
                    douse(block);
                }

                // Not a forge; nothing else to do. Move along...
                continue;
            }

            // Keep the forge burning.
            // TODO: This will change once fuel support is added.
            ignite(block);
        }

        if (forceSave) {
            DwarfForge.saveActiveForges(active);
        }
    }

}

