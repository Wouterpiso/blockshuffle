package com.wpiso.blockshuffle;

import org.bukkit.Material;

public class PlayerData {

    private Material targetBlock; // The block that the player needs to stand on or hold
    private int lives; // The number of lives the player has
    private boolean hasStoodOnCorrectBlock; // Flag to check if the player has stood on the correct block

    // Constructor to initialize the player data with a given number of lives
    public PlayerData(int lives) {
        this.lives = lives;
        this.hasStoodOnCorrectBlock = false; // Initially, player hasn't stood on the correct block
    }

    // Getter for the target block that the player needs to stand on or hold
    public Material getTargetBlock() {
        return targetBlock;
    }

    // Setter for the target block
    public void setTargetBlock(Material targetBlock) {
        this.targetBlock = targetBlock;
    }

    // Getter for the player's remaining lives
    public int getLives() {
        return lives;
    }

    // Setter to update the number of lives
    public void setLives(int lives) {
        this.lives = lives;
    }

    // Getter to check if the player has stood on the correct block
    public boolean hasStoodOnCorrectBlock() {
        return hasStoodOnCorrectBlock;
    }

    // Setter to update the status of whether the player has stood on the correct
    // block
    public void setHasStoodOnCorrectBlock(boolean hasStoodOnCorrectBlock) {
        this.hasStoodOnCorrectBlock = hasStoodOnCorrectBlock;
    }

    // Method to decrease a player's life by 1 if they have any remaining
    public void loseLife() {
        if (lives > 0) {
            lives--;
        }
    }

    // Resets the player's state, specifically the flag for standing on the correct
    // block
    public void reset() {
        this.hasStoodOnCorrectBlock = false;
    }
}