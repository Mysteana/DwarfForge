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


import org.bukkit.configuration.file.FileConfiguration;


class DFConfig {
    private final static String KEY_COOK_TIME = "DwarfForge.cooking-time.default";
    private final static double MAX_COOK_TIME = 9.25;
    private static double cookTime;

    private final static String KEY_REQUIRE_FUEL = "DwarfForge.fuel.require";
    private static boolean requireFuel;

    private final static String KEY_ALLOW_CRAFTED_FUEL = "DwarfForge.fuel.allow-crafted-items";
    private static boolean allowCraftedFuel;

    private final static String KEY_MAX_STACK_HORIZONTAL = "DwarfForge.stack-limit.horizontal";
    private static int maxStackHorizontal;

    private final static String KEY_MAX_STACK_VERTICAL = "DwarfForge.stack-limit.vertical";
    private static int maxStackVertical;

    private final static String KEY_PERMISSIONS_ENABLE = "Permissions.if-available.enable";
    private static boolean enablePermissions;

    private final static String KEY_NOPERMS_OPS_ONLY = "Permissions.if-disabled.ops-only";
    private static boolean opsOnly;


    static void onEnable(FileConfiguration config) {
        //config.options().copyDefaults();

        opsOnly = config.getBoolean(KEY_NOPERMS_OPS_ONLY);
        enablePermissions = config.getBoolean(KEY_PERMISSIONS_ENABLE);
        maxStackVertical = config.getInt(KEY_MAX_STACK_VERTICAL);
        maxStackHorizontal = config.getInt(KEY_MAX_STACK_HORIZONTAL);
        allowCraftedFuel = config.getBoolean(KEY_ALLOW_CRAFTED_FUEL);
        requireFuel = config.getBoolean(KEY_REQUIRE_FUEL);
        cookTime = config.getDouble(KEY_COOK_TIME);

        // Some limits...
        if (maxStackVertical < 0)
          maxStackVertical = 0;
        if (maxStackHorizontal < 0)
          maxStackHorizontal = 0;

        if (cookTime < 0)
          cookTime = 0;
        if (cookTime > MAX_COOK_TIME)
          cookTime = MAX_COOK_TIME;
    }

    static void onDisable() {
    }

    static short cookTime() {
        // Furnace.setCookTime sets time elapsed, NOT time remaining.
        // The config file specifies time remaining, so adjust here.
        return (short) (Utils.SECS * (MAX_COOK_TIME - cookTime));
    }

    static boolean allowCraftedFuel() {
        return allowCraftedFuel;
    }

    static boolean requireFuel() {
        return requireFuel;
    }

    static int maxStackHorizontal() {
        return maxStackHorizontal;
    }

    static int maxStackVertical() {
        return maxStackVertical;
    }

    static boolean enablePermissions() {
        return enablePermissions;
    }

    static boolean opsOnly() {
        return opsOnly;
    }
}

