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


class Utils {

    static final short SECS = 20;
    static final short MINS = 60 * SECS;

    static final Material[] COOKABLES = new Material[] {
        Material.DIAMOND_ORE,
        Material.IRON_ORE,
        Material.GOLD_ORE,
        Material.SAND,
        Material.COBBLESTONE,
        Material.CLAY_BALL,
        Material.PORK,
        Material.RAW_FISH,
        Material.LOG,
        Material.CACTUS,
    };

    static {
        Arrays.sort(COOKABLES);
    }

    static boolean canCook(Material m) {
        return Arrays.binarySearch(COOKABLES, m) >= 0;
    }

    static BlockFace nextCardinalFace(BlockFace dir) {
        switch (dir) {
            case NORTH:   return BlockFace.EAST;
            case EAST:    return BlockFace.SOUTH;
            case SOUTH:   return BlockFace.WEST;
            case WEST:    return BlockFace.NORTH;
            default:
                throw new IllegalArgumentException("Only cardinal directions permitted: received " + dir);
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

