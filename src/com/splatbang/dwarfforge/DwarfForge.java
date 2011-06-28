package com.splatbang.dwarfforge;

import java.lang.String;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;


public class DwarfForge extends JavaPlugin {

    private Logger log = Logger.getLogger("Minecraft");
    private DFBlockListener blockListener = new DFBlockListener();

    public DFPermissions permission = new DFPermissions();
    public Configuration config = null;


    @Override
    public void onEnable() {
        config = getConfiguration();

        permission.enable(this);
        blockListener.enable(this);

        // If the config file didn't exist, this will write the default back to disk.
        config.save();

        PluginDescriptionFile pdf = this.getDescription();
        logInfo("Version " + pdf.getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        blockListener.disable();
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

}

