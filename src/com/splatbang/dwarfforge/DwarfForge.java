package com.splatbang.dwarfforge;

import java.lang.String;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;


public class DwarfForge extends JavaPlugin {
    private static Logger log = Logger.getLogger("Minecraft");
    private static PermissionHandler permissionHandler;
    private Configuration config;
    private DFBlockListener blockListener = new DFBlockListener(this);
    private boolean permsEnable;
    private boolean permsOpsOnly;

    @Override
    public void onEnable() {
        config = getConfiguration();
        permsOpsOnly = config.getBoolean("Permissions.if-disabled.ops-only", false);
        permsEnable = config.getBoolean("Permissions.if-available.enable", true);
        config.save();

        if (permsEnable) {
            setupPermissions();
        }
        else {
            logInfo("Not using Permissions plugin; ops-only? " + permsOpsOnly);
        }

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvent(Event.Type.BLOCK_PLACE,  blockListener, Event.Priority.Normal, this);
        pm.registerEvent(Event.Type.BLOCK_BREAK,  blockListener, Event.Priority.Normal, this);
        pm.registerEvent(Event.Type.BLOCK_DAMAGE, blockListener, Event.Priority.Normal, this);
        pm.registerEvent(Event.Type.BLOCK_IGNITE, blockListener, Event.Priority.Normal, this);

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

    private void setupPermissions() {
        if (permissionHandler == null) {

            Plugin plugin = getServer().getPluginManager().getPlugin("Permissions");
            if (plugin != null) {
                Permissions permPlugin = (Permissions) plugin;
                logInfo("Loaded " + permPlugin.name + ", version " + permPlugin.version);
                permissionHandler = permPlugin.getHandler();
            }
            else {
                logInfo("Permission system not detected.");
            }
        }
    }
    
    public boolean playerHasPermission(Player player, String perm) {
        // Ops ALWAYS have permission
        if (player.isOp())
            return true;

        // Is permissions available? If so, use it.
        if (permissionHandler != null)
            return permissionHandler.has(player, perm);

        // Otherwise, ops only?
        return !permsOpsOnly;
    }

}

