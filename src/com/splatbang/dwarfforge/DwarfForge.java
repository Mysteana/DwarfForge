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

    public DFPermissions permission = new DFPermissions();
    public Configuration config = null;


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

    public void logInfo(String msg) {
        log.info("[DwarfForge] " + msg);
    }

    public void logSevere(String msg) {
        log.severe("[DwarfForge] " + msg);
    }

    public int queueTask(Runnable task) {
        return getServer().getScheduler().scheduleSyncDelayedTask(this, task);
    }

    public int queueRepeatingTask(Runnable task, short interval) {
        return getServer().getScheduler().scheduleSyncRepeatingTask(this, task, 0, interval);
    }

    public void cancelRepeatingTask(int id) {
        getServer().getScheduler().cancelTask(id);
    }

    public void registerEvent(Event.Type type, org.bukkit.event.Listener listener, Event.Priority priority) {
        getServer().getPluginManager().registerEvent(type, listener, priority, this);
    }
}

