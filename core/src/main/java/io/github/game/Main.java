// File: core/src/main/java/io/github/game/Main.java
package io.github.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

// Import specific enemy classes
import io.github.game.enemies.Enemy;
import io.github.game.enemies.Goomba;
import io.github.game.enemies.Koopa;

public class Main extends ApplicationAdapter {
    // World and Game Mechanics Constants
    private static final float WORLD_WIDTH = 800;
    private static final float WORLD_HEIGHT = 480;
    private static final float GRAVITY = -900f;
    private static final float MOVE_SPEED = 200f;
    private static final float JUMP_INITIAL_IMPULSE = 380f;
    private static final float JUMP_HOLD_GRAVITY_MULTIPLIER = 0.40f;
    private static final float MAX_JUMP_HOLD_TIME = 0.22f;
    public static final float SCALED_BLOCK_SIZE = 32f;
    private static final float PLAYER_STOMP_BOUNCE_VELOCITY = 250f;

    // Game States
    public enum GameMode {
        WORLD_MAP,
        PLAYING_LEVEL,
        LEVEL_EDITOR // Debug only
    }

    // Core Game Objects
    private SpriteBatch batch;
    private OrthographicCamera camera;
    private Viewport viewport;
    private Player player;
    private Background background;
    private Array<Platform> activePlatforms;
    private Array<Enemy> activeEnemies;// FIXED: Key methods for Main.java to replace existing methods

    private void loadLevelFromWorldMap(String levelName) {
        Gdx.app.log("Main", "Loading level: " + levelName);

        // Get the level file name from the world map
        String levelFileName = worldMap.getCurrentLevelFileName();
        if (levelFileName == null || levelFileName.isEmpty()) {
            Gdx.app.error("Main", "No level file name provided for: " + levelName);
            currentGameMode = GameMode.WORLD_MAP;
            return;
        }

        // Load the level using the world map's level loading system
        try {
            currentLevel = worldMap.loadLevelByFileName(levelFileName);
            if (currentLevel != null) {
                loadCurrentLevelData();
                currentGameMode = GameMode.PLAYING_LEVEL;

                // FIXED: Use enhanced GameStateManager method to enter level
                // Get current node info for proper state management
                WorldMapNode currentNode = null;
                for (WorldMapNode node : worldMap.getNodes()) {
                    if (node.getLevelName().equals(levelName)) {
                        currentNode = node;
                        break;
                    }
                }

                if (currentNode != null) {
                    gameStateManager.enterLevel(levelName, currentNode.getWorldNumber(), currentNode.getLevelNumber());
                } else {
                    // Fallback if node not found
                    gameStateManager.enterLevel(levelName, 1, 1);
                }

                // Play level music
                if (currentLevel.getBackgroundMusic() != null && !currentLevel.getBackgroundMusic().isEmpty()) {
                    soundManager.playMusic(currentLevel.getBackgroundMusic(), currentLevel.getMusicVolume());
                } else {
                    soundManager.stopMusic();
                }
            } else {
                Gdx.app.error("Main", "Failed to load level: " + levelName);
                currentGameMode = GameMode.WORLD_MAP;
                soundManager.stopMusic();
            }
        } catch (Exception e) {
            Gdx.app.error("Main", "Error loading level: " + levelName, e);
            currentGameMode = GameMode.WORLD_MAP;
            soundManager.stopMusic();
        }
    }

    // FIXED: Enhanced return to world map method
    private void returnToWorldMap() {
        currentGameMode = GameMode.WORLD_MAP;
        soundManager.stopMusic();

        // FIXED: Use enhanced GameStateManager method to exit to world map
        gameStateManager.exitToWorldMap();

        levelCompleted = false;
        gamePaused = false;

        // Ensure world map edit mode is disabled when returning
        if (worldMap.isEditMode()) {
            worldMap.setEditMode(false);
        }

        Gdx.app.log("Main", "Returned to World Map. Lives preserved: " + gameStateManager.getPersistentLives());
    }
    // FIXED: Enhanced level completion handling - simplified for world map return only
    private void handleLevelCompletion() {
        if (!levelCompleted) {
            levelCompleted = true;

            // Simply complete the level - world map will handle progression
            gameStateManager.completeLevel();
            soundManager.stopMusic();

            // Update world map progress
            if (currentLevel != null) {
                worldMap.completeLevel(currentLevel.getName());
            }

            Gdx.app.log("Game", "Level Completed! Returning to world map in 5 seconds...");
        }
    }


