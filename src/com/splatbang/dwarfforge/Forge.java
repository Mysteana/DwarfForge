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


import java.lang.Runnable;
import java.util.HashMap;
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


class Forge implements Runnable {

    static final int RAW_SLOT = 0;
    static final int FUEL_SLOT = 1;
    static final int PRODUCT_SLOT = 2;

    private static final int INVALID_TASK = -1;

    // These durations must all be less than max short.
    // Additionally, TASK_DURATION + AVOID_STAMPEDE < BURN_DURATION.
    private static final short ZERO_DURATION  =  0;
    private static final short AVOID_STAMPEDE =  2 * Utils.MINS;
    private static final short TASK_DURATION  = 20 * Utils.MINS;
    private static final short BURN_DURATION  = 25 * Utils.MINS;


    static HashMap<Location, Forge> active = new HashMap<Location, Forge>();
    private static java.util.Random rnd = new java.util.Random();


    private static short avoidStampedeDelay() {
        return (short) rnd.nextInt(AVOID_STAMPEDE);
    }


    private Location loc;
    private int task = INVALID_TASK;


    public Forge(Block block) {
        this.loc = block.getLocation();
    }

    public Forge(Location loc) {
        this.loc = loc;
    }

    @Override
    public boolean equals(Object obj) {
        return loc.equals( ((Forge) obj).loc );
    }

    @Override
    public int hashCode() {
        return loc.hashCode();
    }

    Location getLocation() {
        return loc;
    }

    Block getBlock() {
        return loc.getBlock();
    }

    boolean isValid() {
        return Forge.isValid(getBlock());
    }

    static boolean isValid(Block block) {
        return isValid(block, DFConfig.maxStackVertical());
    }

    // This static version is kept around so that other code may check if a block
    // is potentially a Forge before actually creating a Forge object.
    static boolean isValid(Block block, int stack) {
        // Can't be a Forge if it isn't a furnace.
        if (!Utils.isBlockOfType(block, Material.FURNACE, Material.BURNING_FURNACE))
            return false;

        // Can't be a Forge beyond the vertical stacking limit.
        if (stack <= 0)
            return false;

        // Is lava or another Forge below? Then it is a Forge.
        Block below = block.getRelative(BlockFace.DOWN);
        return Utils.isBlockOfType(below, Material.LAVA, Material.STATIONARY_LAVA)
            || isValid(below, stack - 1);
    }

    boolean isBurning() {
        Furnace state = (Furnace) getBlock().getState();
        return state.getBurnTime() > 0;
    }

