package com.example.skills;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class SkillListener implements Listener {

    private final SkillsPlugin plugin;

    public SkillListener(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Never see other players' ore outlines.
        plugin.hideMarkersFrom(player);

        if (plugin.hasSkill(player)) {
            plugin.applyPassives(player);
        } else {
            // Open the selection menu shortly after joining.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !plugin.hasSkill(player)) {
                    SkillMenu.open(player);
                }
            }, 20L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.clearMarkers(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SkillMenu)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Only react to clicks inside the menu itself (not the player's own inventory).
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof SkillMenu)) return;

        Skill skill = Skill.fromMenuSlot(event.getSlot());
        if (skill == null) return;

        if (plugin.hasSkill(player)) {
            plugin.msg(player, "You already chose your skill - it can't be changed!");
            player.closeInventory();
            return;
        }

        plugin.setSkill(player, skill);
        player.closeInventory();
        plugin.msg(player, "Skill chosen: " + skill.getDisplayName()
                + ChatColor.GRAY + " - this choice is permanent!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SkillMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SkillMenu)) return;
        Player player = (Player) event.getPlayer();
        if (plugin.hasSkill(player)) return;

        // Closed without choosing -> reopen so everyone picks exactly once.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !plugin.hasSkill(player)) {
                SkillMenu.open(player);
            }
        }, 10L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = null;

        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            attacker = player;
        }

        if (attacker == null || plugin.getSkill(attacker) != Skill.SUPER_PVPER) return;

        event.setDamage(event.getDamage() * plugin.getPvpMultiplier());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // Remove the ore outline instantly so there's no "ghost block".
        plugin.removeMarkerAt(event.getBlock());
    }
}