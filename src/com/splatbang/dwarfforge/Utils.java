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


import java.util.Arrays;
import java.lang.IllegalArgumentException;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import net.minecraft.server.FurnaceRecipes;
import net.minecraft.server.ItemStack;
import org.bukkit.craftbukkit.inventory.CraftItemStack;


class Utils {

    static final short SECS = 20;           // 20 server ticks
    static final short MINS = 60 * SECS;

    // Logs are usually considered a typical fuel, but the Dwarfs were not
    // stupid. Cook logs into charcoal, a much more efficient fuel.
    static private boolean isTypicalFuel(Material m) {
        switch (m) {
            case COAL:
            case WOOD:
            case SAPLING:
            case STICK:
            case LAVA_BUCKET:
                return true;
            default:
                return false;
        }
    }
        
    static private boolean isCraftedFuel(Material m) {
        switch (m) {
            case FENCE:
            case WOOD_STAIRS:
            case TRAP_DOOR:
            case CHEST:
            case LOCKED_CHEST:
            case NOTE_BLOCK:
            case JUKEBOX:
            case BOOKSHELF:
                return true;
            default:
                return false;
        }
    }

    static Material resultOfCooking(Material mat) {
        ItemStack item = FurnaceRecipes.getInstance().a(mat.getId());
        return (item != null)
                ? new CraftItemStack(item).getType()
                : null;
    }

    static boolean canCook(Material m) {
        return resultOfCooking(m) != null;
    }

    static boolean canBurn(Material m) {
        return  isTypicalFuel(m)
            || (isCraftedFuel(m) && DFConfig.allowCraftedFuel());
    }

    static BlockFace nextCardinalFace(BlockFace dir) {
        switch (dir) {
            case NORTH:   return BlockFace.EAST;
            case EAST:    return BlockFace.SOUTH;
            case SOUTH:   return BlockFace.WEST;
            case WEST:    return BlockFace.NORTH;
            default:
                throw new IllegalArgumentException(
                        "Only cardinal directions permitted: received " + dir);
        }
    }

    static BlockFace prevCardinalFace(BlockFace dir) {
        return nextCardinalFace(dir).getOppositeFace();
    }

    static boolean isBlockOfType(Block block, Material... types) {
        for (Material type : types) {
            if (block.getType() == type)
                return true;
        }
        return false;
    }

}

