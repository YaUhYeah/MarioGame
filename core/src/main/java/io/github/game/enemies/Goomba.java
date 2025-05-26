// File: core/src/main/java/io/github/game/enemies/Goomba.java
package io.github.game.enemies;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import io.github.game.Platform;
import io.github.game.Player;
import io.github.game.SoundManager; // Make sure SoundManager is accessible

public class Goomba extends Enemy {
    public static final float WIDTH = 32f;
    public static final float HEIGHT = 32f;
    private static final float COLLISION_WIDTH = 28f;
    private static final float COLLISION_HEIGHT = 28f;
    private static final float SPEED = 40f; // Slightly slower for more classic feel
    private static final float STOMP_DURATION = 0.5f;

    private Animation<TextureRegion> walkAnimation;
    private TextureRegion squashedTextureRegion; // Changed from Texture to TextureRegion

    // Modified Constructor to take Textures (or TextureRegions)
    public Goomba(Texture walkFrame1Tex, Texture walkFrame2Tex, Texture squashedTex, float x, float y) {
        super(x, y, COLLISION_WIDTH, COLLISION_HEIGHT); // Enemy constructor sets facingRight = false by default

        Array<TextureRegion> frames = new Array<>();
        frames.add(new TextureRegion(walkFrame1Tex));
        frames.add(new TextureRegion(walkFrame2Tex));
        walkAnimation = new Animation<>(0.25f, frames, Animation.PlayMode.LOOP); // Slower animation

        this.squashedTextureRegion = new TextureRegion(squashedTex);
        // If squashedTex is just one of the walk frames, it's fine.
        // Example: if no specific squashed sprite, use walkFrame1Tex for squashedTextureRegion

        // Initial velocity: Enemy.facingRight is false by default, so Goomba starts moving left.
        velocity.x = facingRight ? SPEED : -SPEED;
    }

    @Override
    protected void updateLogic(float deltaTime, Array<Platform> platforms, Player player) {
        if (currentState == EnemyState.STOMPED) {
            velocity.x = 0;
            if (stateTimer > STOMP_DURATION) {
                isAlive = false;
                currentState = EnemyState.DEAD;
            }
            return;
        }

        if (currentState == EnemyState.WALKING) {
            // Edge detection logic (simple version: turn if no ground immediately ahead)
            // Check slightly in front and down, relative to movement direction
            float lookAheadOffset = facingRight ? bounds.width : -1; // Small offset in front of the bounding box
            float lookAheadX = position.x + lookAheadOffset;
            float lookDownY = position.y - 1; // Check just below the Goomba's feet

            boolean groundAhead = false;
            for (Platform p : platforms) {
                if (p.getType() == Platform.PlatformType.COIN) continue;
                // A small feeler rectangle for ground detection ahead
                Rectangle feeler = new Rectangle(lookAheadX, lookDownY, 1, 1);
                if (p.getBounds().overlaps(feeler)) {
                    groundAhead = true;
                    break;
                }
            }
            if (!groundAhead && velocity.y == 0) { // If on ground (vy=0) and no ground ahead
                facingRight = !facingRight; // Turn around
                velocity.x = facingRight ? SPEED : -SPEED; // Update velocity based on new direction
            }
        }
    }

    @Override
    public void render(SpriteBatch batch) {
        if (!isAlive && currentState != EnemyState.STOMPED) return;

        TextureRegion currentFrame = null;
        if (currentState == EnemyState.STOMPED) {
            currentFrame = squashedTextureRegion;
        } else {
            currentFrame = walkAnimation.getKeyFrame(stateTimer, true);
        }

        if (currentFrame != null) {
            float drawX = position.x - (WIDTH - COLLISION_WIDTH) / 2f;
            float drawY = position.y; // Collision bounds Y is usually bottom
            float drawWidth = WIDTH;
            float drawHeight = (currentState == EnemyState.STOMPED) ? HEIGHT / 2f : HEIGHT;

            // If sprites are drawn facing LEFT by default:
            // - To move LEFT (facingRight = false), don't flip (flipX = false).
            // - To move RIGHT (facingRight = true), flip (flipX = true).
            // This means flipX should be true when facingRight is true.
            boolean flipX = facingRight; // Corrected logic

            batch.draw(currentFrame,
                flipX ? drawX + drawWidth : drawX, // If flipping, adjust x-coordinate to keep position
                drawY,
                flipX ? -drawWidth : drawWidth,    // Negative width flips the texture region
                drawHeight);
        }
    }

    @Override
    public void onStompedBy(Player player) {
        if (currentState == EnemyState.WALKING) {
            currentState = EnemyState.STOMPED;
            velocity.set(0, 0);
            stateTimer = 0f;
            // SoundManager.getInstance().playEnemyStomp(); // Example: play stomp sound
        }
    }

    @Override
    public boolean onCollisionWith(Player player) {
        return currentState == EnemyState.WALKING; // Harmful only if walking
    }

    // No need for dispose here if Textures are managed externally (by Main.java)
}
