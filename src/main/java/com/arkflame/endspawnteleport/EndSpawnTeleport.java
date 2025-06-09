package com.arkflame.endspawnteleport;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.arkflame.endspawnteleport.utils.Materials;

public class EndSpawnTeleport extends JavaPlugin implements Listener {

    // Map to store portal blocks that need to be restored for each player
    private Map<UUID, Map<Location, Block>> playerPortalBlocks = new HashMap<>();
    // Map to track which portal area each player is currently in
    private Map<UUID, Location> playerCurrentPortal = new HashMap<>();

    @Override
    public void onEnable() {
        // Register event listener
        getServer().getPluginManager().registerEvents(this, this);

        // Log plugin enable message
        getLogger().info("EndSpawnTeleport plugin has been enabled!");
        getLogger().info("Players will now teleport to End world spawn when using End portals.");
        getLogger().info("Portal trapping prevention is active.");
    }

    @Override
    public void onDisable() {
        // Restore all portal blocks before disabling
        restoreAllPortalBlocks();
        getLogger().info("EndSpawnTeleport plugin has been disabled!");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        Location from = event.getFrom();

        // Handle End portal teleportation
        if (to != null &&
                to.getWorld().getEnvironment() == Environment.THE_END &&
                event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {

            // Get the End world
            World endWorld = to.getWorld();

            // Set the destination to the world spawn location
            Location worldSpawn = endWorld.getSpawnLocation();

            // Make sure the spawn location is safe (add small Y offset if needed)
            worldSpawn.setY(worldSpawn.getY() + 1);

            // Update the teleport destination
            event.setTo(worldSpawn);

            // Send message to player (optional)
            player.sendMessage("§aTeleporting to End world spawn...");
        }

        // Handle Nether portal teleportation for trap prevention
        if (to != null &&
                (to.getWorld().getEnvironment() == Environment.NETHER
                        || to.getWorld().getEnvironment() == Environment.NORMAL)
                &&
                event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {

            // Schedule portal block replacement after teleportation
            new BukkitRunnable() {
                @Override
                public void run() {
                    handlePortalTrapPrevention(player, to);
                }
            }.runTaskLater(this, 2L); // Run after 2 ticks to ensure teleportation is complete
        }

        // Disable any portal creation
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL &&
                ((Math.abs(from.getBlockX()) <= 256 &&
                        Math.abs(from.getBlockY()) <= 256 &&
                        Math.abs(from.getBlockZ()) <= 256) ||
                        (Math.abs(to.getBlockX()) <= 256 &&
                                Math.abs(to.getBlockY()) <= 256 &&
                                Math.abs(to.getBlockZ()) <= 256))) {
            event.setTo(to.getWorld().getSpawnLocation());
            if (event.getPortalTravelAgent() != null) event.getPortalTravelAgent().setCanCreatePortal(false);
            event.setPortalTravelAgent(null);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if player has moved to a different block
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            if (event.getFrom().getBlock().getType() == Materials.get("NETHER_PORTAL", "PORTAL") &&
                    event.getTo().getBlock().getType() != Materials.get("NETHER_PORTAL", "PORTAL")) {
                if (playerPortalBlocks.containsKey(playerId)) {
                    restorePortalBlocks(player);
                    playerCurrentPortal.remove(playerId);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Restore portal blocks when player leaves
        restorePortalBlocks(event.getPlayer());
        UUID playerId = event.getPlayer().getUniqueId();
        playerCurrentPortal.remove(playerId);
    }

    /**
     * Handle portal trap prevention by replacing portal blocks with air
     */
    private void handlePortalTrapPrevention(Player player, Location teleportLocation) {
        UUID playerId = player.getUniqueId();

        // First restore any existing portal blocks for this player
        restorePortalBlocks(player);

        // Get the player's feet location
        Location feetLocation = player.getLocation();
        feetLocation.setY(Math.floor(feetLocation.getY())); // Ensure we're at block level

        // Find and replace portal blocks around the player
        Map<Location, Block> portalBlocks = new HashMap<>();

        // Check in a 3x3x3 area around the player's feet
        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) { // Check from feet level to head level + 1
                for (int z = -1; z <= 1; z++) {
                    Location checkLocation = feetLocation.clone().add(x, y, z);
                    Block block = checkLocation.getBlock();

                    // If it's a portal block, replace it with air temporarily
                    if (block.getType() == Materials.get("NETHER_PORTAL", "PORTAL")) {
                        portalBlocks.put(checkLocation.clone(), block);
                        // Send block change to make it appear as air to the player
                        player.sendBlockChange(checkLocation, Material.AIR, (byte) 0);
                    }
                }
            }
        }

        // If we found portal blocks, store them and track the player's location
        if (!portalBlocks.isEmpty()) {
            playerPortalBlocks.put(playerId, portalBlocks);
            playerCurrentPortal.put(playerId, feetLocation.clone());

            // Schedule a safety restore after 30 seconds in case something goes wrong
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (playerPortalBlocks.containsKey(playerId)) {
                        restorePortalBlocks(player);
                        playerCurrentPortal.remove(playerId);
                    }
                }
            }.runTaskLater(this, 600L); // 30 seconds
        }
    }

    /**
     * Restore portal blocks for a specific player
     */
    private void restorePortalBlocks(Player player) {
        UUID playerId = player.getUniqueId();
        Map<Location, Block> portalBlocks = playerPortalBlocks.get(playerId);

        if (portalBlocks != null && !portalBlocks.isEmpty()) {
            for (Map.Entry<Location, Block> entry : portalBlocks.entrySet()) {
                Location location = entry.getKey();
                Block block = entry.getValue();

                // Restore the original block appearance to the player
                player.sendBlockChange(location, block.getType(), block.getData());
            }

            playerPortalBlocks.remove(playerId);
        }
    }

    /**
     * Restore all portal blocks for all players (used on plugin disable)
     */
    private void restoreAllPortalBlocks() {
        for (UUID playerId : new HashSet<>(playerPortalBlocks.keySet())) {
            Player player = getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                restorePortalBlocks(player);
            }
        }
        playerPortalBlocks.clear();
        playerCurrentPortal.clear();
    }

    /**
     * Check if two locations are near each other within a certain distance
     */
    private boolean isNearLocation(Location loc1, Location loc2, double distance) {
        return loc1.getWorld().equals(loc2.getWorld()) && loc1.distance(loc2) <= distance;
    }

    /**
     * Check if the location is near the default End platform
     * Default End platform is typically at coordinates around (100, 50, 0)
     */
    private boolean isDefaultEndPlatform(Location location) {
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        // Check if coordinates are within the typical End platform area
        return (Math.abs(x - 100) < 50 && y >= 48 && y <= 65 && Math.abs(z) < 50);
    }
}