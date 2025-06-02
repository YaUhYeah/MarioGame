// File: core/src/main/java/io/github/game/enemies/Koopa.java
package io.github.game.enemies;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import io.github.game.Platform;
import io.github.game.Player;
import io.github.game.SoundManager;

public class Koopa extends Enemy {
    public static final float WIDTH = 32f;
    public static final float HEIGHT = 48f; // Taller than Goomba
    public static final float SHELL_HEIGHT = 24f; // Height when in shell form
    private static final float COLLISION_WIDTH = 28f;
    private static final float COLLISION_HEIGHT = 44f;
    private static final float SHELL_COLLISION_HEIGHT = 20f;
    private static final float WALK_SPEED = 30f; // Slower than Goomba
    private static final float SHELL_SPEED = 200f; // Fast shell movement
    private static final float SHELL_IDLE_DURATION = 5f; // Time before shell disappears or respawns

    public enum KoopaState {
        WALKING,
        SHELL_IDLE,
        SHELL_MOVING,
        DEAD
    }

    private Animation<TextureRegion> walkAnimation;
    private Animation<TextureRegion> shellMoveAnimation;
    private TextureRegion shellIdleRegion;

    private KoopaState koopaState;
    private float shellIdleTimer;
    private boolean shellMovingRight;

    public Koopa(Texture walkFrame1Tex, Texture walkFrame2Tex, Texture shellIdleTex,
                 Texture shellMove1Tex, Texture shellMove2Tex, Texture shellMove3Tex, Texture shellMove4Tex,
                 float x, float y) {
        super(x, y, COLLISION_WIDTH, COLLISION_HEIGHT);

        // Create walking animation
        Array<TextureRegion> walkFrames = new Array<>();
        walkFrames.add(new TextureRegion(walkFrame1Tex));
        walkFrames.add(new TextureRegion(walkFrame2Tex));
        walkAnimation = new Animation<>(0.4f, walkFrames, Animation.PlayMode.LOOP);

        // Create shell moving animation
        Array<TextureRegion> shellFrames = new Array<>();
        shellFrames.add(new TextureRegion(shellMove1Tex));
        shellFrames.add(new TextureRegion(shellMove2Tex));
        shellFrames.add(new TextureRegion(shellMove3Tex));
        shellFrames.add(new TextureRegion(shellMove4Tex));
        shellMoveAnimation = new Animation<>(0.1f, shellFrames, Animation.PlayMode.LOOP);

        this.shellIdleRegion = new TextureRegion(shellIdleTex);

        this.koopaState = KoopaState.WALKING;
        this.shellIdleTimer = 0f;
        this.shellMovingRight = false;

        // Initial velocity: start moving left
        velocity.x = facingRight ? WALK_SPEED : -WALK_SPEED;
    }

    @Override
    protected void updateLogic(float deltaTime, Array<Platform> platforms, Player player) {
        switch (koopaState) {
            case WALKING:
                updateWalkingLogic(deltaTime, platforms);
                break;
            case SHELL_IDLE:
                updateShellIdleLogic(deltaTime);
                break;
            case SHELL_MOVING:
                updateShellMovingLogic(deltaTime, platforms);
                break;
            case DEAD:
                velocity.x = 0;
                velocity.y = 0;
                break;
        }
    }

    private void updateWalkingLogic(float deltaTime, Array<Platform> platforms) {
        // Edge detection logic similar to Goomba
        float lookAheadOffset = facingRight ? bounds.width : -1;
        float lookAheadX = position.x + lookAheadOffset;
        float lookDownY = position.y - 1;

        boolean groundAhead = false;
        for (Platform p : platforms) {
            if (p.getType() == Platform.PlatformType.COIN) continue;
            Rectangle feeler = new Rectangle(lookAheadX, lookDownY, 1, 1);
            if (p.getBounds().overlaps(feeler)) {
                groundAhead = true;
                break;
            }
        }
        if (!groundAhead && velocity.y == 0) {
            facingRight = !facingRight;
            velocity.x = facingRight ? WALK_SPEED : -WALK_SPEED;
        }
    }

    private void updateShellIdleLogic(float deltaTime) {
        velocity.x = 0;
        shellIdleTimer += deltaTime;

        // After idle duration, respawn as walking Koopa or disappear
        if (shellIdleTimer > SHELL_IDLE_DURATION) {
            // For now, just mark as dead. In a full game, you might respawn the Koopa
            koopaState = KoopaState.DEAD;
            isAlive = false;
        }
    }

    private void updateShellMovingLogic(float deltaTime, Array<Platform> platforms) {
        // Shell moves fast in one direction until it hits something
        velocity.x = shellMovingRight ? SHELL_SPEED : -SHELL_SPEED;

        // Check for walls to bounce off
        // This is handled by the main collision detection in Enemy.update()
    }

