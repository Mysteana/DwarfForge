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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;


public class Listener implements Runnable {
    private static final int INVALID_TASK = -1;

    static final short ZERO_DURATION = 1;                 // Yeah, I should explain this...
    static final short TASK_DURATION = 1 * Utils.SECS;    // This should be less than burn duration.
    static final short BURN_DURATION =25 * Utils.MINS;    // This must be less than max short.

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
            if (!Forge.isValid(forge)) {
                it.remove();

                // No longer a forge, but still a burning furnace?
                if (Forge.isBurning(forge)) {
                    Forge.douse(forge);
                }

                forceSave = true;

                // Not a forge; do nothing else to this particular block.
                continue;
            }

            // Keep the forge burning.
            Forge.ignite(forge);
        }

        if (forceSave) {
            saveActiveForges();
        }
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

    static void toggleForge(Block block) {
        Forge.toggle(block);

        if (Forge.isBurning(block)) {
            activeForges.add(block.getLocation());
        }
        else {
            activeForges.remove(block.getLocation());
        }

        saveActiveForges();
    }

}

