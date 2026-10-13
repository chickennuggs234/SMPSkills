package com.example.skills;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public enum Skill {

    ULTRA_MINER("Ultra Miner", ChatColor.AQUA, Material.DIAMOND_PICKAXE, 11, Arrays.asList(
            "See nearby ores glowing",
            "through walls.")),

    SUPER_PVPER("Super PVPer", ChatColor.RED, Material.NETHERITE_SWORD, 13, Arrays.asList(
            "Deal bonus damage on every",
            "hit, with any weapon.")),

    DRILLER("Driller", ChatColor.GOLD, Material.GOLDEN_PICKAXE, 15, Arrays.asList(
            "Mine faster with a",
            "permanent Haste effect."));

    private final String name;
    private final ChatColor color;
    private final Material iconMaterial;
    private final int menuSlot;
    private final List<String> lore;

    Skill(String name, ChatColor color, Material iconMaterial, int menuSlot, List<String> lore) {
        this.name = name;
        this.color = color;
        this.iconMaterial = iconMaterial;
        this.menuSlot = menuSlot;
        this.lore = lore;
    }

    public String getDisplayName() {
        return color + name;
    }

    public int getMenuSlot() {
        return menuSlot;
    }

    public ItemStack buildIcon() {
        ItemStack item = new ItemStack(iconMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color.toString() + ChatColor.BOLD + name);
        meta.setLore(lore.stream().map(line -> ChatColor.GRAY + line).collect(Collectors.toList()));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public static Skill fromMenuSlot(int slot) {
        for (Skill skill : values()) {
            if (skill.menuSlot == slot) return skill;
        }
        return null;
    }
}