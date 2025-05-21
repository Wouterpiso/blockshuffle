package com.wpiso.blockshuffle;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;
import net.kyori.adventure.text.Component;

import java.util.*;

public class BlockShufflePlugin extends JavaPlugin implements Listener {

    private Map<UUID, PlayerData> players = new HashMap<>(); // Stores player data (lives, etc.)
    private List<Material> blockPool = new ArrayList<>(); // List of valid blocks to shuffle
    private int livesPerPlayer = 3; // Default lives per player
    private int roundInterval = 300; // Round duration in seconds
    private BukkitRunnable gameTask; // Handles the ongoing game loop
    private UUID lastDragonDamager = null; // Tracks the last player to damage the EnderDragon
    private boolean gameEnded = false; // Flag to check if the game ended
    private ScoreboardManager scoreboardManager; // Scoreboard manager
    private Scoreboard scoreboard; // The actual scoreboard object
    private Objective objective; // The objective used in the scoreboard

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this); // Register event listeners
        initBlockPool(); // Initialize the block pool with valid blocks
        setupScoreboard(); // Set up the scoreboard to track player lives
    }

    @Override
    public void onDisable() {
        if (gameTask != null) {
            gameTask.cancel(); // Stop the game task if the plugin is disabled
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // Add new players to the game data map
        if (!players.containsKey(uuid)) {
            players.put(uuid, new PlayerData(livesPerPlayer));
        }
    }

    private void initBlockPool() {
        // Populate block pool with all solid blocks in Minecraft
        for (Material material : Material.values()) {
            if (material.isBlock() && material.isSolid()) {
                blockPool.add(material);
            }
        }
    }

    private void setupScoreboard() {
        // Create a new scoreboard and register it
        scoreboardManager = Bukkit.getScoreboardManager();
        scoreboard = scoreboardManager.getNewScoreboard();
        objective = scoreboard.registerNewObjective("blockshuffle", "dummy", Component.text("§aBlock Shuffle Levens"));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR); // Display the scoreboard on the sidebar
    }

    private void updateScoreboard(Player player, int lives) {
        // Update player’s score in the scoreboard
        Score score = objective.getScore(player.getName());
        score.setScore(lives);
        player.setScoreboard(scoreboard);
    }

    private void removeFromScoreboard(Player player) {
        // Remove the player’s score from the scoreboard
        scoreboard.resetScores(player.getName());
        player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard()); // Reset scoreboard after game ends
    }

    private void startBlockShuffleTask() {
        if (gameTask != null) {
            gameTask.cancel(); // Cancel any existing game task
        }

        // Start the game mode based on the number of players
        if (Bukkit.getOnlinePlayers().size() == 1) {
            startSinglePlayerGame(); // Start single-player game mode
        } else {
            startMultiplayerGame(); // Start multiplayer game mode
        }
    }

    private void startCountdown() {
        new BukkitRunnable() {
            int timeLeft = roundInterval; // Countdown timer

            @Override
            public void run() {
                // Send a warning message at 30 seconds remaining
                if (timeLeft == 30) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendMessage("§eWaarschuwing: De tijd is bijna op! Nog maar 30 seconden!");
                    }
                }

                // Countdown from 10 seconds
                if (timeLeft <= 10 && timeLeft > 0) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendMessage("§cNog " + timeLeft + " seconden...");
                    }
                }

                // End the round when time is up
                if (timeLeft <= 1) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendMessage("§cDe tijd is om!");
                    }
                    cancel(); // Stop the countdown
                }

                timeLeft--; // Decrement the timer
            }
        }.runTaskTimer(this, 0L, 20L); // Run every second (20 ticks)
    }

    private void startSinglePlayerGame() {
        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                startCountdown(); // Start countdown

                // Iterate over players and assign new target blocks for each player
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    PlayerData data = players.get(uuid);

                    if (data.getLives() > 0) {
                        Material newTarget = blockPool.get(new Random().nextInt(blockPool.size()));
                        data.setTargetBlock(newTarget);
                        player.sendMessage(
                                "Je moet binnen " + roundInterval + " seconden op: §a" + formatMaterialName(newTarget));
                    }
                }

                // Check player actions (standing on or holding the correct block)
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            UUID uuid = player.getUniqueId();
                            PlayerData data = players.get(uuid);

                            if (data.getLives() > 0) {
                                Block blockUnderPlayer = player.getLocation().clone().add(0, -1,0).getBlock();
                                Block blockPlayer = player.getLocation().getBlock();
                                if (blockUnderPlayer.getType() != data.getTargetBlock()
                                        && blockPlayer.getType() != data.getTargetBlock()
                                        && !data.hasStoodOnCorrectBlock()) {
                                    data.loseLife();
                                    player.getWorld().createExplosion(player.getLocation(), 2F, false, false);
                                    player.setHealth(0.0); // Kill the player

                                    if (data.getLives() <= 0) {
                                        player.sendMessage("§cJe bent uitgeschakeld! Je hebt geen levens meer.");
                                        removeFromScoreboard(player); // Remove from scoreboard if out of lives
                                    } else {
                                        player.sendMessage(
                                                "§cJe stond niet op het juiste blok! Levens over: " + data.getLives());
                                        updateScoreboard(player, data.getLives());
                                    }
                                } else {
                                    data.setHasStoodOnCorrectBlock(true); // Mark that the player stood on the correct
                                                                          // block
                                    player.sendMessage("§aGoed gedaan! Je stond op het juiste blok.");
                                }
                            }
                        }
                    }
                }.runTaskLater(BlockShufflePlugin.this, 20L * roundInterval - 1L); // Check after round time
            }
        };

        gameTask.runTaskTimer(this, 0L, 20L * roundInterval);
    }

    private void startMultiplayerGame() {
        // Multiplayer game logic similar to single-player
        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                startCountdown(); // Start countdown for multiplayer game

                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    PlayerData data = players.get(uuid);

                    if (data.getLives() > 0) {
                        Material newTarget = blockPool.get(new Random().nextInt(blockPool.size()));
                        data.setTargetBlock(newTarget);
                        player.sendMessage(
                                "Je moet binnen " + roundInterval + " seconden op: §a" + formatMaterialName(newTarget));
                    }
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            UUID uuid = player.getUniqueId();
                            PlayerData data = players.get(uuid);

                            if (data.getLives() > 0) {
                                Block blockUnderPlayer = player.getLocation().clone().add(0, -1,0).getBlock();
                                Block blockPlayer = player.getLocation().getBlock();
                                if (blockUnderPlayer.getType() != data.getTargetBlock()
                                        && blockPlayer.getType() != data.getTargetBlock()
                                        && !data.hasStoodOnCorrectBlock()) {
                                    data.loseLife();
                                    player.getWorld().createExplosion(player.getLocation(), 2F, false, false);
                                    player.setHealth(0.0); // Kill the player

                                    if (data.getLives() <= 0) {
                                        player.sendMessage("§cJe bent uitgeschakeld! Je hebt geen levens meer.");
                                        removeFromScoreboard(player);
                                    } else {
                                        player.sendMessage(
                                                "§cJe stond niet op het juiste blok! Levens over: " + data.getLives());
                                        updateScoreboard(player, data.getLives());
                                    }
                                } else {
                                    data.setHasStoodOnCorrectBlock(true);
                                    player.sendMessage("§aGoed gedaan! Je stond op het juiste blok.");
                                }
                            }
                        }

                        // Check for last player standing and end the game
                        List<Player> alive = new ArrayList<>();
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            PlayerData d = players.get(p.getUniqueId());
                            if (d != null && d.getLives() > 0) {
                                alive.add(p);
                            }
                        }

                        if (alive.size() <= 1 && !gameEnded) {
                            if (alive.size() == 1) {
                                endGame(alive.get(0));
                            } else {
                                for (Player p : Bukkit.getOnlinePlayers()) {
                                    p.sendMessage("§6Block Shuffle voorbij! Niemand heeft gewonnen.");
                                }
                                gameTask.cancel();
                                gameEnded = true;
                            }
                        }
                    }
                }.runTaskLater(BlockShufflePlugin.this, 20L * roundInterval - 1L);
            }
        };

        gameTask.runTaskTimer(this, 0L, 20L * roundInterval);
    }

    private String formatMaterialName(Material material) {
        String raw = material.name().toLowerCase().replace("_", " ");
        String[] words = raw.split(" ");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                formatted.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return formatted.toString().trim();
    }

    @EventHandler
    public void onDragonDamaged(EntityDamageByEntityEvent event) {
        // Tracks who damaged the EnderDragon
        if (event.getEntity() instanceof EnderDragon && event.getDamager() instanceof Player) {
            Player damager = (Player) event.getDamager();
            lastDragonDamager = damager.getUniqueId();
        }
    }

    @EventHandler
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent event) {
        // End the game when the Ender Dragon is defeated
        if (!event.getAdvancement().getKey().toString().equals("minecraft:end/complete_ender_dragon") || gameEnded) {
            return;
        }

        Player player = event.getPlayer();
        PlayerData data = players.get(player.getUniqueId());
        if (data == null || data.getLives() <= 0)
            return;

        if (Bukkit.getOnlinePlayers().size() == 1) {
            endGame(player);
        }
    }

    private void endGame(Player winner) {
        if (gameEnded)
            return;
        gameEnded = true;

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage("§6Block Shuffle voorbij! De winnaar is: §a" + winner.getName());
        }

        if (gameTask != null) {
            gameTask.cancel();
        }

        // Remove the scoreboard when the game ends
        for (Player p : Bukkit.getOnlinePlayers()) {
            removeFromScoreboard(p);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("startshuffle")) {
            if (args.length < 2) {
                sender.sendMessage("Gebruik: /startshuffle <levens> <intervalInSeconden>");
                return true;
            }

            try {
                livesPerPlayer = Integer.parseInt(args[0]);
                roundInterval = Integer.parseInt(args[1]);

                for (Player player : Bukkit.getOnlinePlayers()) {
                    players.put(player.getUniqueId(), new PlayerData(livesPerPlayer));
                    updateScoreboard(player, livesPerPlayer);
                    player.setScoreboard(scoreboard);
                }

                gameEnded = false;
                lastDragonDamager = null;
                startBlockShuffleTask();
                sender.sendMessage("Block Shuffle gestart met " + livesPerPlayer + " levens en " + roundInterval
                        + " seconden per ronde.");
            } catch (NumberFormatException e) {
                sender.sendMessage("Gebruik geldige getallen voor levens en tijd.");
            }
            return true;
        }
        return false;
    }
}