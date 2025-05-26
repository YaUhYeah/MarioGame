// File: core/src/main/java/io/github/game/Main.java
package io.github.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas; // Added for enemies
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
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
    public static final float SCALED_BLOCK_SIZE = 32f; // Potentially used by other classes
    private static final float PLAYER_STOMP_BOUNCE_VELOCITY = 250f; // How high Mario bounces after stomping

    // Core Game Objects
    private SpriteBatch batch;
    private OrthographicCamera camera;
    private Viewport viewport;
    private Player player;
    private Background background;
    private Array<Platform> activePlatforms;
    private Array<Enemy> activeEnemies; // For Goombas and other enemies
    private TextureAtlas enemyAtlas;    // Atlas for enemy sprites

    // Level Editor and Game State
    private LevelEditor levelEditor;
    private boolean editMode = true;
    private boolean gamePaused = false; // Used during player death animation, etc.

    // Utility
    private SoundManager soundManager;
    private Texture playerIdleTexture; // For editor preview of player start

    // Player jump state variables
    private boolean playerIsHoldingJumpKey = false;
    private float playerJumpHoldTimer = 0f;

    @Override
    public void create() {
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0); // Center camera initially

        batch = new SpriteBatch();
        soundManager = SoundManager.getInstance();

        // Initialize Player
        player = new Player(150, Platform.GROUND_TILE_SIZE * 2); // Initial position

        // Load assets
        try {
            playerIdleTexture = new Texture("mario_sprites/playables/mario/mario_idle.png");
            enemyAtlas = new TextureAtlas(Gdx.files.internal("mario_sprites/enemies/atlas/enemies.atlas"));
        } catch (Exception e) {
            Gdx.app.error("MainCreate", "Failed to load essential textures (player idle or enemy atlas)", e);
            // Consider exiting or using placeholder assets if critical textures fail
        }

        background = new Background(); // Loads default background
        activePlatforms = new Array<>();
        activeEnemies = new Array<>();

        levelEditor = new LevelEditor();
        editMode = true; // Start in editor mode

        loadCurrentLevelData(); // Load initial level (platforms, enemies, player pos)
    }

    private void loadCurrentLevelData() {
        // Load data if editor has changes, or if it's the first load (e.g., activePlatforms is empty)
        // Forcing reload if switching to play mode ensures fresh start.
        // if (levelEditor.hasUnappliedChanges() || activePlatforms.isEmpty() || !editMode) {
        Level currentLoadedLevel = levelEditor.getCurrentLevel();

        // Create/Reset Platforms
        activePlatforms.clear();
        activePlatforms.addAll(currentLoadedLevel.createPlatforms());

        // Create/Reset Enemies
        activeEnemies.clear();
        if (enemyAtlas != null) { // Ensure atlas is loaded
            for (Level.EnemyData data : currentLoadedLevel.getEnemyData()) {
                if ("GOOMBA".equals(data.type)) {
                    activeEnemies.add(new Goomba(enemyAtlas, data.x, data.y));
                }
                // Add other enemy types here based on data.type
            }
        }

        background.setTexture(currentLoadedLevel.getBackgroundTexturePath());
        player.respawn(currentLoadedLevel.getPlayerStartX(), currentLoadedLevel.getPlayerStartY());
        player.getVelocity().set(0, 0); // Ensure velocity is zeroed on load/respawn

        levelEditor.clearChangesFlag();
        gamePaused = false; // Ensure game is not paused when loading/reloading level data
        // }
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0.4f, 0.7f, 1, 1); // Blue sky color
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        float deltaTime = Gdx.graphics.getDeltaTime();

        // Toggle between Edit and Play mode
        if (Gdx.input.isKeyJustPressed(Input.Keys.P) || (editMode && levelEditor.isPlayButtonClicked())) {
            toggleFullEditPlayMode();
        }

        if (editMode) {
            // In edit mode, ensure the view reflects the latest editor state
            if(levelEditor.hasUnappliedChanges()){ // Only reload if editor explicitly marked changes
                loadCurrentLevelData();
            }
            levelEditor.update(camera); // Update editor logic (camera movement, UI interaction)

            if (levelEditor.getCurrentUIMode() == LevelEditor.EditorUIMode.EDITING) {
                camera.update(); // Editor camera might be controlled independently
                batch.setProjectionMatrix(camera.combined);
                batch.begin();
                background.render(batch, camera);
                for (Platform item : activePlatforms) item.render(batch);
                // Enemies are rendered by levelEditor.renderEditorElements using previews
                batch.end();
                levelEditor.renderEditorElements(batch, camera); // Renders grid, UI, previews
            } else if (levelEditor.getCurrentUIMode() == LevelEditor.EditorUIMode.LEVEL_PREVIEW) {
                // Level Preview Mode (player is static, camera might focus on player start)
                Level currentLevel = levelEditor.getCurrentLevel();
                background.setTexture(currentLevel.getBackgroundTexturePath()); // Ensure preview bg is correct

                // Simplified camera for preview - centers on player start or a bit ahead
                float targetX = currentLevel.getPlayerStartX() + Player.PLAYER_WIDTH / 2f;
                float targetY = currentLevel.getPlayerStartY() + Player.PLAYER_HEIGHT / 2f;
                camera.position.set(targetX + 100, Math.max(WORLD_HEIGHT / 2f, targetY + 50), 0);
                camera.update();

                batch.setProjectionMatrix(camera.combined);
                batch.begin();
                background.render(batch, camera);
                for (Platform p : activePlatforms) p.render(batch);
                for (Enemy enemy : activeEnemies) enemy.render(batch); // Render enemies in preview
                if (playerIdleTexture != null) { // Draw static player preview
                    batch.draw(playerIdleTexture, currentLevel.getPlayerStartX(), currentLevel.getPlayerStartY(), Player.PLAYER_WIDTH, Player.PLAYER_HEIGHT);
                }
                batch.end();
                levelEditor.renderPreviewNotification(batch); // "Preview Mode - ESC to exit"
            }
        } else { // Play Mode
            if (!gamePaused) {
                updateGameLogic(deltaTime);
            } else { // Game is paused (e.g., during player death animation)
                player.update(deltaTime); // Allow player's death animation to proceed
                // Some enemies might continue minimal updates (e.g., stomp animation)
                for (int i = activeEnemies.size - 1; i >= 0; i--) {
                    Enemy enemy = activeEnemies.get(i);
                    if (enemy.getCurrentState() == Enemy.EnemyState.STOMPED) {
                        enemy.update(deltaTime, activePlatforms, player); // Minimal update for stomp
                        if (!enemy.isAlive()) {
                            activeEnemies.removeIndex(i);
                        }
                    }
                }
            }

            // Check for player death animation completion
            if (player.getCurrentState() == Player.State.DEATH && player.getStateTimer() > Player.DEATH_ANIMATION_DURATION) {
                respawnPlayerAndResetLevel(); // This will also set gamePaused = false
            }

            // Camera follows player in play mode
            // A little ahead of the player, and doesn't go below a certain Y
            camera.position.x = player.getPosition().x + Player.PLAYER_WIDTH / 2f + 100f;
            camera.position.y = Math.max(WORLD_HEIGHT / 2f, player.getPosition().y + Player.PLAYER_HEIGHT / 2f - WORLD_HEIGHT / 4f + 50f);
            // Prevent camera from going too far left/right (level bounds if they exist)
            camera.position.x = Math.max(camera.viewportWidth / 2f, camera.position.x);
            camera.position.y = Math.max(camera.viewportHeight / 2f, camera.position.y);
            camera.update();

            // Rendering game objects
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            background.render(batch, camera);
            for (Platform item : activePlatforms) item.render(batch);
            for (Enemy enemy : activeEnemies) enemy.render(batch);
            player.render(batch);
            batch.end();
        }
    }

    private void toggleFullEditPlayMode() {
        editMode = !editMode;
        if (editMode) {
            soundManager.stopMusic(); // Stop game music when returning to editor
            gamePaused = false; // Ensure game is unpaused
            System.out.println("Switched to EDITOR MODE");
            // Data is already reflective of editor; no need to load unless specific logic requires.
            // loadCurrentLevelData(); // Can be called to ensure editor is 100% synced if needed
        } else { // Switching to Play Mode
            levelEditor.clearChangesFlag(); // Mark editor changes as "applied" for this play session
            loadCurrentLevelData(); // Crucial: Load the latest level setup from editor

            Level currentLevel = levelEditor.getCurrentLevel();
            // Player state (pos, vel, etc.) is reset within loadCurrentLevelData via player.respawn()

            if (currentLevel.getBackgroundMusic() != null && !currentLevel.getBackgroundMusic().isEmpty()) {
                soundManager.playMusic(currentLevel.getBackgroundMusic(), currentLevel.getMusicVolume());
            } else {
                soundManager.stopMusic();
            }
            gamePaused = false;
            playerIsHoldingJumpKey = false; // Reset jump state
            playerJumpHoldTimer = 0f;
            System.out.println("Switched to PLAY MODE");
        }
    }

    private void updateGameLogic(float deltaTime) {
        if (player.getCurrentState() == Player.State.DEATH) {
            gamePaused = true; // Keep game logic paused
            // Player's own update method handles its death animation movement
            return;
        }

        handlePlayerInput(deltaTime);

        // Apply gravity and jump hold force to player
        float effectiveGravity = GRAVITY;
        boolean jumpKeyIsCurrentlyHeld = Gdx.input.isKeyPressed(Input.Keys.UP) ||
            Gdx.input.isKeyPressed(Input.Keys.W) ||
            Gdx.input.isKeyPressed(Input.Keys.SPACE);

        if (playerIsHoldingJumpKey && jumpKeyIsCurrentlyHeld && player.getVelocity().y > 0 && playerJumpHoldTimer < MAX_JUMP_HOLD_TIME) {
            effectiveGravity = GRAVITY * JUMP_HOLD_GRAVITY_MULTIPLIER;
            playerJumpHoldTimer += deltaTime;
        } else {
            playerIsHoldingJumpKey = false; // Stop jump hold boost if key released, timer expired, or falling
        }
        player.getVelocity().y += effectiveGravity * deltaTime;

        // Update player position and check platform collisions
        player.getPosition().x += player.getVelocity().x * deltaTime;
        player.getBounds().setX(player.getPosition().x);
        checkHorizontalCollisions(player, activePlatforms);

        player.getPosition().y += player.getVelocity().y * deltaTime;
        player.getBounds().setY(player.getPosition().y);
        checkVerticalCollisions(player, activePlatforms);

        player.update(deltaTime); // Update player's internal state (animation timers, etc.)
        collectCoins();

        // Update Enemies
        for (int i = activeEnemies.size - 1; i >= 0; i--) {
            Enemy enemy = activeEnemies.get(i);
            enemy.update(deltaTime, activePlatforms, player); // Pass platforms for enemy AI/collision
            if (!enemy.isAlive() && enemy.getCurrentState() == Enemy.EnemyState.DEAD) { // Ensure stomp anim finished
                activeEnemies.removeIndex(i);
            }
        }
        checkPlayerEnemyCollisions(); // Check interactions after player and enemies have moved

        // Player fall death (if not already dying)
        if (player.getPosition().y < -Player.PLAYER_HEIGHT * 2 && player.getCurrentState() != Player.State.DEATH) {
            player.die(); // This sets state to DEATH and plays sound
            gamePaused = true; // Pause main game logic
        }
    }

    private void handlePlayerInput(float deltaTime) {
        if (player.getCurrentState() == Player.State.DEATH) return; // No input if player is dead

        // Horizontal Movement
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D)) {
            player.getVelocity().x = MOVE_SPEED;
            player.setFacingRight(true);
            if (player.isGrounded()) player.setCurrentState(Player.State.WALKING);
        } else if (Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A)) {
            player.getVelocity().x = -MOVE_SPEED;
            player.setFacingRight(false);
            if (player.isGrounded()) player.setCurrentState(Player.State.WALKING);
        } else {
            player.getVelocity().x = 0;
            if (player.isGrounded() && player.getCurrentState() == Player.State.WALKING) {
                player.setCurrentState(Player.State.IDLE);
            }
        }

        // Jumping Input
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

        // Ducking Input (from original code)
        boolean duckKeyPressed = Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S);
        if (duckKeyPressed) {
            if (player.isGrounded() && player.getCurrentState() != Player.State.DUCKING) {
                player.setCurrentState(Player.State.DUCKING);
                player.getVelocity().x *= 0.5f; // Slow down when ducking
            }
        } else {
            if (player.getCurrentState() == Player.State.DUCKING) {
                // TODO: Add check for ceiling clearance before standing up
                player.setCurrentState(player.getVelocity().x != 0 ? Player.State.WALKING : Player.State.IDLE);
            }
        }
    }

    private void checkHorizontalCollisions(Player gamePlayer, Array<Platform> platforms) {
        Rectangle playerRect = gamePlayer.getBounds();
        for (Platform platform : platforms) {
            if (platform.getType() == Platform.PlatformType.COIN) continue; // Coins are not solid
            Rectangle platformRect = platform.getBounds();
            if (playerRect.overlaps(platformRect)) {
                if (gamePlayer.getVelocity().x > 0) { // Moving right, collided with left side of platform
                    gamePlayer.getPosition().x = platformRect.x - playerRect.width;
                } else if (gamePlayer.getVelocity().x < 0) { // Moving left, collided with right side of platform
                    gamePlayer.getPosition().x = platformRect.x + platformRect.width;
                }
                gamePlayer.getVelocity().x = 0; // Stop horizontal movement
                gamePlayer.getBounds().setX(gamePlayer.getPosition().x); // Update bounds after position correction
            }
        }
    }

    private void checkVerticalCollisions(Player gamePlayer, Array<Platform> platforms) {
        gamePlayer.setGrounded(false); // Assume not grounded until a collision proves otherwise
        Rectangle playerRect = gamePlayer.getBounds();
        float playerOldY = gamePlayer.getPosition().y - (gamePlayer.getVelocity().y * Gdx.graphics.getDeltaTime()); // Approximate Y before this frame's Y movement

        for (Platform platform : platforms) {
            if (platform.getType() == Platform.PlatformType.COIN) continue;
            Rectangle platformRect = platform.getBounds();

            if (playerRect.overlaps(platformRect)) {
                // Landing on top of a platform
                if (gamePlayer.getVelocity().y <= 0 && playerOldY + playerRect.height * 0.5f >= platformRect.y + platformRect.height) {
                    gamePlayer.getPosition().y = platformRect.y + platformRect.height;
                    gamePlayer.getVelocity().y = 0;
                    gamePlayer.setGrounded(true);
                    if (gamePlayer.getCurrentState() == Player.State.JUMPING || gamePlayer.getCurrentState() == Player.State.FALLING) {
                        gamePlayer.setCurrentState(player.getVelocity().x == 0 ? Player.State.IDLE : Player.State.WALKING);
                    }
                }
                // Hitting head on bottom of a platform
                else if (gamePlayer.getVelocity().y > 0 && playerOldY <= platformRect.y) {
                    gamePlayer.getPosition().y = platformRect.y - playerRect.height;
                    gamePlayer.getVelocity().y = 0;
                    playerIsHoldingJumpKey = false; // Cancel jump hold if head is hit
                    if(gamePlayer.getCurrentState() == Player.State.JUMPING) gamePlayer.setCurrentState(Player.State.FALLING);
                    // TODO: Handle question block hit logic (spawn powerup, change block state)
                }
                gamePlayer.getBounds().setY(gamePlayer.getPosition().y); // Update bounds after position correction
            }
        }
    }

    private void checkPlayerEnemyCollisions() {
        if (player.getCurrentState() == Player.State.DEATH) return; // No enemy collisions if already dead

        Rectangle playerRect = player.getBounds();
        for (Enemy enemy : activeEnemies) {
            if (!enemy.isAlive() || enemy.getCurrentState() == Enemy.EnemyState.STOMPED) continue; // Ignore dead or being-stomped enemies

            if (playerRect.overlaps(enemy.getBounds())) {
                // Check for stomp: Player is falling and player's bottom is roughly above enemy's top
                boolean isStomp = player.getVelocity().y < 0 &&
                    (player.getBounds().y > enemy.getBounds().y + enemy.getBounds().height * 0.5f);
                // A more precise stomp check might compare player's previous bottom Y with enemy's current top Y.

                if (isStomp) {
                    enemy.onStompedBy(player); // Enemy handles its stomp reaction (sound, state change)
                    player.getVelocity().y = PLAYER_STOMP_BOUNCE_VELOCITY; // Player bounces up
                    player.setGrounded(false); // Player is airborne after bounce
                    // Sound for stomp is usually played by enemy.onStompedBy()
                } else {
                    // Player collided with enemy (not a stomp)
                    if (enemy.onCollisionWith(player)) { // Ask enemy if this collision is harmful to player
                        player.die(); // Player dies
                        gamePaused = true; // Pause game logic for death animation
                        break; // Player is dead, no need to check other enemies for this frame
                    }
                }
            }
        }
    }

    private void respawnPlayerAndResetLevel() {
        Gdx.app.log("Game", "Player Died. Respawning and Resetting Level.");
        gamePaused = false; // Unpause the game
        Level currentLevel = levelEditor.getCurrentLevel(); // Get fresh level specification

        // Reset Player to start position and state
        player.respawn(currentLevel.getPlayerStartX(), currentLevel.getPlayerStartY());

        // Reset Enemies: Clear existing and recreate from level data
        activeEnemies.clear();
        if (enemyAtlas != null) {
            for (Level.EnemyData data : currentLevel.getEnemyData()) {
                if ("GOOMBA".equals(data.type)) {
                    activeEnemies.add(new Goomba(enemyAtlas, data.x, data.y));
                }
                // Add other enemy types
            }
        }

        // Reset platforms if they have states (e.g., hit question blocks)
        // For now, platform states are not dynamically changed in a way that needs reset here,
        // but if they were, you'd either recreate them or call a reset method on each.
        // activePlatforms.clear();
        // activePlatforms.addAll(currentLevel.createPlatforms());

        // Reset camera to player's new position smoothly or instantly
        camera.position.x = player.getPosition().x + Player.PLAYER_WIDTH / 2f + 100f;
        camera.position.y = Math.max(WORLD_HEIGHT / 2f, player.getPosition().y + Player.PLAYER_HEIGHT / 2f);
        camera.update();

        // Restart level music
        if (currentLevel.getBackgroundMusic() != null && !currentLevel.getBackgroundMusic().isEmpty()) {
            soundManager.playMusic(currentLevel.getBackgroundMusic(), currentLevel.getMusicVolume());
        } else {
            soundManager.stopMusic();
        }
        playerIsHoldingJumpKey = false; // Reset jump state
        playerJumpHoldTimer = 0f;
    }

    private void collectCoins() {
        Rectangle playerRect = player.getBounds();
        for (int i = activePlatforms.size - 1; i >= 0; i--) {
            Platform item = activePlatforms.get(i);
            if (item.getType() == Platform.PlatformType.COIN && playerRect.overlaps(item.getBounds())) {
                activePlatforms.removeIndex(i); // Remove from active game list
                // Note: This does not remove it from the LevelData in the editor.
                // If coins should be permanently collected per level save, that logic needs to be added to Level and LevelEditor.
                soundManager.playCoinCollect();
                Gdx.app.log("Game", "Coin collected!");
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true); // Update viewport, true to center camera
        if (levelEditor != null) {
            levelEditor.resize(width, height); // Notify editor of resize for UI adjustments
        }
    }

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
        if (player != null) player.dispose();
        if (playerIdleTexture != null) playerIdleTexture.dispose();
        if (background != null) background.dispose();

        Platform.disposeSharedTextures(); // Dispose static textures in Platform class
        if (activePlatforms != null) activePlatforms.clear(); // Clear list

        if (enemyAtlas != null) enemyAtlas.dispose(); // Dispose enemy texture atlas
        if (activeEnemies != null) {
            for (Enemy enemy : activeEnemies) {
                enemy.dispose(); // Enemies currently don't have unique resources beyond atlas
            }
            activeEnemies.clear();
        }

        if (levelEditor != null) levelEditor.dispose();
        if (soundManager != null) soundManager.dispose();
    }
}
