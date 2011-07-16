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

import net.minecraft.server.BlockFurnace;
import org.bukkit.craftbukkit.CraftWorld;

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

    private static final short ZERO_DURATION = 0;
    private static final short BURN_DURATION = 25 * Utils.MINS;   // This must be less than max short.

    static HashSet<Forge> active = new HashSet<Forge>();


    private Block block;
    

    public Forge(Block block) {
        this.block = block;
    }

    public Forge(Location loc) {
        this.block = loc.getBlock();
    }

    @Override
    public boolean equals(Object obj) {
        return block.equals(((Forge) obj).block);
    }

    @Override
    public int hashCode() {
        return block.hashCode();
    }

    Location getLocation() {
        return block.getLocation();
    }

    boolean isValid() {
        // Can't be a Forge if it isn't a furnace.
        if (!Utils.isBlockOfType(block, Material.FURNACE, Material.BURNING_FURNACE))
            return false;

        Block below = block.getRelative(BlockFace.DOWN);

        // Is lava below? Then it is a Forge.
        if (Utils.isBlockOfType(below, Material.LAVA, Material.STATIONARY_LAVA))
            return true;

        // Finally, it is a Forge if another Forge is below.
        return (new Forge(below)).isValid();
    }

    static boolean isValid(Block block) {
        return (new Forge(block)).isValid();
    }

    boolean isBurning() {
        return Utils.isBlockOfType(block, Material.BURNING_FURNACE);
    }

    private void internalsSetFurnaceBurning(boolean flag) {
        // This gets into Craftbukkit/Minecraft internalss, but it's simple and works.
        // See net.minecraft.server.BlockFurnace.java:69-84 (approx).
        Location loc = block.getLocation();
        CraftWorld world = (CraftWorld) loc.getWorld();
        BlockFurnace.a(flag, world.getHandle(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    void ignite() {
        Furnace state = (Furnace) block.getState();
        state.setBurnTime(BURN_DURATION);
        state.update();
        internalsSetFurnaceBurning(true);

        /*  NOTE: --- Here is the prior, inventory-juggling code. ---
        if (!isBurning()) {
            // Setting the block type causes the furnace to drop
            // inventory. Hence, we save and restore the inventory
            // around the type change.

            Furnace priorState = state;
            ItemStack[] stuff = priorState.getInventory().getContents();
            priorState.getInventory().clear();      // Needed to avoid duping, etc.

            block.setType(Material.BURNING_FURNACE);

            state = (Furnace) block.getState();
            state.getInventory().setContents(stuff);
            state.setData(priorState.getData());
        }
        */

        // Anytime we (re-)ignite the furnace, we can attempt to reload
        // raw materials.
        loadRawMaterial();
    }
        
    void douse() {
        Furnace state = (Furnace) block.getState();
        state.setBurnTime(ZERO_DURATION);
        state.update();
        internalsSetFurnaceBurning(false);
    }

    void toggle() {
        if (isBurning()) {
            douse();
            active.remove(this);
        }
        else {
            ignite();
            active.add(this);
        }

        DwarfForge.saveActiveForges(active);
    }

    static BlockFace getForward(Block block) {
        Furnace state = (Furnace) block.getState();
        return ((FurnaceAndDispenser) state.getData()).getFacing();
    }

    private static Block getForgeChest(Block block, BlockFace dir) {
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

    private Block getInputChest() {
        // Look for a chest stage-right (i.e. "next" cardinal face);
        return getForgeChest(block, Utils.nextCardinalFace(getForward(block)));
    }

    private Block getOutputChest() {
        // Look for a chest stage-left (i.e. "prev" cardinal face).
        return getForgeChest(block, Utils.prevCardinalFace(getForward(block)));
    }

    void loadRawMaterial() {
        Furnace state = (Furnace) block.getState();
        Inventory blockInv = state.getInventory();

        // If the raw material slot in the furnace is occupied, can't reload.
        ItemStack raw = blockInv.getItem(RAW_SLOT);
        if (raw != null && raw.getType() != Material.AIR)
            return;

        // If there is no input chest, can't reload.
        Block input = getInputChest();
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

    void unloadProduct() {
        Furnace state = (Furnace) block.getState();

        Block output = getOutputChest();
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

    void setCookTime(short dt) {
        ((Furnace) block.getState()).setCookTime(dt);
    }

    static void updateAll() {
        boolean forceSave = false;

        Iterator<Forge> it = active.iterator();
        while (it.hasNext()) {
            Forge forge = it.next();

            // It's possible for blocks to change such that they are no longer
            // considered forges. (TODO: How???) Forget the remembered forge.

            if (!forge.isValid()) {
                it.remove();
                forceSave = true;

                // TODO: What's the "right thing" to do if it's still a burning furnace?
                // This may change once fuel support is added.
                if (forge.isBurning()) {
                    forge.douse();
                }

                // Not a forge; nothing else to do. Move along...
                continue;
            }

            // Keep the forge burning.
            // TODO: This will change once fuel support is added.
            forge.ignite();
        }

        if (forceSave) {
            DwarfForge.saveActiveForges(active);
        }
    }

}

