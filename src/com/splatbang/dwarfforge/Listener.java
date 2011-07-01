package com.splatbang.dwarfforge;

import java.lang.Runnable;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.event.inventory.InventoryListener;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.material.FurnaceAndDispenser;


public class Listener implements Runnable {
    private static final int INVALID_TASK = -1;
    private static final short SECS = 20;
    private static final short MINS = 60 * SECS;

    private static final short ZERO_DURATION = 1;           // Yeah, I should explain this...
    private static final short TASK_DURATION = 1 * SECS;    // This should be less than burn duration.
    private static final short BURN_DURATION =25 * MINS;    // This must be less than max short.

    private static final int RAW_SLOT = 0;
    private static final int FUEL_SLOT = 1;
    private static final int REFINED_SLOT = 2;

    private static final List<BlockFace> DIRS = Arrays.asList(
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    private static final Set<Material> SMELTABLES = new HashSet<Material>(Arrays.asList(
        new Material[] {
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
        }));

    private interface DFListener {
        void onEnable();
        void onDisable();
    }

    private DwarfForge main;
    private DFListener[] listeners = {
        new DFBlockListener(),
        new DFInventoryListener()
    };

    private HashSet<Location> activeForges = new HashSet<Location>();
    private int task = INVALID_TASK;


    public void enable(DwarfForge main) {
        this.main = main;

        for (DFListener listener : listeners) {
            listener.onEnable();
        }

        startTask();
    }

    public void disable() {
        stopTask();

        for (DFListener listener : listeners) {
            listener.onDisable();
        }
    }

    private void startTask() {
        task = main.queueRepeatingTask(this, TASK_DURATION);
    }

    private void stopTask() {
        if (task != INVALID_TASK) {
            main.cancelRepeatingTask(task);
            task = INVALID_TASK;
        }
    }

    @Override
    public void run() {
        Iterator<Location> it = activeForges.iterator();
        while (it.hasNext()) {
            Block forge = it.next().getBlock();

            // It's possible for blocks in this list to change in such a way that
            // they are no longer forges. Remove the remembered forge to forget it.
            if (!isDwarfForge(forge)) {
                it.remove();

                // No longer a forge, but still a burning furnace?
                if (isBurning(forge)) {
                    douse(forge);
                }

                // Not a forge; do nothing else to this particular block.
                continue;
            }

            // Keep the forge burning.
            ignite(forge);
        }
    }

    private boolean isDwarfForge(Block block) {
        // Can't be a Dwarf Forge if it isn't a furnace.
        if (!isBlockOfType(block, Material.FURNACE, Material.BURNING_FURNACE))
            return false;

        // Is lava or another Dwarf Forge below this one?
        Block below = block.getRelative(BlockFace.DOWN);
        return isBlockOfType(below, Material.LAVA, Material.STATIONARY_LAVA)
            || isDwarfForge(below);
    }

    private void ignite(Block forge) {
        Furnace state = (Furnace) forge.getState();

        // Setting the block type causes the furnace to drop
        // inventory. Hence, we save and restore the inventory
        // around the type change.
        if (!isBurning(forge)) {
            Furnace priorState = state;
            ItemStack[] stuff = saveInventory(priorState);
            clearInventory(priorState); // Needed to avoid duping, etc.

            forge.setType(Material.BURNING_FURNACE);

            state = (Furnace) forge.getState();
            restoreInventory(state, stuff);
            state.setData(priorState.getData());
        }

        state.setBurnTime(BURN_DURATION);
        state.update();

        // Anytime we (re-)ignite the furnace, we can attempt to reload
        // raw materials.
        reload(forge);
    }

    private void douse(Block forge) {
        // Easy way to douse a forge is simply to set a "zero" duration.
        Furnace state = (Furnace) forge.getState();
        state.setBurnTime(ZERO_DURATION);
        state.update();
    }

    private boolean isBurning(Block block) {
        return isBlockOfType(block, Material.BURNING_FURNACE);
    }

    private void clearInventory(Furnace furnace) {
        furnace.getInventory().clear();
    }

    private ItemStack[] saveInventory(Furnace furnace) {
        return furnace.getInventory().getContents();
    }

    private void restoreInventory(Furnace furnace, ItemStack[] stuff) {
        furnace.getInventory().setContents(stuff);
    }

    private BlockFace nextFace(BlockFace forward) {
        return DIRS.get((DIRS.indexOf(forward) + 1) % DIRS.size());
    }

    private BlockFace prevFace(BlockFace forward) {
        return DIRS.get((DIRS.indexOf(forward) + 3) % DIRS.size());
    }

    private BlockFace getForward(Block forge) {
        Furnace state = (Furnace) forge.getState();
        return ((FurnaceAndDispenser) state.getData()).getFacing();
    }

    private Block getForgeChest(Block forge, BlockFace dir) {
        // If the adjacent block is a chest, use it.
        Block adjacent = forge.getRelative(dir);
        if (isBlockOfType(adjacent, Material.CHEST))
            return adjacent;

        // If there is a forge below, use its chest.
        Block below = forge.getRelative(BlockFace.DOWN);
        if (isDwarfForge(below))
            return getForgeChest(below, dir);

        // No chest.
        return null;
    }

    private Block getInputChest(Block forge) {
        // Look for a chest stage-right (i.e. "next" face);
        return getForgeChest(forge, nextFace(getForward(forge)));
    }

    private Block getOutputChest(Block forge) {
        // Look for a chest stage-left (i.e. "prev" face).
        return getForgeChest(forge, prevFace(getForward(forge)));
    }

    // Blocks are unloaded into chest stage-left of the forge (i.e. face "previous" to forward).
    private void unload(final Block forge) {
        Furnace state = (Furnace) forge.getState();

        Block output = getOutputChest(forge);
        if (output != null) {
            Chest chest = (Chest) output.getState();

            Inventory forgeInv = state.getInventory();
            Inventory chestInv = chest.getInventory();

            ItemStack item = forgeInv.getItem(REFINED_SLOT);
            int preCount = item.getAmount();

            // Remove the item from the furnace.
            forgeInv.clear(REFINED_SLOT);

            // Add the smelted item to the chest.
            HashMap<Integer,ItemStack> remains = chestInv.addItem(item);

            // Did everything fit?
            if (!remains.isEmpty()) {
                // NO. Put back what remains into the refined slot.
                forgeInv.setItem(REFINED_SLOT, remains.get(0));
            }

            int postCount = forgeInv.getItem(REFINED_SLOT).getAmount();

            // Toggle lever/redstone power.
            if (postCount != preCount) {
                redstoneOn(forge);
                main.queueDelayedTask(new Runnable() {
                    public void run() {
                        redstoneOff(forge);
                    }
                }, 1 * SECS);
            }
        }
    }

    // Blocks are loaded from chest stage-right of the forge (i.e. face "next" from forward).
    private void reload(Block forge) {
        Furnace state = (Furnace) forge.getState();
        Inventory forgeInv = state.getInventory();

        // If the raw material slot in the furnace is occupied, can't reload.
        ItemStack raw = forgeInv.getItem(RAW_SLOT);
        if (raw != null && raw.getType() != Material.AIR)
            return;

        // If there is no input chest, can't reload.
        Block input = getInputChest(forge);
        if (input == null)
            return;

        Chest chest = (Chest) input.getState();
        Inventory chestInv = chest.getInventory();

        // Find the first smeltable item in the chest.
        ItemStack[] allItems = chestInv.getContents();
        for (ItemStack items : allItems) {
            if (items == null)
                continue;

            if (SMELTABLES.contains(items.getType())) {
                // Remove from the chest.
                chestInv.clear(chestInv.first(items));
                // Add to the furnace.
                forgeInv.setItem(RAW_SLOT, items);
                // Reload is done!
                return;
            }
        }
    }

    private void redstoneOff(Block forge) {
        Furnace state = (Furnace) forge.getState();
        BlockFace forward = ((FurnaceAndDispenser) state.getData()).getFacing();

        // Check for a lever behind the forge.
        Block behind = forge.getRelative(forward.getOppositeFace());
        if (isBlockOfType(behind, Material.LEVER)) {
            // Turn the lever to OFF setting.
            behind.setData((byte) (behind.getData() & ~0x8));    // clear ON bit
        }
    }

    private void redstoneOn(Block forge) {
        Furnace state = (Furnace) forge.getState();
        BlockFace forward = ((FurnaceAndDispenser) state.getData()).getFacing();

        // Check for a lever behind the forge.
        Block behind = forge.getRelative(forward.getOppositeFace());
        if (isBlockOfType(behind, Material.LEVER)) {
            // Turn the lever to ON setting.
            behind.setData((byte) (behind.getData() | 0x8));    // set ON bit
        }
    }

    private boolean isBlockOfType(Block block, Material... types) {
        for (Material type : types) {
            if (block.getType() == type)
                return true;
        }
        return false;
    }

    private void toggleForge(Block forge) {
        if (isBurning(forge)) {
            douse(forge);
            activeForges.remove(forge.getLocation());
        }
        else {
            ignite(forge);
            activeForges.add(forge.getLocation());
        }
    }

    //------------------------------

    class DFInventoryListener extends InventoryListener implements DFListener {
        @Override
        public void onEnable() {
            main.registerEvent(Event.Type.FURNACE_SMELT, this, Event.Priority.Monitor);
        }

        @Override
        public void onDisable() { }

        @Override
        public void onFurnaceSmelt(FurnaceSmeltEvent event) {
            // Monitoring event: do nothing if event was cancelled.
            if (event.isCancelled())
                return;

            // Do nothing if the furnace isn't a Dwarf Forge.
            final Block furnace = event.getFurnace();
            if (!isDwarfForge(furnace))
                return;

            // Queue up task to unload and reload the furnace.
            main.queueTask(new Runnable() {
                public void run() {
                    unload(furnace);
                    reload(furnace);
                }
            });
        }
    }

    //------------------------------

    class DFBlockListener extends BlockListener implements DFListener {
        @Override
        public void onEnable() {
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

            if (isBlockOfType(block, Material.FURNACE, Material.BURNING_FURNACE)) {
                attemptToBuildForge = isDwarfForge(block);
            }
            else if (isBlockOfType(block, Material.LAVA, Material.STATIONARY_LAVA)) {
                attemptToBuildForge = isDwarfForge(block.getRelative(BlockFace.UP));
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
            if (!isDwarfForge(block))
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
            if (!isDwarfForge(block))
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
                    toggleForge(block);
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
                        if (isDwarfForge(check)) {
                            // Protect the block; cancel the ignite event.
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }
    }

}

