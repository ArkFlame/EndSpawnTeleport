package com.arkflame.endspawnteleport;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class EndSpawnTeleport extends JavaPlugin implements Listener {
    
    @Override
    public void onEnable() {
        // Register event listener
        getServer().getPluginManager().registerEvents(this, this);
        
        // Log plugin enable message
        getLogger().info("EndSpawnTeleport plugin has been enabled!");
        getLogger().info("Players will now teleport to End world spawn when using End portals.");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("EndSpawnTeleport plugin has been disabled!");
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        
        // Check if the destination is the End world and the cause is an End portal
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
            
            getLogger().info("Redirected " + player.getName() + " to End world spawn instead of default End platform.");
        }
    }
    
    // Alternative event handler in case the above doesn't catch all cases
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        
        // Check if this is an End portal teleport to the End
        if (to != null && 
            to.getWorld().getEnvironment() == Environment.THE_END && 
            event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            
            // Get the End world
            World endWorld = to.getWorld();
            
            // Check if the destination is the default End platform (coordinates around 100, 50, 0)
            if (isDefaultEndPlatform(to)) {
                // Set the destination to the world spawn location
                Location worldSpawn = endWorld.getSpawnLocation();
                
                // Make sure the spawn location is safe
                worldSpawn.setY(worldSpawn.getY() + 1);
                
                // Update the teleport destination
                event.setTo(worldSpawn);
                
                // Send message to player (optional)
                player.sendMessage("§aTeleporting to End world spawn...");
                
                getLogger().info("Redirected " + player.getName() + " from default End platform to world spawn.");
            }
        }
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