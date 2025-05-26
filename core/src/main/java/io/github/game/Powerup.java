package io.github.game;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

public class Powerup {
    public enum PowerupType {
        CHICKEN("Chicken Power", "mario_sprites/items/chicken.png"),
        MUSHROOM("Mushroom", "mario_sprites/items/mushroom.png"),
        FIRE_FLOWER("Fire Flower", "mario_sprites/items/fire_flower.png"),
        STAR("Star", "mario_sprites/items/star.png");

        private final String name;
        private final String texturePath;

        PowerupType(String name, String texturePath) {
            this.name = name;
            this.texturePath = texturePath;
        }

        public String getName() { return name; }
        public String getTexturePath() { return texturePath; }
    }

    private PowerupType type;
    private Vector2 position;
    private Vector2 velocity;
    private Rectangle bounds;
    private Texture texture;
    private boolean active;
    private float animationTimer;

    public static final float POWERUP_SIZE = 32f;
    private static final float SPAWN_VELOCITY = 50f;
    private static final float MOVE_SPEED = 50f;

    public Powerup(PowerupType type, float x, float y) {
        this.type = type;
        this.position = new Vector2(x, y);
        this.velocity = new Vector2(MOVE_SPEED, SPAWN_VELOCITY);
        this.bounds = new Rectangle(x, y, POWERUP_SIZE, POWERUP_SIZE);
        this.active = true;
        this.animationTimer = 0;

        // Try to load texture, use placeholder if not found
        try {
            this.texture = new Texture(type.getTexturePath());
        } catch (Exception e) {
            // Use coin texture as placeholder if powerup texture doesn't exist
            try {
                this.texture = new Texture("mario_sprites/world/coin.png");
            } catch (Exception e2) {
                System.err.println("Warning: Could not load powerup texture: " + type.getTexturePath());
            }
        }
    }

    public void update(float deltaTime, Array<Platform> platforms) {
        if (!active) return;

        animationTimer += deltaTime;

        // Apply gravity
        velocity.y -= 400f * deltaTime;

        // Update position
        position.x += velocity.x * deltaTime;
        position.y += velocity.y * deltaTime;

        // Update bounds
        bounds.setPosition(position.x, position.y);

        // Check collisions with platforms
        for (Platform platform : platforms) {
            if (platform.getType() == Platform.PlatformType.COIN) continue;

            Rectangle platformBounds = platform.getBounds();
            if (bounds.overlaps(platformBounds)) {
                // Landing on top
                if (velocity.y < 0 && position.y > platformBounds.y) {
                    position.y = platformBounds.y + platformBounds.height;
                    velocity.y = 0;
                }
                // Hitting from side
                if (velocity.x > 0 && position.x < platformBounds.x) {
                    position.x = platformBounds.x - POWERUP_SIZE;
                    velocity.x = -MOVE_SPEED;
                } else if (velocity.x < 0 && position.x > platformBounds.x) {
                    position.x = platformBounds.x + platformBounds.width;
                    velocity.x = MOVE_SPEED;
                }
            }
        }

        // Remove if fallen off screen
        if (position.y < -100) {
            active = false;
        }
    }

    public void render(SpriteBatch batch) {
        if (!active || texture == null) return;

        // Add slight bobbing animation
        float yOffset = (float)Math.sin(animationTimer * 3) * 2;

        batch.draw(texture, position.x, position.y + yOffset, POWERUP_SIZE, POWERUP_SIZE);
    }

    public boolean checkCollision(Rectangle playerBounds) {
        return active && bounds.overlaps(playerBounds);
    }

    public void collect() {
        active = false;
        System.out.println(type.getName() + " collected!");
    }

    public PowerupType getType() { return type; }
    public boolean isActive() { return active; }
    public Vector2 getPosition() { return position; }
    public Rectangle getBounds() { return bounds; }

    public void dispose() {
        if (texture != null) {
            texture.dispose();
        }
    }
}
