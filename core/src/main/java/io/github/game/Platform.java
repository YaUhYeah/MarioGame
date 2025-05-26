// File: core/src/main/java/io/github/game/Platform.java
package io.github.game;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.Gdx;

public class Platform {
    private Rectangle bounds;
    private PlatformType type;
    private Texture singleBlockTexture;
    private Powerup.PowerupType containedPowerup; // UPDATED: Now properly typed
    private boolean hasBeenHit;
    private String uniqueId;

    // Visual size of each ground tile component
    public static final int GROUND_TILE_SIZE = 32;

    // Static textures for ground composition
    private static Texture texGrassCornerLeft;
    private static Texture texGrassMiddle;
    private static Texture texGrassCornerRight;
    private static Texture texDirtMiddle;
    private static Texture texQuestionBlockEmpty;

    // Static map for textures
    private static ObjectMap<PlatformType, Texture> otherBlockTextures;

    public enum PlatformType {
        GROUND,
        GRAVEL_BLOCK,
        QUESTION_BLOCK,
        COIN
    }

    static {
        try {
            // Load ground composition textures
            texGrassCornerLeft = new Texture("mario_sprites/world/grass_left_corner.png");
            texGrassMiddle = new Texture("mario_sprites/world/grass_middle.png");
            texGrassCornerRight = new Texture("mario_sprites/world/grass_right_corner.png");
            texDirtMiddle = new Texture("mario_sprites/world/dirt_middle.png");

            // Initialize map and load other block textures
            otherBlockTextures = new ObjectMap<>();
            otherBlockTextures.put(PlatformType.GRAVEL_BLOCK, new Texture("mario_sprites/world/gravel.png"));

            String qbPath = "mario_sprites/world/question_block.png";
            if (Gdx.files.internal(qbPath).exists()) {
                otherBlockTextures.put(PlatformType.QUESTION_BLOCK, new Texture(qbPath));
            } else {
                System.err.println("Warning: Texture not found: " + qbPath);
                if (otherBlockTextures.containsKey(PlatformType.GRAVEL_BLOCK)) {
                    otherBlockTextures.put(PlatformType.QUESTION_BLOCK, otherBlockTextures.get(PlatformType.GRAVEL_BLOCK));
                }
            }

            // Try to load empty question block texture
            String emptyQbPath = "mario_sprites/world/question_block_empty.png";
            if (Gdx.files.internal(emptyQbPath).exists()) {
                texQuestionBlockEmpty = new Texture(emptyQbPath);
            } else {
                // Use gravel as fallback for empty question block
                texQuestionBlockEmpty = otherBlockTextures.get(PlatformType.GRAVEL_BLOCK);
            }

            String coinPath = "mario_sprites/world/coin.png";
            if (Gdx.files.internal(coinPath).exists()) {
                otherBlockTextures.put(PlatformType.COIN, new Texture(coinPath));
            } else {
                System.err.println("Warning: Texture not found: " + coinPath);
            }

        } catch (Exception e) {
            System.err.println("Fatal error loading platform textures: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Texture getTextureForType(PlatformType type) {
        if (type == PlatformType.GROUND) {
            return null;
        }
        return otherBlockTextures.get(type);
    }

    public Platform(float x, float y, float width, float height, PlatformType type) {
        this.bounds = new Rectangle(x, y, width, height);
        this.type = type;
        this.hasBeenHit = false;
        this.containedPowerup = null; // UPDATED: Initialize as null
        this.uniqueId = x + "_" + y + "_" + type;

        if (type != PlatformType.GROUND) {
            this.singleBlockTexture = getTextureForType(type);

            if (this.singleBlockTexture == null) {
                System.err.println("Warning: Texture for type " + type + " is missing.");
                if (type != PlatformType.GRAVEL_BLOCK && getTextureForType(PlatformType.GRAVEL_BLOCK) != null) {
                    this.singleBlockTexture = getTextureForType(PlatformType.GRAVEL_BLOCK);
                }
            }
        }
    }

    // UPDATED: Properly typed setter and getter for contained powerup
    public void setContainedPowerup(Powerup.PowerupType powerup) {
        this.containedPowerup = powerup;
    }

    public Powerup.PowerupType getContainedPowerup() {
        return containedPowerup;
    }

    public boolean hit() {
        if (type == PlatformType.QUESTION_BLOCK && !hasBeenHit) {
            hasBeenHit = true;
            return true; // Successfully hit for the first time
        }
        return false; // Already hit or not a question block
    }

    public boolean hasBeenHit() {
        return hasBeenHit;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void render(SpriteBatch batch) {
        switch (type) {
            case GROUND:
                renderGroundPlatform(batch);
                break;
            case GRAVEL_BLOCK:
            case COIN:
                renderSingleScaledBlock(batch);
                break;
            case QUESTION_BLOCK:
                renderQuestionBlock(batch);
                break;
            default:
                if (singleBlockTexture != null) {
                    batch.draw(singleBlockTexture, bounds.x, bounds.y, bounds.width, bounds.height);
                }
                break;
        }
    }

    private void renderQuestionBlock(SpriteBatch batch) {
        Texture textureToDraw;
        if (hasBeenHit && texQuestionBlockEmpty != null) {
            textureToDraw = texQuestionBlockEmpty;
        } else if (singleBlockTexture != null) {
            textureToDraw = singleBlockTexture;
        } else {
            return;
        }
        batch.draw(textureToDraw, bounds.x, bounds.y, bounds.width, bounds.height);
    }

    private void renderGroundPlatform(SpriteBatch batch) {
        if (texGrassCornerLeft == null || texGrassMiddle == null || texGrassCornerRight == null || texDirtMiddle == null) {
            System.err.println("Cannot render GROUND: one or more essential ground textures are missing.");
            return;
        }

        int numCols = (int) (bounds.width / GROUND_TILE_SIZE);
        int numRows = (int) (bounds.height / GROUND_TILE_SIZE);

        for (int row = 0; row < numRows; row++) {
            for (int col = 0; col < numCols; col++) {
                float tileX = bounds.x + col * GROUND_TILE_SIZE;
                float tileY = bounds.y + (numRows - 1 - row) * GROUND_TILE_SIZE;

                Texture currentTileToDraw;
                if (row == 0) {
                    if (numCols == 1) {
                        currentTileToDraw = texGrassMiddle;
                    } else if (col == 0) {
                        currentTileToDraw = texGrassCornerLeft;
                    } else if (col == numCols - 1) {
                        currentTileToDraw = texGrassCornerRight;
                    } else {
                        currentTileToDraw = texGrassMiddle;
                    }
                } else {
                    currentTileToDraw = texDirtMiddle;
                }
                batch.draw(currentTileToDraw, tileX, tileY, GROUND_TILE_SIZE, GROUND_TILE_SIZE);
            }
        }
    }

    private void renderSingleScaledBlock(SpriteBatch batch) {
        if (singleBlockTexture == null) {
            return;
        }
        batch.draw(singleBlockTexture, bounds.x, bounds.y, bounds.width, bounds.height);
    }

    public Rectangle getBounds() {
        return bounds;
    }

    public static void disposeSharedTextures() {
        if (texGrassCornerLeft != null) texGrassCornerLeft.dispose();
        if (texGrassMiddle != null) texGrassMiddle.dispose();
        if (texGrassCornerRight != null) texGrassCornerRight.dispose();
        if (texDirtMiddle != null) texDirtMiddle.dispose();
        if (texQuestionBlockEmpty != null) texQuestionBlockEmpty.dispose();

        if (otherBlockTextures != null) {
            for (Texture tex : otherBlockTextures.values()) {
                if (tex != null) tex.dispose();
            }
            otherBlockTextures.clear();
        }
        System.out.println("Platform shared textures disposed.");
    }

    public void dispose() {
        // No operation needed here as textures are static and shared.
    }

    public PlatformType getType() {
        return type;
    }
}
