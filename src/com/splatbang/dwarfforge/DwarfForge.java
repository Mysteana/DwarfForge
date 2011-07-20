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
import java.lang.String;
import java.util.HashSet;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;


public class DwarfForge extends JavaPlugin {

    static Logger log = Logger.getLogger("Minecraft");

    DFPermissions permission = new DFPermissions();

    interface Listener {
        void onEnable(DwarfForge main);
        void onDisable();
    }

    private Listener[] listeners = {
        new DFBlockListener(),
        new DFInventoryListener()
    };

    private static final int INVALID_TASK = -1;
    private static final short TASK_DURATION = 1 * Utils.SECS;    // This should be less than burn duration.
    private int task = INVALID_TASK;

    private static DwarfForge main;

    @Override
    public void onEnable() {
        main = this;

        DFConfig.onEnable(getConfiguration());

        restoreActiveForges(Forge.active);
        permission.enable(this);
        for (Listener listener : listeners) {
            listener.onEnable(this);
        }

        startTask();

        PluginDescriptionFile pdf = this.getDescription();
        logInfo("Version " + pdf.getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        stopTask();

        for (Listener listener : listeners) {
            listener.onDisable();
        }
        permission.disable();
        saveActiveForges(Forge.active);

        DFConfig.onDisable();

        logInfo("Disabled.");
    }

    void logInfo(String msg) {
        log.info("[DwarfForge] " + msg);
    }

    void logSevere(String msg) {
        log.severe("[DwarfForge] " + msg);
    }

    int queueTask(Runnable task) {
        return getServer().getScheduler().scheduleSyncDelayedTask(this, task);
    }

    int queueDelayedTask(Runnable task, long delay) {
        return getServer().getScheduler().scheduleSyncDelayedTask(this, task, delay);
    }

    int queueRepeatingTask(Runnable task, short interval) {
        return getServer().getScheduler().scheduleSyncRepeatingTask(this, task, 0, interval);
    }

    void cancelRepeatingTask(int id) {
        getServer().getScheduler().cancelTask(id);
    }

    void registerEvent(Event.Type type, org.bukkit.event.Listener listener, Event.Priority priority) {
        getServer().getPluginManager().registerEvent(type, listener, priority, this);
    }

    static void saveActiveForges(HashSet<Forge> activeForges) {
        // TODO: Clean up this stupidity.
        main.saveActive(activeForges);
    }

    void saveActive(HashSet<Forge> activeForges) {
        File fout = new File(getDataFolder(), "active_forges");
        try {
            DataOutputStream out = new DataOutputStream(new FileOutputStream(fout));
            int count = 0;
            for (Forge forge : activeForges) {
                Location loc = forge.getLocation();
                out.writeUTF(loc.getWorld().getName());
                out.writeDouble(loc.getX());
                out.writeDouble(loc.getY());
                out.writeDouble(loc.getZ());
                count += 1;
            }
            out.close();
            logInfo("Saved " + count + " active Forges.");
        }
        catch (Exception e) {
            logSevere("Could not save active forges to file: " + e);
        }
    }

    static void restoreActiveForges(HashSet<Forge> activeForges) {
        // TODO: Clean up this stupidity.
        main.restoreActive(activeForges);
    }

    void restoreActive(HashSet<Forge> activeForges) {
        activeForges.clear();
        File fin = new File(getDataFolder(), "active_forges");
        if (fin.exists()) {
            try {
                DataInputStream in = new DataInputStream(new FileInputStream(fin));
                int count = 0;
                while (true) {
                    try {
                        String name = in.readUTF();
                        double x = in.readDouble();
                        double y = in.readDouble();
                        double z = in.readDouble();
                        Location loc = new Location(getServer().getWorld(name), x, y, z);
                        activeForges.add(new Forge(loc.getBlock()));
                        count += 1;
                    }
                    catch (EOFException e) {
                        break;
                    }
                }
                in.close();
                logInfo("Restored " + count + " active Forges.");
            }
            catch (Exception e) {
                logSevere("Something went wrong with file while restoring forges: " + e);
            }
        }
    }

    private void startTask() {
        task = queueRepeatingTask(new Runnable() {
            public void run() {
                Forge.updateAll();
            }
        }, TASK_DURATION);
    }

    private void stopTask() {
        if (task != INVALID_TASK) {
            cancelRepeatingTask(task);
            task = INVALID_TASK;
        }
    }

}

