package com.example.skills;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class SkillMenu implements InventoryHolder {

    private final Inventory inventory;

    public SkillMenu() {
        inventory = Bukkit.createInventory(this, 27,
                ChatColor.DARK_AQUA.toString() + ChatColor.BOLD + "Choose Your Skill");

        ItemStack filler = namedPane();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        for (Skill skill : Skill.values()) {
            inventory.setItem(skill.getMenuSlot(), skill.buildIcon());
        }
    }

    private ItemStack namedPane() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public static void open(Player player) {
        player.openInventory(new SkillMenu().getInventory());
    }
}