    @Override
    public void render(SpriteBatch batch) {
        if (!isAlive && koopaState != KoopaState.SHELL_IDLE && koopaState != KoopaState.SHELL_MOVING) return;

        TextureRegion currentFrame = null;
        float drawWidth = WIDTH;
        float drawHeight = HEIGHT;

        switch (koopaState) {
            case WALKING:
                currentFrame = walkAnimation.getKeyFrame(stateTimer, true);
                break;
            case SHELL_IDLE:
                currentFrame = shellIdleRegion;
                drawHeight = SHELL_HEIGHT;
                break;
            case SHELL_MOVING:
                currentFrame = shellMoveAnimation.getKeyFrame(stateTimer, true);
                drawHeight = SHELL_HEIGHT;
                break;
            default:
                return;
        }

        if (currentFrame != null) {
            float drawX = position.x - (WIDTH - COLLISION_WIDTH) / 2f;
            float drawY = position.y;

            // Flip sprite based on direction (only when walking)
            boolean flipX = false;
            if (koopaState == KoopaState.WALKING) {
                flipX = facingRight; // Flip when facing right (assuming sprites face left by default)
            } else if (koopaState == KoopaState.SHELL_MOVING) {
                // Don't flip shell sprites as they should look the same regardless of direction
                flipX = false;
            }

            batch.draw(currentFrame,
                flipX ? drawX + drawWidth : drawX,
                drawY,
                flipX ? -drawWidth : drawWidth,
                drawHeight);
        }
    }

    @Override
    public void onStompedBy(Player player) {
        if (koopaState == KoopaState.WALKING) {
            // Transform into shell
            koopaState = KoopaState.SHELL_IDLE;
            shellIdleTimer = 0f;
            velocity.set(0, 0);

            // Adjust bounds for shell form
            bounds.height = SHELL_COLLISION_HEIGHT;

            // Play sound
            SoundManager.getInstance().playEnemyStomp();

            stateTimer = 0f;
            Gdx.app.log("Koopa", "Koopa turned into idle shell");
        } else if (koopaState == KoopaState.SHELL_IDLE) {
            // Kick the shell
            kickShell(player);
        } else if (koopaState == KoopaState.SHELL_MOVING) {
            // Stop the shell
            koopaState = KoopaState.SHELL_IDLE;
            shellIdleTimer = 0f;
            velocity.x = 0;
            Gdx.app.log("Koopa", "Moving shell stopped");
        }
    }

    private void kickShell(Player player) {
        koopaState = KoopaState.SHELL_MOVING;
        shellIdleTimer = 0f;

        // Determine kick direction based on player position
        float playerCenterX = player.getPosition().x + player.getBounds().width / 2f;
        float koopaCenterX = position.x + bounds.width / 2f;

        shellMovingRight = playerCenterX < koopaCenterX;
        velocity.x = shellMovingRight ? SHELL_SPEED : -SHELL_SPEED;

        stateTimer = 0f;

        // Play kick sound
        SoundManager.getInstance().playEnemyStomp();
        Gdx.app.log("Koopa", "Shell kicked! Moving " + (shellMovingRight ? "right" : "left"));
    }

    @Override
    public boolean onCollisionWith(Player player) {
        switch (koopaState) {
            case WALKING:
                Gdx.app.log("Koopa", "Walking Koopa hit player - harmful");
                return true; // Harmful when walking
            case SHELL_IDLE:
                // FIXED: Player kicks idle shell, not harmful
                Gdx.app.log("Koopa", "Player touched idle shell - kicking shell");
                kickShell(player);
                return false; // Not harmful, just kicked
            case SHELL_MOVING:
                Gdx.app.log("Koopa", "Moving shell hit player - harmful");
                return true; // Harmful when shell is moving
            default:
                return false;
        }
    }

    // Override collision methods to handle shell bouncing
    @Override
    protected void checkHorizontalPlatformCollisions(Array<Platform> platforms) {
        for (Platform platform : platforms) {
            if (platform.getType() == Platform.PlatformType.COIN) continue;
            if (bounds.overlaps(platform.getBounds())) {
                if (velocity.x > 0) {
                    position.x = platform.getBounds().x - bounds.width;
                    if (koopaState == KoopaState.SHELL_MOVING) {
                        shellMovingRight = false; // Bounce left
                        velocity.x = -SHELL_SPEED;
                    } else {
                        velocity.x = -Math.abs(velocity.x);
                        facingRight = false;
                    }
                } else if (velocity.x < 0) {
                    position.x = platform.getBounds().x + platform.getBounds().width;
                    if (koopaState == KoopaState.SHELL_MOVING) {
                        shellMovingRight = true; // Bounce right
                        velocity.x = SHELL_SPEED;
                    } else {
                        velocity.x = Math.abs(velocity.x);
                        facingRight = true;
                    }
                }
                bounds.setX(position.x);
            }
        }
    }

    public KoopaState getKoopaState() {
        return koopaState;
    }

    public boolean isShell() {
        return koopaState == KoopaState.SHELL_IDLE || koopaState == KoopaState.SHELL_MOVING;
    }

    public boolean isMovingShell() {
        return koopaState == KoopaState.SHELL_MOVING;
    }
}
