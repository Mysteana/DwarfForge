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
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryListener;
import org.bukkit.inventory.ItemStack;


class DFInventoryListener extends InventoryListener implements DwarfForge.Listener {
    private DwarfForge main;

    @Override
    public void onEnable(DwarfForge main) {
        this.main = main;

        // Event registration
        main.registerEvent(Event.Type.FURNACE_BURN,  this, Event.Priority.Monitor);
        main.registerEvent(Event.Type.FURNACE_SMELT, this, Event.Priority.Monitor);
    }

    @Override
    public void onDisable() { }

    @Override
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        // NOTE: This identifies the START of a fuel burning event, not its
        // completion. Still, it's a good opportunity to reload the fuel slot
        // if it is now empty.

        // Monitoring event: do nothing if event was cancelled.
        if (event.isCancelled())
            return;

        // Do nothing if the furnace isn't a Dwarf Forge.
        Block block = event.getFurnace();
        if (!Forge.isValid(block))
            return;

        final Forge forge = new Forge(block);

        // If it was a lava bucket that was burned, place an empty
        // bucket into the output chest (or drop to ground).
        if (event.getFuel().getType() == Material.LAVA_BUCKET) {
            forge.addToOutput(new ItemStack(Material.BUCKET, 1), true);
        }

        // Do nothing if fuel is not required.
        if (!DFConfig.requireFuel())
            return;

        // Attempt to reload the Forge's fuel slot.
        main.queueTask(new Runnable() {
            public void run() {
                forge.loadFuel();
            }
        });
    }

    @Override
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        // Monitoring event: do nothing if event was cancelled.
        if (event.isCancelled())
            return;

        // Do nothing if the furnace isn't a Dwarf Forge.
        Block block = event.getFurnace();
        if (!Forge.isValid(block))
            return;

        // Queue up task to unload and reload the furnace.
        final Forge forge = new Forge(block);
        main.queueTask(new Runnable() {
            public void run() {
                forge.unloadProduct();
                forge.loadRawMaterial();

                // setCookTime sets time elapsed, not time remaining.
                short dt = (short) (Math.max(DFConfig.cookTime(), 0) * Utils.SECS);
                forge.setCookTime(dt);
            }
        });
    }
}

