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

import org.bukkit.entity.Player;


public class DFPermissions {

    public void enable(DwarfForge main) {
        if (DFConfig.enablePermissions()) {
            main.logInfo("Using Bukkit permissions.");
        }
        else {
            main.logInfo("Permissions plugin disabled; ops only? " + DFConfig.opsOnly());
        }
    }

    public void disable() {
    }

    public boolean allow(Player player, String perm) {
        // Ops ALWAYS have permission
        if (player.isOp())
            return true;

        // Are permissions enabled? If so, use them.
        if (DFConfig.enablePermissions()) {
            return player.hasPermission(perm);
        }

        // Otherwise, allow if not ops-only.
        return !DFConfig.opsOnly();
    }
}

