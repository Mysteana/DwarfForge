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


import java.lang.Runnable;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;


class DFBlockListener extends BlockListener implements DFListener {
    private DwarfForge main;

    @Override
    public void onEnable(DwarfForge main) {
        this.main = main;

        main.registerEvent(Event.Type.BLOCK_PLACE,  this, Event.Priority.Normal);
        main.registerEvent(Event.Type.BLOCK_BREAK,  this, Event.Priority.Normal);
        main.registerEvent(Event.Type.BLOCK_DAMAGE, this, Event.Priority.Monitor);
        main.registerEvent(Event.Type.BLOCK_IGNITE, this, Event.Priority.Normal);
    }

    @Override
    public void onDisable() { }

    @Override
    public void onBlockPlace(BlockPlaceEvent event) {
        // If the event was already cancelled, we're not going to change that status.
        if (event.isCancelled())
            return;

        Block block = event.getBlockPlaced();
        boolean attemptToBuildForge = false;

        if (Utils.isBlockOfType(block, Material.FURNACE, Material.BURNING_FURNACE)) {
            attemptToBuildForge = Forge.isValid(block);
        }
        else if (Utils.isBlockOfType(block, Material.LAVA, Material.STATIONARY_LAVA)) {
            attemptToBuildForge = Forge.isValid(block.getRelative(BlockFace.UP));
        }

        // If the player was not attempting to build a Dwarf Forge, ignore the event.
        if (!attemptToBuildForge)
            return;

        // Does the player have permission?
        Player player = event.getPlayer();
        if (!main.permission.allow(player, "dwarfforge.create")) {
            // No: cancel the event.
            event.setCancelled(true);
            player.sendMessage("Ye have not the strength of the Dwarfs to create such a forge.");
        }
    }

    @Override
    public void onBlockBreak(BlockBreakEvent event) {
        // If event was already cancelled, we're not going to change that status.
        if (event.isCancelled())
            return;

        // If the player was not attempting to destroy a Dwarf Forge, ignore the event.
        Block block = event.getBlock();
        if (!Forge.isValid(block))
            return;

        // Does the player have permission?
        Player player = event.getPlayer();
        if (!main.permission.allow(player, "dwarfforge.destroy")) {
            // NO: cancel the event.
            event.setCancelled(true);
            player.sendMessage("Ye have not the might of the Dwarfs to destroy such a forge.");
        }
    }

    @Override
    public void onBlockDamage(BlockDamageEvent event) {
        // Monitoring event: do nothing if event was cancelled.
        if (event.isCancelled())
            return;

        // Do nothing if the furnace isn't a Dwarf Forge.
        final Block block = event.getBlock();
        if (!Forge.isValid(block))
            return;

        // Do nothing if the player hasn't permission to use the forge.
        // Note that we do NOT cancel the event; only this plugin does no further work.
        Player player = event.getPlayer();
        if (!main.permission.allow(player, "dwarfforge.use")) {
            player.sendMessage("Ye have not the will of the Dwarfs to use such a forge.");
            return;
        }

        // Queue up task to toggle the forge.
        main.queueTask(new Runnable() {
            public void run() {
                Listener.toggleForge(block);
            }
        });
    }

    @Override
    public void onBlockIgnite(BlockIgniteEvent event) {
        // If event was already cancelled, we're not going to change that status.
        if (event.isCancelled())
            return;

        // Ignore event if lava was not the cause.
        if (event.getCause() != IgniteCause.LAVA)
            return;

        // If there is any Dwarf Forge within 3 radius, cancel the event.
        // Yes, it's possible other exposed lava also nearby caused the
        // event, but let's assume the Dwarfs are protecting the area around
        // the Dwarf forge sufficiently.
        Block block = event.getBlock();
        for (int dx = -3; dx <= 3; ++dx) {
            for (int dy = -3; dy <= 3; ++dy) {
                for (int dz = -3; dz <= 3; ++dz) {
                    Block check = block.getRelative(dx, dy, dz);
                    if (Forge.isValid(check)) {
                        // Protect the block; cancel the ignite event.
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }
}