    private void internalsSetFurnaceBurning(boolean flag) {
        // This gets into Craftbukkit internals, but it's simple and works.
        // See net.minecraft.server.BlockFurnace.java:69-84 (approx).
        CraftWorld world = (CraftWorld) loc.getWorld();
        BlockFurnace.a(flag, world.getHandle(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private void ignite() {
        Furnace state = (Furnace) getBlock().getState();
        state.setBurnTime(BURN_DURATION);
        state.update();
        internalsSetFurnaceBurning(true);
    }

    private void douse() {
        Furnace state = (Furnace) getBlock().getState();
        state.setBurnTime(ZERO_DURATION);
        state.update();
        internalsSetFurnaceBurning(false);
    }


    // Returns false if forge should be deactivated.
    boolean updateProduct() {
        /*
            product?
                yes:
                    unload product (*special: if product is coal, unload to input)
        */
        Furnace state = (Furnace) getBlock().getState();
        Inventory blockInv = state.getInventory();

        ItemStack item = blockInv.getItem(PRODUCT_SLOT);
        if (item != null && item.getType() != Material.AIR) {
            blockInv.clear(PRODUCT_SLOT);

            // Item destination: default is output chest.
            Block dest = getOutputChest();

            // Special case: if charcoal is product and fuel is required,
            // put it back into input chest.
            if (DFConfig.requireFuel() && item.getType() == Material.COAL) {
                dest = getInputChest();
            }

            ItemStack remains = addTo(item, dest, false);
            if (remains != null) {
                // Put what remains back into product slot.
                blockInv.setItem(PRODUCT_SLOT, remains);

                // See if the raw slot is full. If so, make sure it
                // is compatible with what remains. If not, shut it
                // down.
                ItemStack raw = blockInv.getItem(RAW_SLOT);
                if (raw != null && raw.getType() != Material.AIR) {
                    if (Utils.resultOfCooking(raw.getType()) != remains.getType()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    // Returns false if forge should be deactivated.
    boolean updateRawMaterial() {
        /*
            raw?
                no:
                    raw available?
                        yes:
                            load raw
                            set cook time
                        no:
                            - shut down -
        */
        Furnace state = (Furnace) getBlock().getState();
        Inventory blockInv = state.getInventory();

        // Can only reload if the raw material slot is empty.
        ItemStack raw = blockInv.getItem(RAW_SLOT);
        if (raw == null || raw.getType() == Material.AIR) {

            // Can only reload if an input chest is available.
            Block input = getInputChest();
            if (input != null) {

                BetterChest chest = new BetterChest( (Chest) input.getState() );
                Inventory chestInv = chest.getInventory();

                boolean itemFound = false;

                // Find the first smeltable item in the chest.
                ItemStack[] allItems = chestInv.getContents();
                for (ItemStack items : allItems) {
                    if (items != null && Utils.canCook(items.getType())) {

                        // TODO This probably needs to be elsewhere (and here?)
                        // updateRawMaterial is ALWAYS called after updateProduct
                        // If product remains and is NOT the same as what the
                        // current item will cook to, skip it.
                        ItemStack prod = blockInv.getItem(PRODUCT_SLOT);
                        if (prod != null && prod.getType() != Material.AIR) {
                            if (Utils.resultOfCooking(items.getType())
                                    != prod.getType()) {
                                continue;
                            }
                        }

                        // TODO one at a time?
                        chestInv.clear(chestInv.first(items));
                        blockInv.setItem(RAW_SLOT, items);

                        ((Furnace) getBlock().getState()).setCookTime(DFConfig.cookTime());

                        itemFound = true;
                        break;
                    }
                }

                if (!itemFound) {
                    return false;
                }
            }
            else {
                // no input chest; no input material
                return false;
            }
        }
        else {
            // Something already in the raw slot; is it smeltable?
            return Utils.canCook(raw.getType());
        }


        return true;
    }

    // Returns false if forge should be deactivated.
    boolean updateFuel() {
        /*
          fuel available?
              yes:
                  load fuel
              no:
                  - shut down -
         */

        // TODO assert DFConfig.requireFuel()

        Furnace state = (Furnace) getBlock().getState();
        Inventory blockInv = state.getInventory();

        // Can reload only if fuel slot is empty.
        ItemStack fuel = blockInv.getItem(FUEL_SLOT);
        if (fuel == null || fuel.getType() == Material.AIR) {

            // Can reload only if an input chest is available.
            Block input = getInputChest();
            if (input != null) {

                BetterChest chest = new BetterChest( (Chest) input.getState() );
                Inventory chestInv = chest.getInventory();

                boolean itemFound = false;

                // Find the first burnable item in the chest.
                ItemStack[] allItems = chestInv.getContents();
                for (ItemStack items : allItems) {
                    if (items != null && Utils.canBurn(items.getType())) {
                        // TODO one at a time?
                        chestInv.clear(chestInv.first(items));
                        blockInv.setItem(FUEL_SLOT, items);

                        itemFound = true;
                        break;
                    }
                }

                if (!itemFound) {
                    // TODO this might not be right... we want to allow
                    // fuel to burn itself out...?
                    return false;
                }
            }
        }

        return true;
    }

    void update() {
        // TODO assert that the forge is active; when would we ever update an
        // inactive forge?

        if (isValid()) {
            if (DFConfig.requireFuel()) {

                if (!updateProduct() || !updateRawMaterial() || !updateFuel()) {
                    // Something is preventing further smelting. Unload fuel,
                    // deactivate, and let it burn out naturally.
                    // TODO This may not be the best option...? Try it for now.
                    deactivate();
                    unloadFuel();
                }
            }
            else {
              // No fuel required; only user interaction changes forge state.
              // No user interaction here; run the processes, but don't change
              // active state.
              updateProduct();
              updateRawMaterial();
              ignite();
            }
        }
        else {
          // No longer valid: deactivate.
          deactivate();

          // Douse only if fuel is not required.
          if (!DFConfig.requireFuel()) {
            douse();
          }

        }
    }

    void burnUpdate() { update(); }

    void smeltUpdate() {
        // After a normal update (caused by an item-smelted event), set
        // the new cook time.
        update();
        if (isActive()) {
          ((Furnace) getBlock().getState()).setCookTime(DFConfig.cookTime());
        }
    }

    public void run() { update(); }

    private void activate() {
        // Only activate if not already active.
        if (!isActive()) {

            // Add to active forge map.
            active.put(loc, this);

            // Start repeating task.
            task = DwarfForge.main.queueRepeatingTask(
                    0, TASK_DURATION + avoidStampedeDelay(), this);

            // TODO force save
        }
    }

    private void deactivate() {
        // Only deactivate if currently active.
        if (isActive()) {

            // Remove from active forge map.
            active.remove(loc);

            // Cancel repeating task.
            if (task != INVALID_TASK) {
                DwarfForge.main.cancelTask(task);
                task = INVALID_TASK;
            }

            // TODO force save
        }

        // TODO Sanity check: assert(task == INVALID_TASK)
    }

    boolean isActive() {
        return active.containsKey(loc);
    }

    // Manual, user interaction to startup/shutdown a forge.
    void toggle() {
        if (isActive()) {
            if (DFConfig.requireFuel()) {
                unloadFuel();
                // TODO Save partial fuel.
            }
            deactivate();
            douse();
        }
        else {
            activate();
            ((Furnace) getBlock().getState()).setCookTime(DFConfig.cookTime());
        }
    }

    private static BlockFace getForward(Block block) {
        Furnace state = (Furnace) block.getState();
        return ((FurnaceAndDispenser) state.getData()).getFacing();
    }

    private static Block getForgeChest(Block block, BlockFace dir) {
        return getForgeChest(block, dir, DFConfig.maxStackHorizontal());
    }

    private static Block getForgeChest(Block block, BlockFace dir, int stack) {
        // Can't use the chest beyond horizontal stacking limit.
        if (stack <= 0)
            return null;

        // If the adjacent block is a chest, use it.
        Block adjacent = block.getRelative(dir);
        if (Utils.isBlockOfType(adjacent, Material.CHEST))
            return adjacent;

        // If there is a forge below, use its chest.
        Block below = block.getRelative(BlockFace.DOWN);
        if (Forge.isValid(below))
            return getForgeChest(below, dir, stack);    // Don't change horz stack dist going down.

        // If there is a forge adjacent (in provided direction) and it
        // has a chest, use it.
        if (Forge.isValid(adjacent))
            return getForgeChest(adjacent, dir, stack-1);

        // No chest.
        return null;
    }

    Block getInputChest() {
        // Look for a chest stage-right (i.e. "next" cardinal face);
        Block block = getBlock();
        return getForgeChest(block, Utils.nextCardinalFace(getForward(block)));
    }

    Block getOutputChest() {
        // Look for a chest stage-left (i.e. "prev" cardinal face).
        Block block = getBlock();
        return getForgeChest(block, Utils.prevCardinalFace(getForward(block)));
    }

    // This may get called if fuel is required and the operator toggles the forge off.
    void unloadFuel() {
        Furnace state = (Furnace) getBlock().getState();
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
            loc.getWorld().dropItemNaturally(loc, fuel);
    }

    // Move the item stack to the input/output chest as provided, either returning
    // what remains or dropping it based on flag. Note: chest might be null.
    ItemStack addTo(ItemStack item, Block chest, boolean dropRemains) {
        if (item == null)   // TODO This should NOT be possible: need to verify.
            return null;

        if (chest == null) {    // No destination chest.
            if (dropRemains) {
                loc.getWorld().dropItemNaturally(loc, item);
                return null;
            }
            else {
                return item;
            }
        }
        else {
            BetterChest bchest = new BetterChest( (Chest) chest.getState() );
            Inventory chestInv = bchest.getInventory();

            HashMap<Integer, ItemStack> remains = chestInv.addItem(item);
            if (remains.isEmpty()) {
                // Everything fit!
                return null;
            }
            else {
                // Destination chest full.
                if (dropRemains) {
                    loc.getWorld().dropItemNaturally(loc, remains.get(0));
                    return null;
                }
                else {
                    return remains.get(0);
                }
            }
        }
    }

    ItemStack addToOutput(ItemStack item, boolean dropRemains) {
        return addTo(item, getOutputChest(), dropRemains);
    }

    static Forge find(Block block) {
        return find(block.getLocation());
    }

    static Forge find(Location loc) {
        // Is it in the active Forges?
        if (active.containsKey(loc)) {
            return active.get(loc);
        }

        // Does the location block represent a valid Forge? If so, return a new one.
        if (isValid(loc.getBlock())) {
            return new Forge(loc);
        }

        // Otherwise, null.
        return null;
    }

}

