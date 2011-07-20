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
        return Forge.isValid(block);
    }

    // This static version is kept around so that other code may check if a block
    // is potentially a Forge before actually creating a Forge object.
    static boolean isValid(Block block) {
        // Can't be a Forge if it isn't a furnace.
        if (!Utils.isBlockOfType(block, Material.FURNACE, Material.BURNING_FURNACE))
            return false;

        Block below = block.getRelative(BlockFace.DOWN);

        // Is lava or another Forge below? Then it is a Forge.
        return Utils.isBlockOfType(below, Material.LAVA, Material.STATIONARY_LAVA)
            || isValid(below);
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

    // Returns TRUE if the furnace is lit.
    boolean ignite() {
        boolean lit = false;
        if (DFInventoryListener.requireFuel) {
            lit = loadFuel();
        }
        else {
            Furnace state = (Furnace) block.getState();
            state.setBurnTime(BURN_DURATION);
            state.update();
            internalsSetFurnaceBurning(true);
            lit = true;
        }

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
        return lit;
    }
        
    void douse() {
        Furnace state = (Furnace) block.getState();
        state.setBurnTime(ZERO_DURATION);
        state.update();
        internalsSetFurnaceBurning(false);
    }

    void toggle() {
        if (isBurning()) {
            if (DFInventoryListener.requireFuel) {
                unloadFuel();
            }
            else {
                douse();
            }
            active.remove(this);
        }
        else {
            if (ignite()) {
                active.add(this);
            }
        }

        DwarfForge.saveActiveForges(active);
    }

    private static BlockFace getForward(Block block) {
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

    // Returns true if fuel is in the fuel slot (not necessarily if it was just loaded).
    boolean loadFuel() {
        Furnace state = (Furnace) block.getState();
        Inventory blockInv = state.getInventory();

        // If the fuel slot in the furnace is occupied, can't reload.
        ItemStack fuel = blockInv.getItem(FUEL_SLOT);
        if (fuel != null && fuel.getType() != Material.AIR)
            return true;    // We didn't load, but there IS fuel there.

        // If there is no input chest, can't reload.
        Block input = getInputChest();
        if (input == null)
            return false;

        BetterChest chest = new BetterChest( (Chest) input.getState() );
        Inventory chestInv = chest.getInventory();

        // Find the first burnable item in the chest.
        ItemStack[] allItems = chestInv.getContents();
        for (ItemStack items : allItems) {
            if (items == null)
                continue;

            if (Utils.canBurn(items.getType())) {
                chestInv.clear(chestInv.first(items));  // Remove from the chest.
                blockInv.setItem(FUEL_SLOT, items);     // Add to the furnace.
                return true;
            }
        }

        return false;
    }

    // This may get called if fuel is required and the operator toggles the forge off.
    void unloadFuel() {
        Furnace state = (Furnace) block.getState();
        Inventory blockInv = state.getInventory();

        // Remove fuel from the furnace.
        ItemStack fuel = blockInv.getItem(FUEL_SLOT);
        if (fuel == null || fuel.getType() == Material.AIR)
            return;     // No fuel? WTF? Whatever...

        blockInv.clear(FUEL_SLOT);

        // First, try putting as much fuel back into the input chest.
        Block input = getInputChest();
        if (input != null) {
            BetterChest chest = new BetterChest( (Chest) input.getState() );
            Inventory chestInv = chest.getInventory();

            // Add to chest; remember what remains, if any.
            HashMap<Integer,ItemStack> remains = chestInv.addItem(fuel);
            fuel = remains.isEmpty() ? null : remains.get(0);
        }

        // Second, drop on ground.
        if (fuel != null)
            block.getWorld().dropItemNaturally(block.getLocation(), fuel);
    }


    ItemStack addToOutput(ItemStack item, boolean dropRemains) {
        Block output = getOutputChest();
        if (output == null) {
            // No output chest.
            if (dropRemains) {
                block.getWorld().dropItemNaturally(block.getLocation(), item);
                return null;
            }
            else {
                return item;
            }
        }
        else {
            BetterChest chest = new BetterChest( (Chest) output.getState() );
            Inventory chestInv = chest.getInventory();

            HashMap<Integer, ItemStack> remains = chestInv.addItem(item);
            if (!remains.isEmpty()) {
                // Output chest full.
                if (dropRemains) {
                    block.getWorld().dropItemNaturally(block.getLocation(), remains.get(0));
                    return null;
                }
                else {
                    return remains.get(0);
                }
            }
        }
        return null;
    }

    void unloadProduct() {
        Furnace state = (Furnace) block.getState();
        Inventory blockInv = state.getInventory();

        ItemStack item = blockInv.getItem(PRODUCT_SLOT);
        if (item != null) {
            blockInv.clear(PRODUCT_SLOT);
            ItemStack remains = addToOutput(item, false);
            
            if (remains != null) {
                // Put what remains back into product slot.
                blockInv.setItem(PRODUCT_SLOT, remains);
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
            boolean requireFuel = DFInventoryListener.requireFuel;

            // It's possible for blocks to change such that they are no longer
            // considered forges. (TODO: How???) Forget the remembered forge.

            if (!forge.isValid()) {
                it.remove();
                forceSave = true;

                // TODO: What's the "right thing" to do if it's still a burning furnace?
                // Don't douse it if fuel is required; it should burn out on its own.
                if (!requireFuel && forge.isBurning()) {
                    forge.douse();
                }
            }
            else if (requireFuel) {
                // Remove from active list if no longer burning (ie., ran out of fuel).
                if (!forge.isBurning()) {
                    it.remove();
                    forceSave = true;
                }
            }
            else {
                // Still a forge, without need for fuel; keep it burning.
                forge.ignite();
            }
        }

        if (forceSave) {
            DwarfForge.saveActiveForges(active);
        }
    }

}

