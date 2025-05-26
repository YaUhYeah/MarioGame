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

    // Core Game Objects
    private SpriteBatch batch;
    private OrthographicCamera camera;
    private Viewport viewport;
    private Player player;
    private Background background;
    private Array<Platform> activePlatforms;
    private Array<Enemy> activeEnemies;
    private Array<Powerup> activePowerups; // NEW: Array for active powerups

    // Individual textures for Goomba
    private Texture goombaWalkFrame1Tex;
    private Texture goombaWalkFrame2Tex;
    private Texture goombaSquashedTex;

    // Level Editor and Game State
    private LevelEditor levelEditor;
    private boolean editMode = true;
    private boolean gamePaused = false;

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

        try {
            playerIdleTexture = new Texture(Gdx.files.internal("mario_sprites/playables/mario/mario_idle.png"));
            // Load Goomba Textures individually
            goombaWalkFrame1Tex = new Texture(Gdx.files.internal("mario_sprites/enemies/goomba_walk_0.png"));
            goombaWalkFrame2Tex = new Texture(Gdx.files.internal("mario_sprites/enemies/goomba_walk_1.png"));

            // For squashed, use walk_0 as a placeholder if a specific one isn't available
            if (Gdx.files.internal("mario_sprites/enemies/goomba_squashed.png").exists()) {
                goombaSquashedTex = new Texture(Gdx.files.internal("mario_sprites/enemies/goomba_squashed.png"));
            } else {
                goombaSquashedTex = goombaWalkFrame1Tex;
                Gdx.app.log("MainCreate", "goomba_squashed.png not found, using goomba_walk_0.png as placeholder.");
            }
        } catch (Exception e) {
            Gdx.app.error("MainCreate", "Failed to load essential textures (player idle or Goomba frames)", e);
        }

        background = new Background();
        activePlatforms = new Array<>();
        activeEnemies = new Array<>();
        activePowerups = new Array<>(); // NEW: Initialize powerup array

        levelEditor = new LevelEditor();
        editMode = true;
        loadCurrentLevelData();
    }

    private void loadCurrentLevelData() {
        Level currentLoadedLevel = levelEditor.getCurrentLevel();

        activePlatforms.clear();
        activePlatforms.addAll(currentLoadedLevel.createPlatforms());

        activeEnemies.clear();
        if (goombaWalkFrame1Tex != null && goombaWalkFrame2Tex != null && goombaSquashedTex != null) {
            for (Level.EnemyData data : currentLoadedLevel.getEnemyData()) {
                if ("GOOMBA".equals(data.type)) {
                    activeEnemies.add(new Goomba(goombaWalkFrame1Tex, goombaWalkFrame2Tex, goombaSquashedTex, data.x, data.y));
                }
            }
        } else {
            Gdx.app.error("LoadLevelData", "Goomba textures not loaded. Cannot create Goomba instances.");
        }

        // NEW: Load powerups from level data
        activePowerups.clear();
        for (Level.PowerupData data : currentLoadedLevel.getPowerupData()) {
            try {
                Powerup.PowerupType type = Powerup.PowerupType.valueOf(data.type);
                activePowerups.add(new Powerup(type, data.x, data.y));
            } catch (IllegalArgumentException e) {
                Gdx.app.error("LoadLevelData", "Unknown powerup type: " + data.type);
            }
        }

        background.setTexture(currentLoadedLevel.getBackgroundTexturePath());
        player.respawn(currentLoadedLevel.getPlayerStartX(), currentLoadedLevel.getPlayerStartY());
        player.getVelocity().set(0,0);

        levelEditor.clearChangesFlag();
        gamePaused = false;
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0.4f, 0.7f, 1, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        float deltaTime = Gdx.graphics.getDeltaTime();

        if (Gdx.input.isKeyJustPressed(Input.Keys.P) || (editMode && levelEditor.isPlayButtonClicked())) {
            toggleFullEditPlayMode();
        }

        if (editMode) {
            if (levelEditor.hasUnappliedChanges()) {
                loadCurrentLevelData();
            }
            levelEditor.update(camera);

            if (levelEditor.getCurrentUIMode() == LevelEditor.EditorUIMode.EDITING) {
                camera.update();
                batch.setProjectionMatrix(camera.combined);
                batch.begin();
                background.render(batch, camera);
                for (Platform item : activePlatforms) item.render(batch);
                batch.end();
                levelEditor.renderEditorElements(batch, camera);
            } else if (levelEditor.getCurrentUIMode() == LevelEditor.EditorUIMode.LEVEL_PREVIEW) {
                Level currentLevel = levelEditor.getCurrentLevel();
                background.setTexture(currentLevel.getBackgroundTexturePath());

                float targetX = currentLevel.getPlayerStartX() + Player.PLAYER_WIDTH / 2f;
                float targetY = currentLevel.getPlayerStartY() + Player.PLAYER_HEIGHT / 2f;
                camera.position.set(targetX + 100, Math.max(WORLD_HEIGHT / 2f, targetY + 50), 0);
                camera.update();

                batch.setProjectionMatrix(camera.combined);
                batch.begin();
                background.render(batch, camera);
                for (Platform p : activePlatforms) p.render(batch);
                for (Enemy enemy : activeEnemies) enemy.render(batch);
                for (Powerup powerup : activePowerups) powerup.render(batch); // NEW: Render powerups in preview
                if (playerIdleTexture != null) {
                    batch.draw(playerIdleTexture, currentLevel.getPlayerStartX(), currentLevel.getPlayerStartY(), Player.PLAYER_WIDTH, Player.PLAYER_HEIGHT);
                }
                batch.end();
                levelEditor.renderPreviewNotification(batch);
            }
        } else { // Play Mode
            if (!gamePaused) {
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
                // NEW: Update powerups even when paused (for animations)
                for (int i = activePowerups.size - 1; i >= 0; i--) {
                    Powerup powerup = activePowerups.get(i);
                    powerup.update(deltaTime, activePlatforms);
                    if (!powerup.isActive()) {
                        powerup.dispose();
                        activePowerups.removeIndex(i);
                    }
                }
            }

            if (player.getCurrentState() == Player.State.DEATH && player.getStateTimer() > Player.DEATH_ANIMATION_DURATION) {
                respawnPlayerAndResetLevel();
            }

            camera.position.x = player.getPosition().x + Player.PLAYER_WIDTH / 2f + 100f;
            camera.position.y = Math.max(WORLD_HEIGHT / 2f, player.getPosition().y + Player.PLAYER_HEIGHT / 2f - WORLD_HEIGHT / 4f + 50f);
            camera.position.x = Math.max(camera.viewportWidth / 2f, camera.position.x);
            camera.position.y = Math.max(camera.viewportHeight / 2f, camera.position.y);
            camera.update();

            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            background.render(batch, camera);
            for (Platform item : activePlatforms) item.render(batch);
            for (Enemy enemy : activeEnemies) enemy.render(batch);
            for (Powerup powerup : activePowerups) powerup.render(batch); // NEW: Render powerups
            player.render(batch);
            batch.end();
        }
    }

    private void toggleFullEditPlayMode() {
        editMode = !editMode;
        if (editMode) {
            soundManager.stopMusic();
            gamePaused = false;
            System.out.println("Switched to EDITOR MODE");
        } else {
            levelEditor.clearChangesFlag();
            loadCurrentLevelData();

            Level currentLevel = levelEditor.getCurrentLevel();
            if (currentLevel.getBackgroundMusic() != null && !currentLevel.getBackgroundMusic().isEmpty()) {
                soundManager.playMusic(currentLevel.getBackgroundMusic(), currentLevel.getMusicVolume());
            } else {
                soundManager.stopMusic();
            }
            gamePaused = false;
            playerIsHoldingJumpKey = false;
            playerJumpHoldTimer = 0f;
            System.out.println("Switched to PLAY MODE");
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
        checkQuestionBlockHits(); // NEW: Check for question block hits

        // Update enemies
        for (int i = activeEnemies.size - 1; i >= 0; i--) {
            Enemy enemy = activeEnemies.get(i);
            enemy.update(deltaTime, activePlatforms, player);
            if (!enemy.isAlive() && enemy.getCurrentState() == Enemy.EnemyState.DEAD) {
                activeEnemies.removeIndex(i);
            }
        }
        checkPlayerEnemyCollisions();

        // NEW: Update powerups
        for (int i = activePowerups.size - 1; i >= 0; i--) {
            Powerup powerup = activePowerups.get(i);
            powerup.update(deltaTime, activePlatforms);
            if (!powerup.isActive()) {
                powerup.dispose();
                activePowerups.removeIndex(i);
            }
        }
        checkPlayerPowerupCollisions(); // NEW: Check powerup collisions

        if (player.getPosition().y < -Player.PLAYER_HEIGHT * 2 && player.getCurrentState() != Player.State.DEATH) {
            player.die();
            gamePaused = true;
        }
    }

    private void handlePlayerInput(float deltaTime) {
        if (player.getCurrentState() == Player.State.DEATH) return;

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
                    gamePlayer.getPosition().y = platformRect.y - playerRect.height;
                    gamePlayer.getVelocity().y = 0;
                    playerIsHoldingJumpKey = false;
                    if(gamePlayer.getCurrentState() == Player.State.JUMPING) gamePlayer.setCurrentState(Player.State.FALLING);
                }
                gamePlayer.getBounds().setY(gamePlayer.getPosition().y);
            }
        }
    }

    // NEW: Check for question block hits
    private void checkQuestionBlockHits() {
        Rectangle playerRect = player.getBounds();
        for (Platform platform : activePlatforms) {
            if (platform.getType() == Platform.PlatformType.QUESTION_BLOCK && !platform.hasBeenHit()) {
                Rectangle platformRect = platform.getBounds();
                // Check if player is hitting the block from below
                if (playerRect.overlaps(platformRect) &&
                    player.getVelocity().y > 0 &&
                    player.getPosition().y < platformRect.y) {

                    if (platform.hit()) {
                        // Question block was hit for the first time
                        Powerup.PowerupType containedPowerup = platform.getContainedPowerup();
                        if (containedPowerup != null) {
                            // Spawn powerup above the block
                            float powerupX = platformRect.x;
                            float powerupY = platformRect.y + platformRect.height;
                            activePowerups.add(new Powerup(containedPowerup, powerupX, powerupY));
                            soundManager.playPowerup();
                        } else {
                            // No powerup in block, just play a sound
                            soundManager.playCoinCollect();
                        }
                    }
                }
            }
        }
    }

    // NEW: Check player-powerup collisions
    private void checkPlayerPowerupCollisions() {
        Rectangle playerRect = player.getBounds();
        for (int i = activePowerups.size - 1; i >= 0; i--) {
            Powerup powerup = activePowerups.get(i);
            if (powerup.checkCollision(playerRect)) {
                // Powerup collected
                handlePowerupCollection(powerup);
                powerup.collect();
                soundManager.playPowerup();
                // Powerup will be removed in next update cycle when !isActive()
            }
        }
    }

    // NEW: Handle different powerup effects
    private void handlePowerupCollection(Powerup powerup) {
        switch (powerup.getType()) {
            case MUSHROOM:
                // For now, just log the collection
                // In a full game, this might make Mario grow
                Gdx.app.log("Game", "Mushroom collected! Player powered up!");
                break;
            case FIRE_FLOWER:
                Gdx.app.log("Game", "Fire Flower collected! Player can shoot fireballs!");
                break;
            case STAR:
                Gdx.app.log("Game", "Star collected! Player is invincible!");
                break;
            case CHICKEN:
                Gdx.app.log("Game", "Chicken collected! Extra points!");
                break;
            default:
                Gdx.app.log("Game", "Unknown powerup collected: " + powerup.getType());
                break;
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
                    enemy.onStompedBy(player);
                    player.getVelocity().y = PLAYER_STOMP_BOUNCE_VELOCITY;
                    player.setGrounded(false);
                } else {
                    if (enemy.onCollisionWith(player)) {
                        player.die();
                        gamePaused = true;
                        break;
                    }
                }
            }
        }
    }

    private void respawnPlayerAndResetLevel() {
        Gdx.app.log("Game", "Player Died. Respawning and Resetting Level.");
        gamePaused = false;
        Level currentLevel = levelEditor.getCurrentLevel();

        player.respawn(currentLevel.getPlayerStartX(), currentLevel.getPlayerStartY());

        // Reset enemies
        activeEnemies.clear();
        if (goombaWalkFrame1Tex != null && goombaWalkFrame2Tex != null && goombaSquashedTex != null) {
            for (Level.EnemyData data : currentLevel.getEnemyData()) {
                if ("GOOMBA".equals(data.type)) {
                    activeEnemies.add(new Goomba(goombaWalkFrame1Tex, goombaWalkFrame2Tex, goombaSquashedTex, data.x, data.y));
                }
            }
        }

        // NEW: Reset powerups
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
    }

    private void collectCoins() {
        Rectangle playerRect = player.getBounds();
        for (int i = activePlatforms.size - 1; i >= 0; i--) {
            Platform item = activePlatforms.get(i);
            if (item.getType() == Platform.PlatformType.COIN && playerRect.overlaps(item.getBounds())) {
                activePlatforms.removeIndex(i);
                soundManager.playCoinCollect();
                Gdx.app.log("Game", "Coin collected!");
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

        // Dispose Goomba textures
        if (goombaWalkFrame1Tex != null) goombaWalkFrame1Tex.dispose();
        if (goombaWalkFrame2Tex != null) goombaWalkFrame2Tex.dispose();
        if (goombaSquashedTex != null && goombaSquashedTex != goombaWalkFrame1Tex) {
            goombaSquashedTex.dispose();
        }

        if (activeEnemies != null) {
            for (Enemy enemy : activeEnemies) {
                enemy.dispose();
            }
            activeEnemies.clear();
        }

        // NEW: Dispose powerups
        if (activePowerups != null) {
            for (Powerup powerup : activePowerups) {
                powerup.dispose();
            }
            activePowerups.clear();
        }

        if (levelEditor != null) levelEditor.dispose();
        if (soundManager != null) soundManager.dispose();
    }
}
