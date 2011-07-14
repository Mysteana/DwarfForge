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


import java.lang.String;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;


public class DwarfForge extends JavaPlugin {

    private Logger log = Logger.getLogger("Minecraft");
    private Listener listener = new Listener();

    DFPermissions permission = new DFPermissions();
    Configuration config = null;


    @Override
    public void onEnable() {
        config = getConfiguration();

        permission.enable(this);
        listener.enable(this);

        // If the config file didn't exist, this will write the default back to disk.
        config.save();

        PluginDescriptionFile pdf = this.getDescription();
        logInfo("Version " + pdf.getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        listener.disable();
        permission.disable();
        config = null;

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
}