    // FIXED: Enhanced render level method with better state management
    private void renderLevel(float deltaTime) {
        // Handle game state updates
        gameStateManager.update(deltaTime);

        // FIXED: Enhanced game over and level complete handling
        if (gameStateManager.isGameOver()) {
            if (gameStateManager.isRetryClicked()) {
                gameStateManager.resetLevel(); // Use enhanced reset that preserves world map progress
                respawnPlayerAndResetLevel();
            } else if (gameStateManager.isQuitClicked()) {
                returnToWorldMap();
            } else if (gameStateManager.isWorldMapClicked()) { // NEW: Handle world map button
                returnToWorldMap();
            }
        }

        // FIXED: Simplified level complete handling - only world map return
        if (gameStateManager.isLevelComplete()) {
            if (gameStateManager.isWorldMapCompleteClicked()) {
                returnToWorldMap();
            }
        }

        if (!gamePaused && !levelCompleted && gameStateManager.isPlaying()) {
            updateGameLogic(deltaTime);
        } else {
            player.update(deltaTime);
            for (int i = activeEnemies.size - 1; i >= 0; i--) {
                Enemy enemy = activeEnemies.get(i);
                if (enemy.getCurrentState() == Enemy.EnemyState.STOMPED) {
                    enemy.update(deltaTime, activePlatforms, player);
                    if (!enemy.isAlive()) {
                        activeEnemies.removeIndex(i);
                    }
                }
            }
            for (int i = activePowerups.size - 1; i >= 0; i--) {
                Powerup powerup = activePowerups.get(i);
                powerup.update(deltaTime, activePlatforms);
                if (!powerup.isActive()) {
                    powerup.dispose();
                    activePowerups.removeIndex(i);
                }
            }
            if (goalPost != null) {
                goalPost.update(deltaTime);
            }
        }

        // FIXED: Enhanced player death handling
        if (player.getCurrentState() == Player.State.DEATH && player.getStateTimer() > Player.DEATH_ANIMATION_DURATION) {
            gameStateManager.loseLife();
            if (!gameStateManager.isGameOver()) {
                respawnPlayerAndResetLevel();
            }
        }

        // Camera following logic (unchanged)
        if (!levelCompleted && gameStateManager.isPlaying()) {
            camera.position.x = player.getPosition().x + Player.PLAYER_WIDTH / 2f + 100f;
            camera.position.y = Math.max(WORLD_HEIGHT / 2f, player.getPosition().y + Player.PLAYER_HEIGHT / 2f - WORLD_HEIGHT / 4f + 50f);
            camera.position.x = Math.max(camera.viewportWidth / 2f, camera.position.x);
            camera.position.y = Math.max(camera.viewportHeight / 2f, camera.position.y);
        }
        camera.update();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        background.render(batch, camera);
        for (Platform item : activePlatforms) item.render(batch);
        for (Enemy enemy : activeEnemies) enemy.render(batch);
        for (Powerup powerup : activePowerups) powerup.render(batch);
        if (goalPost != null) goalPost.render(batch);
        player.render(batch);
        batch.end();

        gameStateManager.renderUI(batch, camera);
    }


    // FIXED: Enhanced respawn method that works with persistent stats
    private void respawnPlayerAndResetLevel() {
        gamePaused = false;
        levelCompleted = false;
        if (currentLevel == null) return;

        player.respawn(currentLevel.getPlayerStartX(), currentLevel.getPlayerStartY());

        // Reset enemies
        activeEnemies.clear();
        if (goombaWalkFrame1Tex != null && goombaWalkFrame2Tex != null && goombaSquashedTex != null) {
            for (Level.EnemyData data : currentLevel.getEnemyData()) {
                if ("GOOMBA".equals(data.type)) {
                    activeEnemies.add(new Goomba(goombaWalkFrame1Tex, goombaWalkFrame2Tex, goombaSquashedTex, data.x, data.y));
                } else if ("KOOPA".equals(data.type)) {
                    if (koopaWalkFrame1Tex != null && koopaWalkFrame2Tex != null && koopaShellIdleTex != null &&
                        koopaShellMove1Tex != null && koopaShellMove2Tex != null &&
                        koopaShellMove3Tex != null && koopaShellMove4Tex != null) {
                        activeEnemies.add(new Koopa(koopaWalkFrame1Tex, koopaWalkFrame2Tex, koopaShellIdleTex,
                            koopaShellMove1Tex, koopaShellMove2Tex, koopaShellMove3Tex, koopaShellMove4Tex,
                            data.x, data.y));
                    }
                }
            }
        }

        // Reset powerups
        for (Powerup powerup : activePowerups) {
            powerup.dispose();
        }
        activePowerups.clear();
        for (Level.PowerupData data : currentLevel.getPowerupData()) {
            try {
                Powerup.PowerupType type = Powerup.PowerupType.valueOf(data.type);
                activePowerups.add(new Powerup(type, data.x, data.y));
            } catch (IllegalArgumentException e) {
                Gdx.app.error("RespawnLevel", "Unknown powerup type: " + data.type);
            }
        }

        // Reset goal post
        if (goalPost != null) {
            goalPost.dispose();
        }
        goalPost = currentLevel.createGoalPost();

        // Reset platforms
        activePlatforms.clear();
        activePlatforms.addAll(currentLevel.createPlatforms());

        camera.position.x = player.getPosition().x + Player.PLAYER_WIDTH / 2f + 100f;
        camera.position.y = Math.max(WORLD_HEIGHT / 2f, player.getPosition().y + Player.PLAYER_HEIGHT / 2f);
        camera.update();

        if (currentLevel.getBackgroundMusic() != null && !currentLevel.getBackgroundMusic().isEmpty()) {
            soundManager.playMusic(currentLevel.getBackgroundMusic(), currentLevel.getMusicVolume());
        } else {
            soundManager.stopMusic();
        }
        playerIsHoldingJumpKey = false;
        playerJumpHoldTimer = 0f;

        Gdx.app.log("Main", "Level respawned. Lives: " + gameStateManager.getLives());
    }
    private Array<Powerup> activePowerups;
    private GoalPost goalPost;

    // Individual textures for Goomba
    private Texture goombaWalkFrame1Tex;
    private Texture goombaWalkFrame2Tex;
    private Texture goombaSquashedTex;

