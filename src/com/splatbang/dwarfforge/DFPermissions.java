package com.splatbang.dwarfforge;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;


public class DFPermissions {

    private PermissionHandler handler = null;
    private boolean opsOnly = false;

    public void enable(DwarfForge main) {
        opsOnly = main.config.getBoolean("Permissions.if-disabled.ops-only", false);
        boolean permsEnable = main.config.getBoolean("Permissions.if-available.enable", true);

        if (permsEnable) {
            Plugin plugin = main.getServer().getPluginManager().getPlugin("Permissions");
            if (plugin == null) {
                main.logInfo("Permissions plugin not detected.");
            }
            else {
                Permissions perm = (Permissions) plugin;
                handler = perm.getHandler();
                try {
                    main.logInfo("Using " + perm.name + ", version " + perm.version);
                }
                catch (NoSuchFieldError e) {
                    // PermissionsEx Compatibility layer doesn't replicate 'name' field.
                    main.logInfo("Using PermissionsEx Compatibility layer, version " + perm.version);
                }
            }
        }
        else {
            main.logInfo("Permissions plugin disabled; ops only? " + opsOnly);
        }
    }

    public void disable() {
        handler = null;
    }

    public boolean allow(Player player, String perm) {
        // Ops ALWAYS have permission
        if (player.isOp())
            return true;

        // Is permissions available? If so, use it.
        if (handler != null)
            return handler.has(player, perm);

        // Otherwise, allow if not ops-only.
        return !opsOnly;
    }
}

