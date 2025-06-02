// File: core/src/main/java/io/github/game/GameStateManager.java
package io.github.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

public class GameStateManager {

    public enum GameState {
        PLAYING,
        GAME_OVER,
        LEVEL_COMPLETE,
        PAUSED,
        WORLD_MAP, // NEW: World map state
        MAIN_MENU  // NEW: Main menu state (for future use)
    }

    private GameState currentState;
    private GameState previousState; // NEW: Track previous state for returning
    private int lives;
    private int coins;
    private int score;
    private int totalScore; // NEW: Track total score across all levels
    private boolean showLevelCompleteScreen;
    private float levelCompleteTimer;
    private String currentLevelName; // NEW: Track current level name
    private int currentWorld; // NEW: Track current world
    private int currentLevel; // NEW: Track current level number

    // FIXED: Add persistent stats that survive world map transitions
    private int persistentLives; // Lives that persist across world map transitions
    private int persistentTotalScore; // Total score that persists
    private boolean isInLevel; // Track if we're currently in a level

    // UI Elements
    private BitmapFont font;
    private BitmapFont titleFont;
    private BitmapFont smallFont; // NEW: For smaller text
    private ShapeRenderer shapeRenderer;

    // Game Over Screen
    private Rectangle retryButton;
    private Rectangle quitButton;
    private Rectangle worldMapButton; // NEW: Return to world map button
    private boolean retryClicked;
    private boolean quitClicked;
    private boolean worldMapClicked; // NEW

    // Level Complete Screen
    private Rectangle worldMapButtonComplete; // Only world map button
    private boolean worldMapCompleteClicked;

    // Constants
    private static final int STARTING_LIVES = 3;
    private static final int COINS_FOR_LIFE = 100;
    private static final float LEVEL_COMPLETE_AUTO_RETURN_TIME = 5.0f; // Auto return to world map after 5 seconds

