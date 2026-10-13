package com.example.skills;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class SkillsPlugin extends JavaPlugin implements TabCompleter {

    private static final String MARKER_KEY = "smpskills_ore_marker";

    /** Ore block -> glow outline color. */
    private static final Map<Material, ChatColor> ORE_COLORS = new EnumMap<>(Material.class);

    static {
        registerOres(Tag.COAL_ORES, ChatColor.DARK_GRAY);
        registerOres(Tag.IRON_ORES, ChatColor.WHITE);
        registerOres(Tag.COPPER_ORES, ChatColor.GOLD);
        registerOres(Tag.GOLD_ORES, ChatColor.YELLOW);
        registerOres(Tag.REDSTONE_ORES, ChatColor.RED);
        registerOres(Tag.LAPIS_ORES, ChatColor.BLUE);
        registerOres(Tag.DIAMOND_ORES, ChatColor.AQUA);
        registerOres(Tag.EMERALD_ORES, ChatColor.GREEN);
        ORE_COLORS.put(Material.ANCIENT_DEBRIS, ChatColor.LIGHT_PURPLE);
    }

    private final Map<UUID, Skill> skills = new HashMap<>();
    private final Map<UUID, List<Entity>> markers = new HashMap<>();
    private final Map<ChatColor, Team> glowTeams = new EnumMap<>(ChatColor.class);

    private File skillsFile;

    @Override
    public void onEnable() {
        getConfig().addDefault("super-pvper.damage-multiplier", 1.3);
        getConfig().addDefault("driller.haste-amplifier", 2);
        getConfig().addDefault("ultra-miner.radius", 10);
        getConfig().addDefault("ultra-miner.scan-interval-seconds", 3);
        getConfig().options().copyDefaults(true);
        saveConfig();

        loadSkills();

        getServer().getPluginManager().registerEvents(new SkillListener(this), this);
        getCommand("skill").setExecutor(this);
        getCommand("skill").setTabCompleter(this);

        long intervalTicks = Math.max(1, getConfig().getInt("ultra-miner.scan-interval-seconds", 3)) * 20L;
        getServer().getScheduler().runTaskTimer(this, this::updateOreHighlights, 60L, intervalTicks);
        getServer().getScheduler().runTaskTimer(this, this::refreshDrillerHaste, 60L, 20L * 30L);

        getLogger().info("Enabled. " + skills.size() + " player skill(s) loaded.");
    }

    @Override
    public void onDisable() {
        for (UUID id : new ArrayList<>(markers.keySet())) {
            clearMarkers(id);
        }
    }

    // ------------------------------------------------------------------
    // Skill storage (skills.yml)
    // ------------------------------------------------------------------

    private void loadSkills() {
        skillsFile = new File(getDataFolder(), "skills.yml");
        if (!skillsFile.exists()) {
            getDataFolder().mkdirs();
            try {
                skillsFile.createNewFile();
            } catch (IOException ex) {
                getLogger().severe("Could not create skills.yml: " + ex.getMessage());
            }
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(skillsFile);
        ConfigurationSection section = yml.getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                skills.put(UUID.fromString(key), Skill.valueOf(section.getString(key, "").toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveSkills() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Skill> entry : skills.entrySet()) {
            yml.set("players." + entry.getKey(), entry.getValue().name());
        }
        try {
            yml.save(skillsFile);
        } catch (IOException ex) {
            getLogger().severe("Could not save skills.yml: " + ex.getMessage());
        }
    }

    public Skill getSkill(OfflinePlayer player) {
        return skills.get(player.getUniqueId());
    }

    public boolean hasSkill(OfflinePlayer player) {
        return skills.containsKey(player.getUniqueId());
    }

    public void setSkill(Player player, Skill skill) {
        skills.put(player.getUniqueId(), skill);
        saveSkills();
        applyPassives(player);
    }

    public void resetSkill(OfflinePlayer player) {
        skills.remove(player.getUniqueId());
        saveSkills();
        clearMarkers(player.getUniqueId());
        Player online = player.getPlayer();
        if (online != null) {
            online.removePotionEffect(PotionEffectType.FAST_DIGGING);
        }
    }

    // ------------------------------------------------------------------
    // Passive effects
    // ------------------------------------------------------------------

    public void applyPassives(Player player) {
        Skill skill = getSkill(player);
        if (skill == Skill.DRILLER) {
            applyHaste(player);
        } else if (skill == Skill.ULTRA_MINER) {
            highlightOres(player);
        }
    }

    private void refreshDrillerHaste() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (getSkill(player) == Skill.DRILLER) {
                applyHaste(player);
            }
        }
    }

    private void applyHaste(Player player) {
        int amplifier = Math.max(1, getConfig().getInt("driller.haste-amplifier", 2)) - 1;
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.FAST_DIGGING, // renamed HASTE in newer APIs, old name still works
                20 * 45,  // 45s, refreshed every 30s
                amplifier,
                true,     // ambient
                false,    // no particles
                true));   // show icon
    }

    public double getPvpMultiplier() {
        return Math.max(1.0, getConfig().getDouble("super-pvper.damage-multiplier", 1.3));
    }

    // ------------------------------------------------------------------
    // Ultra Miner: glowing ore outlines through walls
    // ------------------------------------------------------------------

    private void updateOreHighlights() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (getSkill(player) == Skill.ULTRA_MINER) {
                highlightOres(player);
            } else {
                clearMarkers(player.getUniqueId());
            }
        }
    }

    private void highlightOres(Player player) {
        clearMarkers(player.getUniqueId());

        int radius = Math.max(1, getConfig().getInt("ultra-miner.radius", 10));
        World world = player.getWorld();
        int cx = player.getLocation().getBlockX();
        int cy = player.getLocation().getBlockY();
        int cz = player.getLocation().getBlockZ();

        List<Player> others = new ArrayList<>(world.getPlayers());
        others.remove(player);

        List<Entity> newMarkers = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(cx + x, cy + y, cz + z);
                    ChatColor glowColor = ORE_COLORS.get(block.getType());
                    if (glowColor == null) continue;

                    // A BlockDisplay placed exactly on the ore block.
                    // Glowing entities render an outline visible through walls.
                    BlockDisplay marker = (BlockDisplay) world.spawnEntity(
                            new Location(world, block.getX(), block.getY(), block.getZ()),
                            EntityType.BLOCK_DISPLAY);
                    marker.setBlock(block.getBlockData());
                    marker.setGlowing(true);
                    marker.setInvulnerable(true);
                    marker.setPersistent(false);
                    marker.setBrightness(new Display.Brightness(15, 15));
                    marker.setMetadata(MARKER_KEY, new FixedMetadataValue(this, true));
                    glowTeam(glowColor).addEntry(marker.getUniqueId().toString());

                    for (Player other : others) {
                        other.hideEntity(this, marker);
                    }
                    newMarkers.add(marker);
                }
            }
        }
        markers.put(player.getUniqueId(), newMarkers);
    }

    public void clearMarkers(UUID playerId) {
        List<Entity> list = markers.remove(playerId);
        if (list == null) return;
        for (Entity marker : list) {
            if (marker.isValid()) marker.remove();
        }
    }

    /** Removes the outline when its ore block gets mined, so no "ghost block" lingers. */
    public void removeMarkerAt(Block block) {
        for (Entity entity : block.getChunk().getEntities()) {
            if (!(entity instanceof BlockDisplay) || !entity.hasMetadata(MARKER_KEY)) continue;
            Location loc = entity.getLocation();
            if (loc.getBlockX() == block.getX()
                    && loc.getBlockY() == block.getY()
                    && loc.getBlockZ() == block.getZ()) {
                entity.remove();
            }
        }
    }

    /** New joiners shouldn't see other players' ore outlines. */
    public void hideMarkersFrom(Player joining) {
        for (Map.Entry<UUID, List<Entity>> entry : markers.entrySet()) {
            if (entry.getKey().equals(joining.getUniqueId())) continue;
            for (Entity marker : entry.getValue()) {
                if (marker.isValid()) joining.hideEntity(this, marker);
            }
        }
    }

    private Team glowTeam(ChatColor color) {
        return glowTeams.computeIfAbsent(color, c -> {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            String name = "sg_" + c.name();
            Team team = board.getTeam(name);
            if (team == null) {
                team = board.registerNewTeam(name);
                team.setColor(c);
            }
            return team;
        });
    }

    // ------------------------------------------------------------------
    // /skill command
    // ------------------------------------------------------------------

    public void msg(CommandSender sender, String text) {
        sender.sendMessage(ChatColor.AQUA + "[Skills] " + ChatColor.GRAY + text);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("info")) {
            if (!sender.hasPermission("skills.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /skill info <player>");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "That player has never joined this server.");
                return true;
            }
            Skill skill = getSkill(target);
            sender.sendMessage(ChatColor.AQUA + "[Skills] " + ChatColor.GRAY + target.getName() + ": "
                    + (skill == null ? ChatColor.YELLOW + "no skill chosen yet" : skill.getDisplayName()));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("skills.admin")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to do that.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /skill reset <player>");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "That player has never joined this server.");
                return true;
            }
            resetSkill(target);
            sender.sendMessage(ChatColor.AQUA + "[Skills] " + ChatColor.GRAY + "Reset "
                    + target.getName() + "'s skill. They can choose again next join or with /skill.");
            return true;
        }

        if (sender instanceof Player player) {
            if (hasSkill(player)) {
                msg(player, "Your skill: " + getSkill(player).getDisplayName()
                        + ChatColor.GRAY + " (permanent)");
            } else {
                SkillMenu.open(player);
            }
        } else {
            sender.sendMessage("Console usage: /skill info <player> | /skill reset <player>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> result = new ArrayList<>();
            if (sender.hasPermission("skills.admin")) {
                for (String sub : List.of("info", "reset")) {
                    if (sub.startsWith(args[0].toLowerCase())) result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2 && sender.hasPermission("skills.admin")
                && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("reset"))) {
            return null; // suggests online player names
        }
        return Collections.emptyList();
    }
}