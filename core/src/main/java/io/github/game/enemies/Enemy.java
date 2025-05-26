// File: core/src/main/java/io/github/game/enemies/Enemy.java
package io.github.game.enemies;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
// Remove TextureAtlas import if not used by any direct Enemy subclass that NEEDS an atlas
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import io.github.game.Platform;
import io.github.game.Player;

public abstract class Enemy implements Disposable {
    public enum EnemyState {
        WALKING,
        STOMPED,
        DEAD
    }

    protected Vector2 position;
    protected Vector2 velocity;
    protected Rectangle bounds;
    protected boolean isAlive;
    protected boolean facingRight;
    protected float stateTimer;
    protected EnemyState currentState;
    // Removed: protected TextureAtlas enemyAtlas;

    public static final float GRAVITY = -900f;

    // Modified Constructor: No longer takes TextureAtlas
    public Enemy(float x, float y, float width, float height) {
        this.position = new Vector2(x, y);
        this.velocity = new Vector2(0, 0);
        this.bounds = new Rectangle(x, y, width, height);
        this.isAlive = true;
        this.facingRight = false;
        this.stateTimer = 0f;
        this.currentState = EnemyState.WALKING;
    }

    public void update(float deltaTime, Array<Platform> platforms, Player player) {
        if (!isAlive && currentState != EnemyState.STOMPED) return;

        stateTimer += deltaTime;
        updateLogic(deltaTime, platforms, player);

        if (currentState == EnemyState.WALKING) {
            velocity.y += GRAVITY * deltaTime;
        }

        position.x += velocity.x * deltaTime;
        bounds.setX(position.x);
        checkHorizontalPlatformCollisions(platforms);

        position.y += velocity.y * deltaTime;
        bounds.setY(position.y);
        checkVerticalPlatformCollisions(platforms);

        if (position.y < -bounds.height * 2) {
            isAlive = false;
            currentState = EnemyState.DEAD;
        }
    }

    protected abstract void updateLogic(float deltaTime, Array<Platform> platforms, Player player);
    public abstract void render(SpriteBatch batch);
    public abstract void onStompedBy(Player player);
    public abstract boolean onCollisionWith(Player player);

    protected void checkHorizontalPlatformCollisions(Array<Platform> platforms) {
        for (Platform platform : platforms) {
            if (platform.getType() == Platform.PlatformType.COIN) continue;
            if (bounds.overlaps(platform.getBounds())) {
                if (velocity.x > 0) {
                    position.x = platform.getBounds().x - bounds.width;
                    velocity.x = -Math.abs(velocity.x);
                    facingRight = false;
                } else if (velocity.x < 0) {
                    position.x = platform.getBounds().x + platform.getBounds().width;
                    velocity.x = Math.abs(velocity.x);
                    facingRight = true;
                }
                bounds.setX(position.x);
            }
        }
    }

    protected void checkVerticalPlatformCollisions(Array<Platform> platforms) {
        boolean grounded = false;
        for (Platform platform : platforms) {
            if (platform.getType() == Platform.PlatformType.COIN) continue;
            if (bounds.overlaps(platform.getBounds())) {
                if (velocity.y <= 0 && position.y + bounds.height * 0.5f >= platform.getBounds().y + platform.getBounds().height) {
                    position.y = platform.getBounds().y + platform.getBounds().height;
                    velocity.y = 0;
                    grounded = true;
                } else if (velocity.y > 0 && position.y < platform.getBounds().y) {
                    position.y = platform.getBounds().y - bounds.height;
                    velocity.y = 0;
                }
                bounds.setY(position.y);
            }
        }
        if (currentState == EnemyState.WALKING && grounded) {
            // Simplified edge detection - if moving and no ground directly ahead and below, turn.
            // This requires careful implementation of "ahead and below".
            // For now, turning is primarily handled by hitting walls or more explicit edge detection in Goomba.
        }
    }

    public Rectangle getBounds() { return bounds; }
    public boolean isAlive() { return isAlive; }
    public Vector2 getPosition() { return position; }
    public Vector2 getVelocity() { return velocity; }
    public EnemyState getCurrentState() { return currentState; }

    @Override
    public void dispose() {
        // Subclasses responsible for disposing their own textures if they own them.
        // If textures are passed in and shared, Main/LevelEditor disposes them.
    }
}
