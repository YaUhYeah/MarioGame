// File: core/src/main/java/io/github/game/GoalPost.java
package io.github.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;

public class GoalPost implements Disposable {
    private Vector2 position;
    private Rectangle bounds;
    private Texture flagTexture;
    private Texture poleTexture;
    private boolean levelCompleted;
    private float animationTimer;

    public static final float GOAL_POST_WIDTH = 32f;
    public static final float GOAL_POST_HEIGHT = 180f; // Tall goal post
    public static final float FLAG_WIDTH = 48f;
    public static final float FLAG_HEIGHT = 32f;

    // Static textures for goal post
    private static Texture staticFlagTexture;
    private static Texture staticPoleTexture;

    static {
        try {
            // Try to load goal post textures
            if (Gdx.files.internal("mario_sprites/world/goal_post.png").exists()) {
                staticPoleTexture = new Texture("mario_sprites/world/goal_post.png");
            } else if (Gdx.files.internal("mario_sprites/world/cannon.png").exists()) {
                // Use cannon as fallback for pole
                staticPoleTexture = new Texture("mario_sprites/world/cannon.png");
            } else if (Gdx.files.internal("mario_sprites/world/brick.png").exists()) {
                // Use brick as another fallback
                staticPoleTexture = new Texture("mario_sprites/world/brick.png");
            } else {
                System.err.println("Warning: No goal post texture found");
            }

            if (Gdx.files.internal("mario_sprites/world/goal_flag.png").exists()) {
                staticFlagTexture = new Texture("mario_sprites/world/goal_flag.png");
            } else if (Gdx.files.internal("mario_sprites/world/exclamation_block.png").exists()) {
                // Use exclamation block as fallback for flag
                staticFlagTexture = new Texture("mario_sprites/world/exclamation_block.png");
            } else if (Gdx.files.internal("mario_sprites/world/coin.png").exists()) {
                // Use coin as fallback for flag
                staticFlagTexture = new Texture("mario_sprites/world/coin.png");
            } else {
                System.err.println("Warning: No goal flag texture found");
            }
        } catch (Exception e) {
            System.err.println("Error loading goal post textures: " + e.getMessage());
        }
    }

    public GoalPost(float x, float y) {
        this.position = new Vector2(x, y);
        this.bounds = new Rectangle(x, y, GOAL_POST_WIDTH, GOAL_POST_HEIGHT);
        this.levelCompleted = false;
        this.animationTimer = 0;
        this.flagTexture = staticFlagTexture;
        this.poleTexture = staticPoleTexture;
    }

    public void update(float deltaTime) {
        animationTimer += deltaTime;
    }

    public void render(SpriteBatch batch) {
        // Draw the pole
        if (poleTexture != null) {
            batch.draw(poleTexture, position.x, position.y, GOAL_POST_WIDTH, GOAL_POST_HEIGHT);
        }

        // Draw the flag with slight animation
        if (flagTexture != null) {
            float flagY = position.y + GOAL_POST_HEIGHT - FLAG_HEIGHT - 10f;
            float flagX = position.x + GOAL_POST_WIDTH;

            // Add slight waving animation
            float waveOffset = (float) Math.sin(animationTimer * 4) * 2f;

            batch.draw(flagTexture, flagX + waveOffset, flagY, FLAG_WIDTH, FLAG_HEIGHT);
        }
    }

    public boolean checkPlayerCollision(Rectangle playerBounds) {
        if (!levelCompleted && bounds.overlaps(playerBounds)) {
            levelCompleted = true;
            return true;
        }
        return false;
    }

    public boolean isLevelCompleted() {
        return levelCompleted;
    }

    public Vector2 getPosition() {
        return position;
    }

    public Rectangle getBounds() {
        return bounds;
    }

    public static void disposeStaticTextures() {
        if (staticFlagTexture != null) {
            staticFlagTexture.dispose();
            staticFlagTexture = null;
        }
        if (staticPoleTexture != null) {
            staticPoleTexture.dispose();
            staticPoleTexture = null;
        }
    }

    @Override
    public void dispose() {
        // Static textures are handled by disposeStaticTextures()
    }
}
