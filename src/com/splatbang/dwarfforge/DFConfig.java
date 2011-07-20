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


import org.bukkit.util.config.Configuration;


class DFConfig {
    private static Configuration config;

    private final static String KEY_COOK_TIME = "DwarfForge.cooking-time.default";
    private final static double DEFAULT_COOK_TIME = 9.25;
    private static double cookTime;

    private final static String KEY_REQUIRE_FUEL = "DwarfForge.fuel.require";
    private final static boolean DEFAULT_REQUIRE_FUEL = false;
    private static boolean requireFuel;

    private final static String KEY_ALLOW_CRAFTED_FUEL = "DwarfForge.fuel.allow-crafted-items";
    private final static boolean DEFAULT_ALLOW_CRAFTED_FUEL = false;
    private static boolean allowCraftedFuel;

    private final static String KEY_PERMISSIONS_ENABLE = "Permissions.if-available.enable";
    private final static boolean DEFAULT_PERMISSIONS_ENABLE = false;
    private static boolean enablePermissions;

    private final static String KEY_NOPERMS_OPS_ONLY = "Permissions.if-disabled.ops-only";
    private final static boolean DEFAULT_NOPERMS_OPS_ONLY = false;
    private static boolean opsOnly;


    static void onEnable(Configuration cfg) {
        config = cfg;

        opsOnly = config.getBoolean(KEY_NOPERMS_OPS_ONLY, DEFAULT_NOPERMS_OPS_ONLY);
        enablePermissions = config.getBoolean(KEY_PERMISSIONS_ENABLE, DEFAULT_PERMISSIONS_ENABLE);
        allowCraftedFuel = config.getBoolean(KEY_ALLOW_CRAFTED_FUEL, DEFAULT_ALLOW_CRAFTED_FUEL);
        requireFuel = config.getBoolean(KEY_REQUIRE_FUEL, DEFAULT_REQUIRE_FUEL);
        cookTime = config.getDouble(KEY_COOK_TIME, DEFAULT_COOK_TIME);

        // If the config file didn't exist, this will write a default back to disk.
        config.save();
    }

    static void onDisable() {
        config = null;
    }

    static double cookTime() {
        // As a parameter to setCookTime, we want to set time elapsed: NOT time remaining.
        return DEFAULT_COOK_TIME - cookTime;    
    }

    static boolean allowCraftedFuel() {
        return allowCraftedFuel;
    }

    static boolean requireFuel() {
        return requireFuel;
    }

    static boolean enablePermissions() {
        return enablePermissions;
    }

    static boolean opsOnly() {
        return opsOnly;
    }
}