    // Individual textures for Koopa
    private Texture koopaWalkFrame1Tex;
    private Texture koopaWalkFrame2Tex;
    private Texture koopaShellIdleTex;
    private Texture koopaShellMove1Tex;
    private Texture koopaShellMove2Tex;
    private Texture koopaShellMove3Tex;
    private Texture koopaShellMove4Tex;

    // Game Systems
    private WorldMap worldMap; // NEW: World map system
    private LevelEditor levelEditor; // DEBUG ONLY
    private GameStateManager gameStateManager;
    private GameMode currentGameMode;
    private Level currentLevel; // Current level being played

    // Debug Mode - FIXED: Better debug mode detection
    private boolean debugMode = false;
    private boolean gamePaused = false;
    private boolean levelCompleted = false;
    private boolean showDebugInfo = false; // NEW: Toggle for debug info display

    // Utility
    private SoundManager soundManager;
    private Texture playerIdleTexture;

    // Player jump state variables
    private boolean playerIsHoldingJumpKey = false;
    private float playerJumpHoldTimer = 0f;

    @Override
    public void create() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0);

        batch = new SpriteBatch();
        soundManager = SoundManager.getInstance();
        player = new Player(150, Platform.GROUND_TILE_SIZE * 2);

        // FIXED: Better debug mode detection
        debugMode = checkDebugMode();
        Gdx.app.log("Main", "Debug mode: " + (debugMode ? "ENABLED" : "DISABLED"));

        try {
            playerIdleTexture = new Texture(Gdx.files.internal("mario_sprites/playables/mario/mario_idle.png"));
            loadEnemyTextures();
        } catch (Exception e) {
            Gdx.app.error("MainCreate", "Failed to load essential textures", e);
        }

        background = new Background();
        activePlatforms = new Array<>();
        activeEnemies = new Array<>();
        activePowerups = new Array<>();

        gameStateManager = new GameStateManager();

        // Initialize world map system - FIXED: Pass debug mode properly
        worldMap = new WorldMap(debugMode);
        currentGameMode = GameMode.WORLD_MAP;

        // Initialize level editor only in debug mode
        if (debugMode) {
            levelEditor = new LevelEditor();
            Gdx.app.log("Main", "Debug mode enabled - Level editor available (F1), World map editor available (F2)");
        }

        Gdx.app.log("Main", "Game initialized. Starting on world map.");

        // NEW: Show debug controls info
        if (debugMode) {
            Gdx.app.log("Main", "=== DEBUG CONTROLS ===");
            Gdx.app.log("Main", "F1: Level Editor");
            Gdx.app.log("Main", "F2: World Map Editor");
            Gdx.app.log("Main", "F3: Toggle Debug Info");
            Gdx.app.log("Main", "ESC: Return to World Map");
            Gdx.app.log("Main", "======================");
        }
    }

    private boolean checkDebugMode() {
        // FIXED: Multiple ways to enable debug mode

        // 1. Check system property
        String debugProperty = System.getProperty("debug");
        if ("true".equals(debugProperty)) {
            Gdx.app.log("Main", "Debug mode enabled via system property");
            return true;
        }

        // 2. Check for debug file
        if (Gdx.files.local("debug.txt").exists()) {
            Gdx.app.log("Main", "Debug mode enabled via debug.txt file");
            return true;
        }

        // 3. Check for development environment (existence of levels directory)
        if (Gdx.files.local("levels/").exists()) {
            Gdx.app.log("Main", "Debug mode enabled - levels directory detected");
            return true;
        }

        // 4. Hardcoded for development (change to false for release)
        boolean hardcodedDebug = true; // SET TO FALSE FOR RELEASE
        if (hardcodedDebug) {
            Gdx.app.log("Main", "Debug mode enabled - hardcoded for development");
            return true;
        }

        return false;
    }

    private void loadEnemyTextures() {
        // Load Goomba Textures
        goombaWalkFrame1Tex = new Texture(Gdx.files.internal("mario_sprites/enemies/goomba_walk_0.png"));
        goombaWalkFrame2Tex = new Texture(Gdx.files.internal("mario_sprites/enemies/goomba_walk_1.png"));

        if (Gdx.files.internal("mario_sprites/enemies/goomba_squashed.png").exists()) {
            goombaSquashedTex = new Texture(Gdx.files.internal("mario_sprites/enemies/goomba_squashed.png"));
        } else {
            goombaSquashedTex = goombaWalkFrame1Tex;
            Gdx.app.log("MainCreate", "goomba_squashed.png not found, using goomba_walk_0.png as placeholder.");
        }

        // Load Koopa Textures
        koopaWalkFrame1Tex = new Texture(Gdx.files.internal("mario_sprites/enemies/koopa/koopa_walk_0.png"));
        koopaWalkFrame2Tex = new Texture(Gdx.files.internal("mario_sprites/enemies/koopa/koopa_walk_1.png"));
        koopaShellIdleTex = new Texture(Gdx.files.internal("mario_sprites/enemies/koopa/red_shell_idle.png"));
        koopaShellMove1Tex = new Texture(Gdx.files.internal("mario_sprites/enemies/koopa/red_shell_move_0.png"));
        koopaShellMove2Tex = new Texture(Gdx.files.internal("mario_sprites/enemies/koopa/red_shell_move_1.png"));
        koopaShellMove3Tex = new Texture(Gdx.files.internal("mario_sprites/enemies/koopa/red_shell_move_2.png"));
        koopaShellMove4Tex = new Texture(Gdx.files.internal("mario_sprites/enemies/koopa/red_shell_move_3.png"));
    }


    private Level loadLevelByName(String levelName) {
        // Map level names to actual level files
        String fileName = mapLevelNameToFile(levelName);

        try {
            if (Gdx.files.local("levels/" + fileName).exists()) {
                com.badlogic.gdx.utils.Json json = new com.badlogic.gdx.utils.Json();
                return json.fromJson(Level.class, Gdx.files.local("levels/" + fileName).readString());
            } else {
                // Create a default level if file doesn't exist
                return createDefaultLevel(levelName);
            }
        } catch (Exception e) {
            Gdx.app.error("Main", "Failed to load level file: " + fileName, e);
            return createDefaultLevel(levelName);
        }
    }

    private String mapLevelNameToFile(String levelName) {
        // Map world map level names to actual level files
        switch (levelName) {
            case "Level 1-1": return "level_1_1.json";
            case "Level 1-2": return "level_1_2.json";
            case "Level 1-3": return "level_1_3.json";
            case "Level 1-4": return "level_1_4.json";
            case "Level 2-1": return "level_2_1.json";
            case "Level 2-2": return "level_2_2.json";
            case "Level 2-3": return "level_2_3.json";
            case "Level 2-4": return "level_2_4.json";
            case "Level 3-1": return "level_3_1.json";
            case "Level 3-2": return "level_3_2.json";
            case "Level 3-3": return "level_3_3.json";
            case "Level 3-4": return "level_3_4.json";
            case "Level 4-1": return "level_4_1.json";
            case "Level 4-2": return "level_4_2.json";
            case "Level 4-3": return "level_4_3.json";
            case "Level 4-4": return "level_4_4.json";
            case "Warp Zone": return "warp_zone.json";
            default: return "default_level.json";
        }
    }

    private Level createDefaultLevel(String levelName) {
        Gdx.app.log("Main", "Creating default level for: " + levelName);

        Level level = new Level(levelName);

        // Add basic ground platform
        Level.PlatformData ground = new Level.PlatformData(
            0, 0, 32f * 20, 32f * 2, Platform.PlatformType.GROUND
        );
        level.addPlatform(ground);

        // Add some basic platforms and enemies based on level name
        if (levelName.contains("1-")) {
            // World 1 style - simple grass levels
            addWorld1Elements(level);
        } else if (levelName.contains("2-")) {
            // World 2 style - desert levels
            addWorld2Elements(level);
        } else if (levelName.contains("3-")) {
            // World 3 style - water levels
            addWorld3Elements(level);
        } else if (levelName.contains("4-")) {
            // World 4 style - ice levels
            addWorld4Elements(level);
        }

        // Add goal post at the end
        level.setGoalPost(32f * 18, 32f * 2);

        return level;
    }

    private void addWorld1Elements(Level level) {
        // Add some basic platforms
        level.addPlatform(new Level.PlatformData(200, 96, 32, 32, Platform.PlatformType.GRAVEL_BLOCK));
        level.addPlatform(new Level.PlatformData(300, 128, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
        level.addPlatform(new Level.PlatformData(400, 96, 96, 32, Platform.PlatformType.GRAVEL_BLOCK));

        // Add some enemies
        level.addEnemy(new Level.EnemyData(250, 64, "GOOMBA"));
        level.addEnemy(new Level.EnemyData(350, 64, "GOOMBA"));

        // Add some powerups
        level.addPowerup(new Level.PowerupData(300, 160, "MUSHROOM"));
    }

    private void addWorld2Elements(Level level) {
        // Desert theme - more challenging
        level.addPlatform(new Level.PlatformData(150, 128, 32, 32, Platform.PlatformType.GRAVEL_BLOCK));
        level.addPlatform(new Level.PlatformData(250, 160, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
        level.addPlatform(new Level.PlatformData(350, 96, 128, 32, Platform.PlatformType.GRAVEL_BLOCK));

        // Add Koopa enemies for variety
        level.addEnemy(new Level.EnemyData(200, 64, "KOOPA"));
        level.addEnemy(new Level.EnemyData(300, 64, "GOOMBA"));
        level.addEnemy(new Level.EnemyData(400, 128, "KOOPA"));
    }

    private void addWorld3Elements(Level level) {
        // Water theme
        level.addPlatform(new Level.PlatformData(180, 128, 64, 32, Platform.PlatformType.GRAVEL_BLOCK));
        level.addPlatform(new Level.PlatformData(300, 160, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
        level.addPlatform(new Level.PlatformData(420, 128, 64, 32, Platform.PlatformType.GRAVEL_BLOCK));

        level.addEnemy(new Level.EnemyData(220, 160, "GOOMBA"));
        level.addEnemy(new Level.EnemyData(460, 160, "KOOPA"));

        level.addPowerup(new Level.PowerupData(300, 192, "FIRE_FLOWER"));
    }

    private void addWorld4Elements(Level level) {
        // Ice theme - most challenging
        level.addPlatform(new Level.PlatformData(160, 160, 32, 32, Platform.PlatformType.GRAVEL_BLOCK));
        level.addPlatform(new Level.PlatformData(240, 128, 32, 32, Platform.PlatformType.QUESTION_BLOCK));
        level.addPlatform(new Level.PlatformData(320, 160, 32, 32, Platform.PlatformType.GRAVEL_BLOCK));
        level.addPlatform(new Level.PlatformData(400, 96, 96, 32, Platform.PlatformType.GRAVEL_BLOCK));

        level.addEnemy(new Level.EnemyData(200, 192, "KOOPA"));
        level.addEnemy(new Level.EnemyData(280, 64, "GOOMBA"));
        level.addEnemy(new Level.EnemyData(360, 192, "KOOPA"));
        level.addEnemy(new Level.EnemyData(440, 128, "GOOMBA"));

        level.addPowerup(new Level.PowerupData(240, 160, "STAR"));
    }

    private void loadCurrentLevelData() {
        if (currentLevel == null) return;

        activePlatforms.clear();
        activePlatforms.addAll(currentLevel.createPlatforms());

        activeEnemies.clear();
        if (goombaWalkFrame1Tex != null && goombaWalkFrame2Tex != null && goombaSquashedTex != null) {
            for (Level.EnemyData data : currentLevel.getEnemyData()) {
                if ("GOOMBA".equals(data.type)) {
                    activeEnemies.add(new Goomba(goombaWalkFrame1Tex, goombaWalkFrame2Tex, goombaSquashedTex, data.x, data.y));
                } else if ("KOOPA".equals(data.type)) {
                    if (koopaWalkFrame1Tex != null && koopaWalkFrame2Tex != null && koopaShellIdleTex != null &&
                        koopaShellMove1Tex != null && koopaShellMove2Tex != null &&
                        koopaShellMove3Tex != null && koopaShellMove4Tex != null) {
                        activeEnemies.add(new Koopa(koopaWalkFrame1Tex, koopaWalkFrame2Tex, koopaShellIdleTex,
                            koopaShellMove1Tex, koopaShellMove2Tex, koopaShellMove3Tex, koopaShellMove4Tex,
                            data.x, data.y));
                    }
                }
            }
        }

        // Load powerups from level data
        activePowerups.clear();
        for (Level.PowerupData data : currentLevel.getPowerupData()) {
            try {
                Powerup.PowerupType type = Powerup.PowerupType.valueOf(data.type);
                activePowerups.add(new Powerup(type, data.x, data.y));
            } catch (IllegalArgumentException e) {
                Gdx.app.error("LoadLevelData", "Unknown powerup type: " + data.type);
            }
        }

        // Load goal post
        if (goalPost != null) {
            goalPost.dispose();
        }
        goalPost = currentLevel.createGoalPost();
        levelCompleted = false;

        background.setTexture(currentLevel.getBackgroundTexturePath());
        player.respawn(currentLevel.getPlayerStartX(), currentLevel.getPlayerStartY());
        player.getVelocity().set(0,0);

        gamePaused = false;
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0.4f, 0.7f, 1, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        float deltaTime = Gdx.graphics.getDeltaTime();

        handleGlobalInput();

        switch (currentGameMode) {
            case WORLD_MAP:
                renderWorldMap(deltaTime);
                break;
            case PLAYING_LEVEL:
                renderLevel(deltaTime);
                break;
            case LEVEL_EDITOR:
                if (debugMode) {
                    renderLevelEditor(deltaTime);
                } else {
                    currentGameMode = GameMode.WORLD_MAP;
                }
                break;
        }
    }

    private void handleGlobalInput() {
        // FIXED: Comprehensive global debug controls
        if (debugMode) {
            // F1: Level Editor
            if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
                if (currentGameMode != GameMode.LEVEL_EDITOR) {
                    currentGameMode = GameMode.LEVEL_EDITOR;
                    soundManager.stopMusic();
                    Gdx.app.log("Main", "Switched to Level Editor (Debug Mode)");
                } else {
                    returnToWorldMap();
                }
            }

            // FIXED: F2: World Map Editor
            if (Gdx.input.isKeyJustPressed(Input.Keys.F2)) {
                if (currentGameMode == GameMode.WORLD_MAP) {
                    // Toggle world map edit mode
                    worldMap.toggleEditMode();
                    if (worldMap.isEditMode()) {
                        Gdx.app.log("Main", "World Map Edit Mode ENABLED");
                        Gdx.app.log("Main", "Controls: Click/drag nodes, WASD camera, F3 save, ESC/F2 exit");
                    } else {
                        Gdx.app.log("Main", "World Map Edit Mode DISABLED");
                    }
                } else {
                    // Switch to world map first
                    returnToWorldMap();
                    Gdx.app.log("Main", "Returned to World Map. Press F2 again to enter edit mode.");
                }
            }

            // NEW: F3: Toggle debug info display
            if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
                showDebugInfo = !showDebugInfo;
                Gdx.app.log("Main", "Debug info display: " + (showDebugInfo ? "ON" : "OFF"));
            }
        }

        // ESC to return to world map from level or level editor
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (currentGameMode == GameMode.PLAYING_LEVEL ||
                (currentGameMode == GameMode.LEVEL_EDITOR && debugMode)) {
                returnToWorldMap();
            } else if (currentGameMode == GameMode.WORLD_MAP && worldMap.isEditMode()) {
                // Exit world map edit mode
                worldMap.setEditMode(false);
                Gdx.app.log("Main", "Exited World Map Edit Mode");
            }
        }
    }



    private void renderWorldMap(float deltaTime) {
        // FIXED: Pass camera to world map for proper edit mode functionality
        worldMap.update(deltaTime, camera);

        // Check if player selected a level
        if (worldMap.isLevelSelectionMode()) {
            String selectedLevel = worldMap.getCurrentLevelName();
            if (selectedLevel != null) {
                loadLevelFromWorldMap(selectedLevel);
                worldMap.setLevelSelectionMode(false);
            }
        }

        worldMap.render(batch, camera);

        // NEW: Render debug info if enabled
        if (debugMode && showDebugInfo) {
            renderWorldMapDebugInfo();
        }
    }

    // NEW: Debug info for world map
    private void renderWorldMapDebugInfo() {
        batch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.begin();

        // You would need a font here - this is just conceptual
        // In a real implementation, you'd use a BitmapFont

        batch.end();
    }

    private void renderLevelEditor(float deltaTime) {
        if (!debugMode || levelEditor == null) {
            currentGameMode = GameMode.WORLD_MAP;
            return;
        }

        if (levelEditor.hasUnappliedChanges()) {
            // Load level editor changes if needed
            currentLevel = levelEditor.getCurrentLevel();
            loadCurrentLevelData();
            levelEditor.clearChangesFlag();
        }

        levelEditor.update(camera);

        if (levelEditor.getCurrentUIMode() == LevelEditor.EditorUIMode.EDITING) {
            camera.update();
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            background.render(batch, camera);
            for (Platform item : activePlatforms) item.render(batch);
            if (goalPost != null) goalPost.render(batch);
            batch.end();
            levelEditor.renderEditorElements(batch, camera);
        } else if (levelEditor.getCurrentUIMode() == LevelEditor.EditorUIMode.LEVEL_PREVIEW) {
            Level editorLevel = levelEditor.getCurrentLevel();
            background.setTexture(editorLevel.getBackgroundTexturePath());

            float targetX = editorLevel.getPlayerStartX() + Player.PLAYER_WIDTH / 2f;
            float targetY = editorLevel.getPlayerStartY() + Player.PLAYER_HEIGHT / 2f;
            camera.position.set(targetX + 100, Math.max(WORLD_HEIGHT / 2f, targetY + 50), 0);
            camera.update();

            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            background.render(batch, camera);
            for (Platform p : activePlatforms) p.render(batch);
            for (Enemy enemy : activeEnemies) enemy.render(batch);
            for (Powerup powerup : activePowerups) powerup.render(batch);
            if (goalPost != null) goalPost.render(batch);
            if (playerIdleTexture != null) {
                batch.draw(playerIdleTexture, editorLevel.getPlayerStartX(), editorLevel.getPlayerStartY(), Player.PLAYER_WIDTH, Player.PLAYER_HEIGHT);
            }
            batch.end();
            levelEditor.renderPreviewNotification(batch);
        }
    }

    private void updateGameLogic(float deltaTime) {
        if (player.getCurrentState() == Player.State.DEATH) {
            gamePaused = true;
            return;
        }

        handlePlayerInput(deltaTime);

        float effectiveGravity = GRAVITY;
        boolean jumpKeyIsCurrentlyHeld = Gdx.input.isKeyPressed(Input.Keys.UP) ||
            Gdx.input.isKeyPressed(Input.Keys.W) ||
            Gdx.input.isKeyPressed(Input.Keys.SPACE);

        if (playerIsHoldingJumpKey && jumpKeyIsCurrentlyHeld && player.getVelocity().y > 0 && playerJumpHoldTimer < MAX_JUMP_HOLD_TIME) {
            effectiveGravity = GRAVITY * JUMP_HOLD_GRAVITY_MULTIPLIER;
            playerJumpHoldTimer += deltaTime;
        } else {
            playerIsHoldingJumpKey = false;
        }
        player.getVelocity().y += effectiveGravity * deltaTime;

        player.getPosition().x += player.getVelocity().x * deltaTime;
        player.getBounds().setX(player.getPosition().x);
        checkHorizontalCollisions(player, activePlatforms);

        player.getPosition().y += player.getVelocity().y * deltaTime;
        player.getBounds().setY(player.getPosition().y);
        checkVerticalCollisions(player, activePlatforms);

        player.update(deltaTime);
        collectCoins();

        // Update enemies
        for (int i = activeEnemies.size - 1; i >= 0; i--) {
            Enemy enemy = activeEnemies.get(i);
            enemy.update(deltaTime, activePlatforms, player);
            if (!enemy.isAlive() && enemy.getCurrentState() == Enemy.EnemyState.DEAD) {
                activeEnemies.removeIndex(i);
            }
        }

        checkShellEnemyCollisions();
        checkPlayerEnemyCollisions();

        // Update powerups
        for (int i = activePowerups.size - 1; i >= 0; i--) {
            Powerup powerup = activePowerups.get(i);
            powerup.update(deltaTime, activePlatforms);
            if (!powerup.isActive()) {
                powerup.dispose();
                activePowerups.removeIndex(i);
            }
        }
        checkPlayerPowerupCollisions();

        // Update goal post and check completion
        if (goalPost != null) {
            goalPost.update(deltaTime);
            if (goalPost.checkPlayerCollision(player.getBounds())) {
                handleLevelCompletion();
            }
        }

        if (player.getPosition().y < -Player.PLAYER_HEIGHT * 2 && player.getCurrentState() != Player.State.DEATH) {
            player.die();
        }
    }

    // All the existing methods remain the same
    private void handlePlayerInput(float deltaTime) {
        if (player.getCurrentState() == Player.State.DEATH || levelCompleted || !gameStateManager.isPlaying()) return;

        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) {
            player.getVelocity().x = MOVE_SPEED;
            player.setFacingRight(false);
            if (player.isGrounded()) player.setCurrentState(Player.State.WALKING);
        } else if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) {
            player.getVelocity().x = -MOVE_SPEED;
            player.setFacingRight(true);
            if (player.isGrounded()) player.setCurrentState(Player.State.WALKING);
        } else {
            player.getVelocity().x = 0;
            if (player.isGrounded() && player.getCurrentState() == Player.State.WALKING) {
                player.setCurrentState(Player.State.IDLE);
            }
        }

        boolean jumpKeyPressedThisFrame = Gdx.input.isKeyJustPressed(Input.Keys.UP) ||
            Gdx.input.isKeyJustPressed(Input.Keys.W) ||
            Gdx.input.isKeyJustPressed(Input.Keys.SPACE);

        if (jumpKeyPressedThisFrame && player.isGrounded()) {
            player.getVelocity().y = JUMP_INITIAL_IMPULSE;
            player.setGrounded(false);
            player.setCurrentState(Player.State.JUMPING);
            soundManager.playJump();
            playerIsHoldingJumpKey = true;
            playerJumpHoldTimer = 0f;
        }

        boolean duckKeyPressed = Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S);
        if (duckKeyPressed) {
            if (player.isGrounded() && player.getCurrentState() != Player.State.DUCKING) {
                player.setCurrentState(Player.State.DUCKING);
                player.getVelocity().x *= 0.5f;
            }
        } else {
            if (player.getCurrentState() == Player.State.DUCKING) {
                player.setCurrentState(player.getVelocity().x != 0 ? Player.State.WALKING : Player.State.IDLE);
            }
        }
    }

    private void checkHorizontalCollisions(Player gamePlayer, Array<Platform> platforms) {
        Rectangle playerRect = gamePlayer.getBounds();
        for (Platform platform : platforms) {
            if (platform.getType() == Platform.PlatformType.COIN) continue;
            Rectangle platformRect = platform.getBounds();
            if (playerRect.overlaps(platformRect)) {
                if (gamePlayer.getVelocity().x > 0) {
                    gamePlayer.getPosition().x = platformRect.x - playerRect.width;
                } else if (gamePlayer.getVelocity().x < 0) {
                    gamePlayer.getPosition().x = platformRect.x + platformRect.width;
                }
                gamePlayer.getVelocity().x = 0;
                gamePlayer.getBounds().setX(gamePlayer.getPosition().x);
            }
        }
    }

    private void checkVerticalCollisions(Player gamePlayer, Array<Platform> platforms) {
        gamePlayer.setGrounded(false);
        Rectangle playerRect = gamePlayer.getBounds();
        float playerOldY = gamePlayer.getPosition().y - (gamePlayer.getVelocity().y * Gdx.graphics.getDeltaTime());

        for (Platform platform : platforms) {
            if (platform.getType() == Platform.PlatformType.COIN) continue;
            Rectangle platformRect = platform.getBounds();

            if (playerRect.overlaps(platformRect)) {
                if (gamePlayer.getVelocity().y <= 0 && playerOldY + playerRect.height * 0.5f >= platformRect.y + platformRect.height) {
                    gamePlayer.getPosition().y = platformRect.y + platformRect.height;
                    gamePlayer.getVelocity().y = 0;
                    gamePlayer.setGrounded(true);
                    if (gamePlayer.getCurrentState() == Player.State.JUMPING || gamePlayer.getCurrentState() == Player.State.FALLING) {
                        gamePlayer.setCurrentState(player.getVelocity().x == 0 ? Player.State.IDLE : Player.State.WALKING);
                    }
                } else if (gamePlayer.getVelocity().y > 0 && playerOldY <= platformRect.y) {
                    if (platform.getType() == Platform.PlatformType.QUESTION_BLOCK && !platform.hasBeenHit()) {
                        if (platform.hit()) {
                            Powerup.PowerupType containedPowerup = platform.getContainedPowerup();
                            if (containedPowerup != null) {
                                float powerupX = platformRect.x;
                                float powerupY = platformRect.y + platformRect.height + 5f;
                                Powerup newPowerup = new Powerup(containedPowerup, powerupX, powerupY);
                                newPowerup.getVelocity().y = 150f;
                                activePowerups.add(newPowerup);
                                soundManager.playPowerup();
                            } else {
                                gameStateManager.collectCoin();
                                gameStateManager.addScore(200);
                                soundManager.playCoinCollect();
                            }
                        }
                    }

                    gamePlayer.getPosition().y = platformRect.y - playerRect.height;
                    gamePlayer.getVelocity().y = 0;
                    playerIsHoldingJumpKey = false;
                    if(gamePlayer.getCurrentState() == Player.State.JUMPING) gamePlayer.setCurrentState(Player.State.FALLING);
                }
                gamePlayer.getBounds().setY(gamePlayer.getPosition().y);
            }
        }
    }

    private void checkPlayerPowerupCollisions() {
        Rectangle playerRect = player.getBounds();
        for (int i = activePowerups.size - 1; i >= 0; i--) {
            Powerup powerup = activePowerups.get(i);
            if (powerup.checkCollision(playerRect)) {
                handlePowerupCollection(powerup);
                powerup.collect();
                soundManager.playPowerup();
            }
        }
    }

    private void handlePowerupCollection(Powerup powerup) {
        switch (powerup.getType()) {
            case MUSHROOM:
                player.powerUp();
                gameStateManager.addScore(1000);
                break;
            case FIRE_FLOWER:
                player.powerUp();
                gameStateManager.addScore(1000);
                break;
            case STAR:
                gameStateManager.addScore(1000);
                break;
            case CHICKEN:
                gameStateManager.addScore(500);
                break;
            default:
                gameStateManager.addScore(100);
                break;
        }
    }

    private void checkShellEnemyCollisions() {
        for (int i = 0; i < activeEnemies.size; i++) {
            Enemy enemy1 = activeEnemies.get(i);

            if (enemy1 instanceof Koopa) {
                Koopa koopa1 = (Koopa) enemy1;
                if (koopa1.isMovingShell()) {
                    for (int j = activeEnemies.size - 1; j >= 0; j--) {
                        if (i == j) continue;

                        Enemy enemy2 = activeEnemies.get(j);
                        if (!enemy2.isAlive()) continue;

                        if (enemy1.getBounds().overlaps(enemy2.getBounds())) {
                            if (enemy2 instanceof Koopa) {
                                Koopa koopa2 = (Koopa) enemy2;
                                if (!koopa2.isShell()) {
                                    koopa2.onStompedBy(player);
                                } else {
                                    enemy2.onStompedBy(player);
                                    activeEnemies.removeIndex(j);
                                    if (j < i) i--;
                                }
                            } else {
                                enemy2.onStompedBy(player);
                                gameStateManager.addScore(100);
                                soundManager.playEnemyStomp();
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkPlayerEnemyCollisions() {
        if (player.getCurrentState() == Player.State.DEATH) return;

        Rectangle playerRect = player.getBounds();
        for (Enemy enemy : activeEnemies) {
            if (!enemy.isAlive() || enemy.getCurrentState() == Enemy.EnemyState.STOMPED) continue;

            if (playerRect.overlaps(enemy.getBounds())) {
                boolean isStomp = player.getVelocity().y < 0 &&
                    (player.getBounds().y > enemy.getBounds().y + enemy.getBounds().height * 0.5f);
                if (isStomp) {
                    // Stomping always works, even during invincibility
                    enemy.onStompedBy(player);
                    gameStateManager.addScore(100);
                    player.getVelocity().y = PLAYER_STOMP_BOUNCE_VELOCITY;
                    player.setGrounded(false);
                } else {
                    // UPDATED: Check invincibility before taking damage
                    if (enemy.onCollisionWith(player)) {
                        // NEW: Respect invincibility frames - Mario can't take damage while invincible
                        if (player.isInvincible()) {
                            Gdx.app.log("Player", "Mario is invincible - no damage taken from " + enemy.getClass().getSimpleName());
                            continue; // Skip damage, but continue checking other enemies
                        }

                        // Take damage based on current power state
                        if (player.getPowerState() != Player.PowerState.SMALL) {
                            Gdx.app.log("Player", "Mario hit by " + enemy.getClass().getSimpleName() + " - powering down");
                            player.powerDown(); // This now includes starting invincibility

                            // No need to check for death state here since powerDown() only calls die()
                            // when already SMALL, and we checked that the player is not SMALL above
                        } else {
                            // Small Mario dies immediately
                            Gdx.app.log("Player", "Small Mario hit by " + enemy.getClass().getSimpleName() + " - dying");
                            player.die();
                            gamePaused = true;
                        }
                        break; // Exit loop after taking damage
                    }
                }
            }
        }
    }



    private void collectCoins() {
        Rectangle playerRect = player.getBounds();
        for (int i = activePlatforms.size - 1; i >= 0; i--) {
            Platform item = activePlatforms.get(i);
            if (item.getType() == Platform.PlatformType.COIN && playerRect.overlaps(item.getBounds())) {
                activePlatforms.removeIndex(i);
                gameStateManager.collectCoin();
                soundManager.playCoinCollect();
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        if (levelEditor != null) {
            levelEditor.resize(width, height);
        }
    }

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
        if (player != null) player.dispose();
        if (playerIdleTexture != null) playerIdleTexture.dispose();
        if (background != null) background.dispose();

        Platform.disposeSharedTextures();
        if (activePlatforms != null) activePlatforms.clear();

        // Dispose enemy textures
        if (goombaWalkFrame1Tex != null) goombaWalkFrame1Tex.dispose();
        if (goombaWalkFrame2Tex != null) goombaWalkFrame2Tex.dispose();
        if (goombaSquashedTex != null && goombaSquashedTex != goombaWalkFrame1Tex) {
            goombaSquashedTex.dispose();
        }

        if (koopaWalkFrame1Tex != null) koopaWalkFrame1Tex.dispose();
        if (koopaWalkFrame2Tex != null) koopaWalkFrame2Tex.dispose();
        if (koopaShellIdleTex != null) koopaShellIdleTex.dispose();
        if (koopaShellMove1Tex != null) koopaShellMove1Tex.dispose();
        if (koopaShellMove2Tex != null) koopaShellMove2Tex.dispose();
        if (koopaShellMove3Tex != null) koopaShellMove3Tex.dispose();
        if (koopaShellMove4Tex != null) koopaShellMove4Tex.dispose();

        if (activeEnemies != null) {
            for (Enemy enemy : activeEnemies) {
                enemy.dispose();
            }
            activeEnemies.clear();
        }

        if (activePowerups != null) {
            for (Powerup powerup : activePowerups) {
                powerup.dispose();
            }
            activePowerups.clear();
        }

        if (goalPost != null) {
            goalPost.dispose();
        }
        GoalPost.disposeStaticTextures();

        if (worldMap != null) worldMap.dispose();
        if (levelEditor != null) levelEditor.dispose();
        if (soundManager != null) soundManager.dispose();
        if (gameStateManager != null) gameStateManager.dispose();
    }
}