    public void update(float deltaTime) {
        if (currentState == GameState.LEVEL_COMPLETE) {
            levelCompleteTimer += deltaTime;

            // Auto return to world map after 5 seconds
            if (levelCompleteTimer >= LEVEL_COMPLETE_AUTO_RETURN_TIME) {
                worldMapCompleteClicked = true;
                Gdx.app.log("GameState", "Auto-returning to World Map after 5 seconds");
            } else {
                handleLevelCompleteInput(); // Handle manual input before auto-return
            }
        }

        if (currentState == GameState.GAME_OVER) {
            handleGameOverInput();
        }

        // Handle pause state
        if (currentState == GameState.PAUSED) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.P) ||
                Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                resumeGame();
            }
        }
    }

    private void handleLevelCompleteInput() {
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            float mouseX = Gdx.input.getX();
            float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();

            if (worldMapButtonComplete.contains(mouseX, mouseY)) {
                worldMapCompleteClicked = true;
                Gdx.app.log("GameState", "World Map button clicked");
            }
        }

        // Keyboard shortcuts - only world map return options
        if (Gdx.input.isKeyJustPressed(Input.Keys.M) ||
            Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) ||
            Gdx.input.isKeyJustPressed(Input.Keys.SPACE) ||
            Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            worldMapCompleteClicked = true;
            Gdx.app.log("GameState", "Return to World Map via keyboard");
        }
    }

    private void handleGameOverInput() {
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            float mouseX = Gdx.input.getX();
            float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY();

            if (retryButton.contains(mouseX, mouseY)) {
                retryClicked = true;
            } else if (worldMapButton.contains(mouseX, mouseY)) {
                worldMapClicked = true;
            } else if (quitButton.contains(mouseX, mouseY)) {
                quitClicked = true;
            }
        }

        // Keyboard shortcuts
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            retryClicked = true;
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            worldMapClicked = true;
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.Q) ||
            Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            quitClicked = true;
        }
    }

    public void renderUI(SpriteBatch batch, OrthographicCamera camera) {
        // Only render HUD during gameplay
        if (currentState == GameState.PLAYING) {
            renderHUD(batch);
        }

        // Render state-specific screens
        switch (currentState) {
            case GAME_OVER:
                renderGameOverScreen(batch);
                break;
            case LEVEL_COMPLETE:
                renderLevelCompleteScreen(batch);
                break;
            case PAUSED:
                renderPauseScreen(batch); // NEW
                break;
        }
    }

    private void renderHUD(SpriteBatch batch) {
        // Set up UI projection
        batch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        batch.begin();
        font.setColor(Color.WHITE);

        // Render HUD (lives, coins, score, level info)
        float hudY = Gdx.graphics.getHeight() - 30f;
        font.draw(batch, "Lives: " + lives, 20f, hudY);
        font.draw(batch, "Coins: " + coins, 150f, hudY);
        font.draw(batch, "Score: " + score, 280f, hudY);

        // NEW: Show current level info
        if (!currentLevelName.isEmpty()) {
            font.draw(batch, currentLevelName, 450f, hudY);
        }

        // NEW: Show total score in smaller text
        smallFont.setColor(Color.LIGHT_GRAY);
        smallFont.draw(batch, "Total: " + totalScore, 20f, hudY - 25f);

        batch.end();
    }

    // NEW: Render pause screen
    private void renderPauseScreen(SpriteBatch batch) {
        // Draw semi-transparent overlay
        shapeRenderer.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0, 0, 0, 0.5f);
        shapeRenderer.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapeRenderer.end();

        batch.begin();
        titleFont.setColor(Color.YELLOW);
        titleFont.draw(batch, "PAUSED",
            Gdx.graphics.getWidth()/2f - 80f,
            Gdx.graphics.getHeight()/2f + 50f);

        font.setColor(Color.WHITE);
        font.draw(batch, "Press P or ESC to resume",
            Gdx.graphics.getWidth()/2f - 120f,
            Gdx.graphics.getHeight()/2f);

        batch.end();
    }

    // FIXED: Enhanced constructor with proper persistent stats initialization
    public GameStateManager() {
        this.currentState = GameState.WORLD_MAP;
        this.previousState = GameState.WORLD_MAP;
        this.lives = STARTING_LIVES;
        this.coins = 0;
        this.score = 0;
        this.totalScore = 0;
        this.showLevelCompleteScreen = false;
        this.levelCompleteTimer = 0f;
        this.currentLevelName = "";
        this.currentWorld = 1;
        this.currentLevel = 1;
        this.isInLevel = false;

        // FIXED: Initialize persistent stats
        this.persistentLives = STARTING_LIVES;
        this.persistentTotalScore = 0;

        this.font = new BitmapFont();
        this.titleFont = new BitmapFont();
        this.smallFont = new BitmapFont();
        this.shapeRenderer = new ShapeRenderer();
        this.glyphLayout = new GlyphLayout();

        // Set default font scales
        this.font.getData().setScale(1.2f);
        this.titleFont.getData().setScale(2.0f);
        this.smallFont.getData().setScale(0.9f);

        // FIXED: Initialize all buttons to prevent null pointer exceptions
        initializeButtons();
    }

    private void renderGameOverScreen(SpriteBatch batch) {
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();

        // Draw semi-transparent overlay
        shapeRenderer.getProjectionMatrix().setToOrtho2D(0, 0, screenWidth, screenHeight);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0, 0, 0, 0.85f);
        shapeRenderer.rect(0, 0, screenWidth, screenHeight);
        shapeRenderer.end();

        // Simple centered panel
        float panelWidth = 600f;
        float panelHeight = 400f;
        float panelX = (screenWidth - panelWidth) / 2f;
        float panelY = (screenHeight - panelHeight) / 2f;

        // Draw panel background
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.1f, 0.1f, 0.15f, 0.95f);
        shapeRenderer.rect(panelX, panelY, panelWidth, panelHeight);
        shapeRenderer.end();

        // Draw panel border
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.RED);
        Gdx.gl.glLineWidth(3f);
        shapeRenderer.rect(panelX, panelY, panelWidth, panelHeight);
        shapeRenderer.end();
        Gdx.gl.glLineWidth(1f);

        // Update button positions
        float buttonWidth = 140f;
        float buttonHeight = 45f;
        float buttonSpacing = 20f;
        float buttonY = panelY + 40f;

        retryButton.set(panelX + panelWidth/2f - buttonWidth*1.5f - buttonSpacing, buttonY, buttonWidth, buttonHeight);
        worldMapButton.set(panelX + panelWidth/2f - buttonWidth/2f, buttonY, buttonWidth, buttonHeight);
        quitButton.set(panelX + panelWidth/2f + buttonWidth/2f + buttonSpacing, buttonY, buttonWidth, buttonHeight);

        // Draw buttons
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.2f, 0.7f, 0.2f, 0.9f); // Green
        shapeRenderer.rect(retryButton.x, retryButton.y, retryButton.width, retryButton.height);
        shapeRenderer.setColor(0.2f, 0.4f, 0.8f, 0.9f); // Blue
        shapeRenderer.rect(worldMapButton.x, worldMapButton.y, worldMapButton.width, worldMapButton.height);
        shapeRenderer.setColor(0.8f, 0.2f, 0.2f, 0.9f); // Red
        shapeRenderer.rect(quitButton.x, quitButton.y, quitButton.width, quitButton.height);
        shapeRenderer.end();

        // Button borders
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.rect(retryButton.x, retryButton.y, retryButton.width, retryButton.height);
        shapeRenderer.rect(worldMapButton.x, worldMapButton.y, worldMapButton.width, worldMapButton.height);
        shapeRenderer.rect(quitButton.x, quitButton.y, quitButton.width, quitButton.height);
        shapeRenderer.end();

        // FIXED: Simple, reliable text rendering
        batch.getProjectionMatrix().setToOrtho2D(0, 0, screenWidth, screenHeight);
        batch.begin();

        // Reset font scales to default
        titleFont.getData().setScale(2.0f);
        font.getData().setScale(1.5f);
        smallFont.getData().setScale(1.0f);

        // Draw title
        titleFont.setColor(Color.RED);
        titleFont.draw(batch, "GAME OVER", panelX + 150f, panelY + panelHeight - 50f);

        // Draw score info
        font.setColor(Color.YELLOW);
        font.draw(batch, "Final Score: " + score, panelX + 50f, panelY + panelHeight - 120f);

        font.setColor(Color.CYAN);
        font.draw(batch, "Total Score: " + totalScore, panelX + 50f, panelY + panelHeight - 160f);

        // Button labels - using simple positioning
        smallFont.setColor(Color.WHITE);
        smallFont.draw(batch, "RETRY", retryButton.x + 35f, retryButton.y + 28f);
        smallFont.draw(batch, "WORLD MAP", worldMapButton.x + 20f, worldMapButton.y + 28f);
        smallFont.draw(batch, "QUIT", quitButton.x + 40f, quitButton.y + 28f);

        // Controls
        smallFont.setColor(Color.GRAY);
        smallFont.draw(batch, "R: Retry  M: World Map  Q: Quit", panelX + 50f, panelY + 20f);

        batch.end();
    }

    private void renderLevelCompleteScreen(SpriteBatch batch) {
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();

        // Draw overlay
        shapeRenderer.getProjectionMatrix().setToOrtho2D(0, 0, screenWidth, screenHeight);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0, 0, 0, 0.8f);
        shapeRenderer.rect(0, 0, screenWidth, screenHeight);
        shapeRenderer.end();

        // Main panel
        float panelWidth = 700f;
        float panelHeight = 500f;
        float panelX = (screenWidth - panelWidth) / 2f;
        float panelY = (screenHeight - panelHeight) / 2f;

        // Draw panel
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.05f, 0.15f, 0.25f, 0.98f);
        shapeRenderer.rect(panelX, panelY, panelWidth, panelHeight);
        shapeRenderer.end();

        // Panel border
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.GOLD);
        Gdx.gl.glLineWidth(4f);
        shapeRenderer.rect(panelX, panelY, panelWidth, panelHeight);
        shapeRenderer.end();
        Gdx.gl.glLineWidth(1f);

        // Update world map button position - centered since it's the only button
        float buttonWidth = 200f;
        float buttonHeight = 60f;
        float buttonY = panelY + 60f;

        if (worldMapButtonComplete == null) {
            worldMapButtonComplete = new Rectangle();
        }

        worldMapButtonComplete.set(panelX + (panelWidth - buttonWidth) / 2f, buttonY, buttonWidth, buttonHeight);

        // Draw world map button
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.2f, 0.5f, 0.9f, 0.95f); // Blue
        shapeRenderer.rect(worldMapButtonComplete.x, worldMapButtonComplete.y, worldMapButtonComplete.width, worldMapButtonComplete.height);
        shapeRenderer.end();

        // Button border
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.WHITE);
        Gdx.gl.glLineWidth(2f);
        shapeRenderer.rect(worldMapButtonComplete.x, worldMapButtonComplete.y, worldMapButtonComplete.width, worldMapButtonComplete.height);
        shapeRenderer.end();
        Gdx.gl.glLineWidth(1f);

        // FIXED: Simple, reliable text rendering
        batch.getProjectionMatrix().setToOrtho2D(0, 0, screenWidth, screenHeight);
        batch.begin();

        // Reset font scales
        titleFont.getData().setScale(2.5f);
        font.getData().setScale(1.3f);
        smallFont.getData().setScale(1.0f);

        // Animated title
        titleFont.setColor(Color.YELLOW);
        float pulse = 1.0f + 0.2f * (float)Math.sin(levelCompleteTimer * 4f);
        titleFont.getData().setScale(2.5f * pulse);
        titleFont.draw(batch, "LEVEL COMPLETE!", panelX + 100f, panelY + panelHeight - 50f);
        titleFont.getData().setScale(2.5f); // Reset

        // Level info
        float textY = panelY + panelHeight - 130f;
        float lineSpacing = 35f;

        font.setColor(Color.WHITE);
        if (!currentLevelName.isEmpty()) {
            font.draw(batch, "Level: " + currentLevelName, panelX + 50f, textY);
            textY -= lineSpacing;
        }

        font.setColor(Color.CYAN);
        font.draw(batch, "Level Score: " + String.format("%,d", score), panelX + 50f, textY);
        textY -= lineSpacing;

        font.setColor(Color.GOLD);
        font.draw(batch, "Coins Collected: " + coins, panelX + 50f, textY);
        textY -= lineSpacing;

        font.setColor(Color.YELLOW);
        font.draw(batch, "Total Score: " + String.format("%,d", totalScore), panelX + 50f, textY);
        textY -= lineSpacing;

        font.setColor(Color.PINK);
        font.draw(batch, "Lives: " + lives, panelX + 50f, textY);
        textY -= lineSpacing;

        // Time bonus
        if (levelCompleteTimer < 60f) {
            font.setColor(Color.GREEN);
            int timeBonus = (int)((60f - levelCompleteTimer) * 10);
            font.draw(batch, "TIME BONUS: +" + String.format("%,d", timeBonus), panelX + 50f, textY);
        }

        // Auto-return countdown timer
        float timeRemaining = LEVEL_COMPLETE_AUTO_RETURN_TIME - levelCompleteTimer;
        if (timeRemaining > 0) {
            font.setColor(Color.ORANGE);
            String countdownText = "Returning to World Map in " + String.format("%.1f", timeRemaining) + "s";
            font.draw(batch, countdownText, panelX + 50f, panelY + panelHeight - 430f);
        }

        // World map button label
        font.setColor(Color.WHITE);
        font.draw(batch, "WORLD MAP", worldMapButtonComplete.x + 50f, worldMapButtonComplete.y + 38f);

        // Instructions
        smallFont.setColor(Color.LIGHT_GRAY);
        smallFont.draw(batch, "Click button or press any key to return to World Map", panelX + 50f, panelY + 30f);

        batch.end();
    }

    // ENHANCED: Better button initialization with proper scaling
    private void initializeButtons() {
        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();

        // Scale factors for different screen sizes
        float scaleFactor = Math.min(screenWidth / 1920f, screenHeight / 1080f);
        scaleFactor = Math.max(scaleFactor, 0.7f);

        float buttonHeight = 35f * scaleFactor;
        float buttonSpacing = 10f * scaleFactor;
        float uiElementMargin = 15f * scaleFactor;

        // Game Over buttons with better scaling
        float gameOverButtonWidth = 200f * scaleFactor;
        float gameOverButtonHeight = 60f * scaleFactor;
        float gameOverButtonSpacing = 25f * scaleFactor;

        retryButton = new Rectangle(
            screenWidth/2f - gameOverButtonWidth*1.5f - gameOverButtonSpacing,
            screenHeight/2f - gameOverButtonHeight/2f - 80f * scaleFactor,
            gameOverButtonWidth,
            gameOverButtonHeight
        );

        worldMapButton = new Rectangle(
            screenWidth/2f - gameOverButtonWidth/2f,
            screenHeight/2f - gameOverButtonHeight/2f - 80f * scaleFactor,
            gameOverButtonWidth,
            gameOverButtonHeight
        );

        quitButton = new Rectangle(
            screenWidth/2f + gameOverButtonWidth/2f + gameOverButtonSpacing,
            screenHeight/2f - gameOverButtonHeight/2f - 80f * scaleFactor,
            gameOverButtonWidth,
            gameOverButtonHeight
        );

        // FIXED: Initialize only world map button for level complete (no next level button)
        float levelCompleteButtonWidth = 200f * scaleFactor;
        float levelCompleteButtonHeight = 60f * scaleFactor;

        // Initialize world map button centered (will be updated in renderLevelCompleteScreen)
        worldMapButtonComplete = new Rectangle(
            screenWidth/2f - levelCompleteButtonWidth/2f,
            screenHeight/2f - 100f * scaleFactor,
            levelCompleteButtonWidth,
            levelCompleteButtonHeight
        );
    }

    // NEW: Check if next level is available
    private boolean checkNextLevelAvailable() {
        return hasNextLevel; // This boolean should be set by the main game
    }

    // Add these fields to the GameStateManager class (if not already present)
    private boolean hasNextLevel = true;
    private String nextLevelName = "";

    // Methods to set next level information
    public void setNextLevelAvailable(boolean available) {
        this.hasNextLevel = available;
    }

    public void setNextLevelName(String nextLevelName) {
        this.nextLevelName = nextLevelName;
    }

    // ENHANCED: Level completion method simplified for world map return only
    public void completeLevel() {
        currentState = GameState.LEVEL_COMPLETE;
        showLevelCompleteScreen = true;
        levelCompleteTimer = 0f;
        addScore(1000); // Bonus for completing level

        // Add time bonus if completed quickly
        if (levelCompleteTimer < 60f) {
            int timeBonus = (int)((60f - levelCompleteTimer) * 10);
            addScore(timeBonus);
        }

        // FIXED: Update persistent stats when completing level
        persistentLives = lives;
        persistentTotalScore = totalScore;

        worldMapCompleteClicked = false;

        SoundManager.getInstance().playItemGet();
        Gdx.app.log("GameState", "Level completed! Will return to world map in " + LEVEL_COMPLETE_AUTO_RETURN_TIME + " seconds");
    }

    // UPDATED: dispose method to include GlyphLayout
    public void dispose() {
        if (font != null) font.dispose();
        if (titleFont != null) titleFont.dispose();
        if (smallFont != null) smallFont.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
        // Note: GlyphLayout doesn't need disposal
    }

    private GlyphLayout glyphLayout; // Add this field

    // Game state methods
    public void collectCoin() {
        coins++;
        score += 50; // 50 points per coin

        // Give extra life every 100 coins
        if (coins % COINS_FOR_LIFE == 0) {
            addLife();
        }
    }

    public void addScore(int points) {
        score += points;
        totalScore += points; // NEW: Also add to total score
    }

    public void addLife() {
        lives++;
        persistentLives = lives; // FIXED: Update persistent lives
        SoundManager.getInstance().playItemGet();
        Gdx.app.log("GameState", "Extra life earned! Lives: " + lives);
    }

    public void loseLife() {
        lives--;
        Gdx.app.log("GameState", "Life lost! Lives remaining: " + lives);

        if (lives <= 0) {
            triggerGameOver();
        }
    }

    public void triggerGameOver() {
        currentState = GameState.GAME_OVER;
        retryClicked = false;
        quitClicked = false;
        worldMapClicked = false; // NEW
        SoundManager.getInstance().stopMusic();
        Gdx.app.log("GameState", "Game Over! Final Score: " + score);
    }

    // FIXED: Enhanced reset methods for better state management
    public void resetGame() {
        previousState = currentState; // NEW: Store previous state
        currentState = GameState.PLAYING;
        lives = STARTING_LIVES;
        coins = 0;
        score = 0;
        // Don't reset totalScore - it persists across game sessions
        showLevelCompleteScreen = false;
        levelCompleteTimer = 0f;
        retryClicked = false;
        quitClicked = false;
        worldMapClicked = false; // NEW
        worldMapCompleteClicked = false;
        isInLevel = true; // FIXED: Mark as in level

        // FIXED: Reset persistent stats to starting values
        persistentLives = STARTING_LIVES;
    }

    // NEW: Method to enter a level (called when entering from world map)
    public void enterLevel(String levelName, int world, int level) {
        isInLevel = true;
        currentState = GameState.PLAYING;

        // FIXED: Restore persistent stats when entering level
        lives = persistentLives;
        totalScore = persistentTotalScore;

        // Reset level-specific stats
        coins = 0;
        score = 0;

        setCurrentLevel(levelName, world, level);
        clearButtonStates();

        Gdx.app.log("GameState", "Entered level: " + levelName + " with " + lives + " lives");
    }

    // NEW: Method to exit to world map (preserves progress)
    public void exitToWorldMap() {
        isInLevel = false;
        currentState = GameState.WORLD_MAP;

        // FIXED: Update persistent stats before leaving level
        persistentLives = lives;
        persistentTotalScore = totalScore;

        clearButtonStates();

        Gdx.app.log("GameState", "Exited to world map. Lives preserved: " + persistentLives);
    }

    // NEW: Reset only the level-specific progress
    public void resetLevel() {
        if (isInLevel) {
            // Reset level stats but keep persistent progress
            coins = 0;
            score = persistentTotalScore; // Start level score from persistent total
            lives = persistentLives; // Use persistent lives
            showLevelCompleteScreen = false;
            levelCompleteTimer = 0f;

            Gdx.app.log("GameState", "Level reset. Lives: " + lives + ", Total Score: " + totalScore);
        }
    }

    public void pauseGame() {
        if (currentState == GameState.PLAYING) {
            previousState = currentState;
            currentState = GameState.PAUSED;
        }
    }

    public void resumeGame() {
        if (currentState == GameState.PAUSED) {
            currentState = previousState;
        }
    }

    // NEW: Set current level info
    public void setCurrentLevel(String levelName, int world, int level) {
        this.currentLevelName = levelName;
        this.currentWorld = world;
        this.currentLevel = level;
    }

    // NEW: Enter world map mode
    public void enterWorldMap() {
        exitToWorldMap(); // Use the enhanced exit method
    }

    // FIXED: Enhanced getters that handle persistent stats
    public GameState getCurrentState() { return currentState; }
    public GameState getPreviousState() { return previousState; } // NEW
    public int getLives() { return lives; }
    public int getCoins() { return coins; }
    public int getScore() { return score; }
    public int getTotalScore() { return totalScore; } // NEW
    public int getPersistentLives() { return persistentLives; } // NEW
    public String getCurrentLevelName() { return currentLevelName; } // NEW
    public int getCurrentWorld() { return currentWorld; } // NEW
    public int getCurrentLevel() { return currentLevel; } // NEW
    public boolean isInLevel() { return isInLevel; } // NEW
    public boolean isRetryClicked() { return retryClicked; }
    public boolean isQuitClicked() { return quitClicked; }
    public boolean isWorldMapClicked() { return worldMapClicked; } // NEW
    public boolean isWorldMapCompleteClicked() { return worldMapCompleteClicked; } // Only world map option for level complete
    public boolean isGameOver() { return currentState == GameState.GAME_OVER; }
    public boolean isPlaying() { return currentState == GameState.PLAYING; }
    public boolean isLevelComplete() { return currentState == GameState.LEVEL_COMPLETE; }
    public boolean isPaused() { return currentState == GameState.PAUSED; } // NEW
    public boolean isOnWorldMap() { return currentState == GameState.WORLD_MAP; } // NEW

    // Setters
    public void setState(GameState state) {
        this.previousState = this.currentState;
        this.currentState = state;
    }
    public void setLives(int lives) {
        this.lives = lives;
        if (isInLevel) {
            this.persistentLives = lives; // Update persistent lives if in level
        }
    }
    public void setCoins(int coins) { this.coins = coins; }
    public void setScore(int score) { this.score = score; }
    public void setTotalScore(int totalScore) {
        this.totalScore = totalScore;
        this.persistentTotalScore = totalScore; // Update persistent total
    }

    // NEW: Clear button click states
    public void clearButtonStates() {
        retryClicked = false;
        quitClicked = false;
        worldMapClicked = false;
        worldMapCompleteClicked = false;
    }
}
