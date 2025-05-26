// File: core/src/main/java/io/github/game/Background.java
package io.github.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class Background {

    private Texture backgroundTexture;
    private String currentTexturePath;
    private float textureWidth;
    private float textureHeight;
    private float scrollSpeed = 0.5f; // Default scroll speed

    public static final String DEFAULT_BACKGROUND_PATH = "mario_sprites/backgrounds/background_0.png";

    public Background() {
        setTexture(DEFAULT_BACKGROUND_PATH);
    }

    public Background(String texturePath) {
        setTexture(texturePath);
    }

    public void setTexture(String texturePath) {
        if (texturePath == null || texturePath.isEmpty()) {
            Gdx.app.error("Background", "Texture path is null or empty. Using default: " + DEFAULT_BACKGROUND_PATH);
            texturePath = DEFAULT_BACKGROUND_PATH;
        }

        if (this.currentTexturePath != null && this.currentTexturePath.equals(texturePath) && backgroundTexture != null) {
            return; // Texture is already loaded and is the same
        }

        try {
            if (backgroundTexture != null) {
                backgroundTexture.dispose(); // Dispose old texture
            }
            Gdx.app.log("Background", "Attempting to load background: " + texturePath);
            backgroundTexture = new Texture(Gdx.files.internal(texturePath));
            this.currentTexturePath = texturePath;
            textureWidth = backgroundTexture.getWidth();
            textureHeight = backgroundTexture.getHeight();
            Gdx.app.log("Background", "Successfully loaded new background: " + texturePath + " (Width: " + textureWidth + ", Height: " + textureHeight + ")");
        } catch (Exception e) {
            Gdx.app.error("Background", "Failed to load background texture: " + texturePath, e);
            // Attempt to load a fallback if the primary one fails and it wasn't the default already
            if (!texturePath.equals(DEFAULT_BACKGROUND_PATH)) {
                Gdx.app.error("Background", "Falling back to default background due to previous error.");
                setTexture(DEFAULT_BACKGROUND_PATH); // Recursive call to load default
            } else {
                // If default also fails, then we have a bigger issue, or it's missing
                Gdx.app.error("Background", "Default background also failed to load. Background will be blank.");
                if (backgroundTexture != null) backgroundTexture.dispose();
                backgroundTexture = null; // Prevent NPEs in render
                this.currentTexturePath = null;
                textureWidth = 0; // Reset dimensions
                textureHeight = 0;
            }
        }
    }


    public void update(float deltaTime) {
        // This method can be used for future background animations or effects.
        // For parallax scrolling based on camera, all logic is in render().
    }

    public void render(SpriteBatch batch, OrthographicCamera camera) {
        if (backgroundTexture == null || textureWidth == 0) { // Don't render if texture failed to load or has no width
            return;
        }

        float cameraViewLeft = camera.position.x - camera.viewportWidth * camera.zoom / 2f;
        float cameraViewRight = camera.position.x + camera.viewportWidth * camera.zoom / 2f;

        float parallaxOffset = camera.position.x * scrollSpeed;

        // Ensure the loop condition handles cases where textureWidth might be zero to prevent infinite loops.
        // The condition textureWidth == 0 is checked at the beginning.
        for (float x = (float)Math.floor((cameraViewLeft - parallaxOffset) / textureWidth) * textureWidth + parallaxOffset; // Adjusted logic slightly
             x < cameraViewRight;
             x += textureWidth) {
            // Draw the background from y=0 up to camera.viewportHeight or level height.
            // For simplicity, drawing at y=0 with its original height.
            // If viewportHeight is desired, texture scaling or tiling vertically would be needed if textureHeight < viewportHeight.
            batch.draw(backgroundTexture, x, 0, textureWidth, textureHeight);
        }
    }

    public void dispose() {
        if (backgroundTexture != null) {
            backgroundTexture.dispose();
            backgroundTexture = null;
        }
    }
}
