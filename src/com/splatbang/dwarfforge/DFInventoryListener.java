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
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.InventoryListener;
import org.bukkit.event.inventory.FurnaceSmeltEvent;


class DFInventoryListener extends InventoryListener implements DFListener {
    private DwarfForge main;
    private final static double DEFAULT_COOK_TIME = 9.25;
    private double cookTime;    // in seconds

    @Override
    public void onEnable(DwarfForge main) {
        this.main = main;
        cookTime =  main.config.getDouble("DwarfForge.cooking-time.default", DEFAULT_COOK_TIME);
        main.registerEvent(Event.Type.FURNACE_SMELT, this, Event.Priority.Monitor);
    }

    @Override
    public void onDisable() { }

    @Override
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        // Monitoring event: do nothing if event was cancelled.
        if (event.isCancelled())
            return;

        // Do nothing if the furnace isn't a Dwarf Forge.
        final Block furnace = event.getFurnace();
        if (!Listener.isDwarfForge(furnace))
            return;

        // Queue up task to unload and reload the furnace.
        main.queueTask(new Runnable() {
            public void run() {
                Listener.unload(furnace);
                Listener.reload(furnace);

                // setCookTime sets time elapsed, not time remaining.
                short dt = (short) (Math.max(DEFAULT_COOK_TIME - cookTime, 0) * Listener.SECS);
                ((Furnace) furnace.getState()).setCookTime(dt);
            }
        });
    }
}

