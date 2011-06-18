package com.splatbang.dwarfforge;

import java.lang.String;
import java.util.logging.Logger;

import org.bukkit.event.Event;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;


public class DwarfForge extends JavaPlugin {
    public static Logger log = Logger.getLogger("Minecraft");

    private DFBlockListener blockListener = new DFBlockListener(this);

    @Override
    public void onEnable() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvent(Event.Type.BLOCK_BREAK,  blockListener, Event.Priority.Monitor, this);
        pm.registerEvent(Event.Type.BLOCK_DAMAGE, blockListener, Event.Priority.Monitor, this);

        blockListener.startTask();

        PluginDescriptionFile pdf = this.getDescription();
        logInfo("Version " + pdf.getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        blockListener.stopTask();

        logInfo("Disabled.");
    }

    public void logInfo(String msg) {
        log.info("[DwarfForge] " + msg);
    }

    public void logSevere(String msg) {
        log.severe("[DwarfForge] " + msg);
    }

}

