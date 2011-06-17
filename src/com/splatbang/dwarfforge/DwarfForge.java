package com.splatbang.dwarfforge;

import java.lang.String;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class DwarfForge extends JavaPlugin {
    public static Logger log = Logger.getLogger("Minecraft");

    private DFBlockListener blockListener = new DFBlockListener(this);

    private final int NO_TASK = -1;
    private final long SECONDS = 20L;
    private int furnaceTask = NO_TASK;

    @Override
    public void onEnable() {
        setupDatabase();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvent(Event.Type.BLOCK_BREAK,  blockListener, Event.Priority.Monitor, this);
        pm.registerEvent(Event.Type.BLOCK_DAMAGE, blockListener, Event.Priority.Monitor, this);
        pm.registerEvent(Event.Type.BLOCK_PLACE,  blockListener, Event.Priority.Monitor, this);

        furnaceTask = getServer().getScheduler().scheduleSyncRepeatingTask(this, blockListener, 30*SECONDS, 30*SECONDS);
        if (furnaceTask == NO_TASK) {
            logSevere("Could not schedule furnace task.");
        }

        PluginDescriptionFile pdf = this.getDescription();
        logInfo("Version " + pdf.getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (furnaceTask != NO_TASK) {
            getServer().getScheduler().cancelTask(furnaceTask);
            furnaceTask = NO_TASK;
        }
        logInfo("Disabled.");
    }

    private void setupDatabase() {
    }

    @Override
    public List<Class<?>> getDatabaseClasses() {
        List<Class<?>> list = new ArrayList<Class<?>>();
        // ...
        return list;
    }

    public void logInfo(String msg) {
        log.info("[DwarfForge] " + msg);
    }

    public void logSevere(String msg) {
        log.severe("[DwarfForge] " + msg);
    }

}

