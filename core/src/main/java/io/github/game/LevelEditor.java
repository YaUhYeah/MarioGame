// File: core/src/main/java/io/github/game/LevelEditor.java
package io.github.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;

import java.util.ArrayDeque;

import io.github.game.editor.EditorCommand;
import io.github.game.editor.AddPlatformCommand;
import io.github.game.editor.RemovePlatformCommand;
import io.github.game.editor.ClearLevelCommand;
import io.github.game.editor.AddEnemyCommand;
import io.github.game.editor.RemoveEnemyCommand;
import io.github.game.editor.AddPowerupCommand;
import io.github.game.editor.RemovePowerupCommand;

public class LevelEditor {
    private static final float GRID_SIZE = 32f;
    private static final float UI_PANEL_WIDTH = 240f; // Increased for more powerup buttons
    private static final float DEFAULT_GROUND_WIDTH_MULTIPLIER = 100f;
    private static final float DEFAULT_GROUND_HEIGHT_MULTIPLIER = 2f;

    private Level currentLevel;

    private enum EditorTool {
        PLATFORM,
        GOOMBA,
        POWERUP
    }
    private EditorTool currentTool = EditorTool.PLATFORM;
    private Platform.PlatformType selectedPlatformType = Platform.PlatformType.GRAVEL_BLOCK;
    private Powerup.PowerupType selectedPowerupType = Powerup.PowerupType.MUSHROOM; // NEW

    private Level.PlatformData selectedPlatform;
    private Level.EnemyData selectedEnemy;
    private Level.PowerupData selectedPowerup; // NEW

    private boolean isDragging = false;
    private float dragStartX, dragStartY;

    private ShapeRenderer shapeRenderer;
    private BitmapFont font;
    private Vector3 mousePos;

    private Array<Rectangle> platformTypeButtons;
    private Array<Platform.PlatformType> buttonPlatformTypes;
    private Rectangle goombaButton;
    private Array<Rectangle> powerupTypeButtons; // NEW
    private Array<Powerup.PowerupType> buttonPowerupTypes; // NEW
    private Rectangle saveButton, loadButton, clearButton, playButton, previewButton;

    private Rectangle placementPreviewRect;
    private boolean showPlacementPreview = true;

    public enum EditorUIMode { EDITING, LEVEL_PREVIEW }
    private EditorUIMode currentUIMode = EditorUIMode.EDITING;
    private boolean hasUnappliedChanges = false;
    private BitmapFont notificationFont;

    private ArrayDeque<EditorCommand> undoStack = new ArrayDeque<>();
    private ArrayDeque<EditorCommand> redoStack = new ArrayDeque<>();
    private static final int MAX_HISTORY_SIZE = 100;

    private Array<String> availableBackgroundPaths;
    private Array<String> availableBackgroundNames;
    private Array<Rectangle> backgroundSelectorButtons;

    private Texture goombaPreviewTexture;
    private ObjectMap<Powerup.PowerupType, Texture> powerupPreviewTextures; // NEW

    public LevelEditor() {
        this.currentLevel = new Level("New Level");
        this.shapeRenderer = new ShapeRenderer();
        this.font = new BitmapFont();
        this.notificationFont = new BitmapFont();
        this.mousePos = new Vector3();
        this.placementPreviewRect = new Rectangle();

        // Load preview textures
        try {
            goombaPreviewTexture = new Texture(Gdx.files.internal("mario_sprites/enemies/goomba_walk_0.png"));
        } catch (Exception e) {
            Gdx.app.error("LevelEditor", "Failed to load goomba_walk_0.png for editor preview: " + e.getMessage(), e);
            goombaPreviewTexture = null;
        }

        // NEW: Load powerup preview textures
        powerupPreviewTextures = new ObjectMap<>();
        for (Powerup.PowerupType type : Powerup.PowerupType.values()) {
            try {
                Texture texture = new Texture(Gdx.files.internal(type.getTexturePath()));
                powerupPreviewTextures.put(type, texture);
            } catch (Exception e) {
                Gdx.app.error("LevelEditor", "Failed to load powerup preview for " + type.name() + ": " + e.getMessage());
                // Try to load coin as fallback
                try {
                    Texture fallback = new Texture(Gdx.files.internal("mario_sprites/world/coin.png"));
                    powerupPreviewTextures.put(type, fallback);
                } catch (Exception e2) {
                    Gdx.app.error("LevelEditor", "Failed to load fallback texture for " + type.name());
                }
            }
        }

        this.availableBackgroundPaths = new Array<>();
        this.availableBackgroundNames = new Array<>();
        this.backgroundSelectorButtons = new Array<>();
        this.powerupTypeButtons = new Array<>(); // NEW
        this.buttonPowerupTypes = new Array<>(); // NEW

        availableBackgroundPaths.add("mario_sprites/backgrounds/background_0.png");
        availableBackgroundNames.add("Sky (Default)");
        availableBackgroundPaths.add("mario_sprites/backgrounds/background_1.png");
        availableBackgroundNames.add("Hills");
        availableBackgroundPaths.add("mario_sprites/backgrounds/background_2.png");
        availableBackgroundNames.add("Night Sky");

        initializeUI();

        Level.PlatformData initialGround = new Level.PlatformData(
            0, 0, GRID_SIZE * DEFAULT_GROUND_WIDTH_MULTIPLIER, GRID_SIZE * DEFAULT_GROUND_HEIGHT_MULTIPLIER, Platform.PlatformType.GROUND
        );
        currentLevel.addPlatform(initialGround);
        this.hasUnappliedChanges = true;
    }

    private void initializeUI() {
        platformTypeButtons = new Array<>();
        buttonPlatformTypes = new Array<>();
        powerupTypeButtons.clear(); // NEW
        buttonPowerupTypes.clear(); // NEW
        backgroundSelectorButtons.clear();

        float viewportHeight = Gdx.graphics.getHeight();
        float currentY = viewportHeight - 20f;
        float buttonHeight = 28f;
        float buttonSpacing = 7f;
        float uiElementMargin = 10f;
        float buttonWidth = UI_PANEL_WIDTH - 2 * uiElementMargin;
        float sectionTitleHeight = 20f;
        float spaceAfterTitle = 5f;

        // --- Platforms Section ---
        currentY -= sectionTitleHeight;
        float nextButtonBottomY = currentY - spaceAfterTitle - buttonHeight;
        for (Platform.PlatformType type : Platform.PlatformType.values()) {
            Rectangle button = new Rectangle(uiElementMargin, nextButtonBottomY, buttonWidth, buttonHeight);
            platformTypeButtons.add(button);
            buttonPlatformTypes.add(type);
            nextButtonBottomY -= (buttonHeight + buttonSpacing);
        }
        currentY = nextButtonBottomY + buttonSpacing;

        // --- Enemies Section ---
        currentY -= sectionTitleHeight;
        nextButtonBottomY = currentY - spaceAfterTitle - buttonHeight;
        goombaButton = new Rectangle(uiElementMargin, nextButtonBottomY, buttonWidth, buttonHeight);
        currentY = nextButtonBottomY;

        // --- NEW: Powerups Section ---
        currentY -= sectionTitleHeight;
        nextButtonBottomY = currentY - spaceAfterTitle - buttonHeight;
        for (Powerup.PowerupType type : Powerup.PowerupType.values()) {
            Rectangle button = new Rectangle(uiElementMargin, nextButtonBottomY, buttonWidth, buttonHeight);
            powerupTypeButtons.add(button);
            buttonPowerupTypes.add(type);
            nextButtonBottomY -= (buttonHeight + buttonSpacing);
        }
        currentY = nextButtonBottomY;

        // --- Controls Section ---
        currentY -= sectionTitleHeight;
        nextButtonBottomY = currentY - spaceAfterTitle - buttonHeight;
        saveButton = new Rectangle(uiElementMargin, nextButtonBottomY, buttonWidth, buttonHeight);
        nextButtonBottomY -= (buttonHeight + buttonSpacing);
        loadButton = new Rectangle(uiElementMargin, nextButtonBottomY, buttonWidth, buttonHeight);
        nextButtonBottomY -= (buttonHeight + buttonSpacing);
        clearButton = new Rectangle(uiElementMargin, nextButtonBottomY, buttonWidth, buttonHeight);
        nextButtonBottomY -= (buttonHeight + buttonSpacing);
        previewButton = new Rectangle(uiElementMargin, nextButtonBottomY, buttonWidth, buttonHeight);
        nextButtonBottomY -= (buttonHeight + buttonSpacing);
        playButton = new Rectangle(uiElementMargin, nextButtonBottomY, buttonWidth, buttonHeight);
        currentY = nextButtonBottomY;

        // --- Backgrounds Section ---
        currentY -= sectionTitleHeight;
        nextButtonBottomY = currentY - spaceAfterTitle - buttonHeight;
        for (int i = 0; i < availableBackgroundPaths.size; i++) {
            Rectangle button = new Rectangle(uiElementMargin, nextButtonBottomY, buttonWidth, buttonHeight);
            backgroundSelectorButtons.add(button);
            nextButtonBottomY -= (buttonHeight + buttonSpacing);
        }
    }

    public void resize(int width, int height) {
        initializeUI();
    }

    private void executeCommand(EditorCommand command) {
        command.execute();
        undoStack.addLast(command);
        if (undoStack.size() > MAX_HISTORY_SIZE) undoStack.removeFirst();
        redoStack.clear();
        hasUnappliedChanges = true;
        selectedPlatform = null;
        selectedEnemy = null;
        selectedPowerup = null; // NEW
    }

    private void undo() {
        if (!undoStack.isEmpty()) {
            EditorCommand command = undoStack.removeLast();
            command.undo();
            redoStack.addLast(command);
            if (redoStack.size() > MAX_HISTORY_SIZE) redoStack.removeFirst();
            hasUnappliedChanges = true;
            selectedPlatform = null;
            selectedEnemy = null;
            selectedPowerup = null; // NEW
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            EditorCommand command = redoStack.removeLast();
            command.execute();
            undoStack.addLast(command);
            if (undoStack.size() > MAX_HISTORY_SIZE) undoStack.removeFirst();
            hasUnappliedChanges = true;
            selectedPlatform = null;
            selectedEnemy = null;
            selectedPowerup = null; // NEW
        }
    }

    public void update(OrthographicCamera camera) {
        mousePos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mousePos);

        if (currentUIMode == EditorUIMode.LEVEL_PREVIEW) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                currentUIMode = EditorUIMode.EDITING;
            }
            return;
        }

        float snappedX = Math.round(mousePos.x / GRID_SIZE) * GRID_SIZE;
        float snappedY = Math.round(mousePos.y / GRID_SIZE) * GRID_SIZE;

        if (showPlacementPreview && mousePos.x > UI_PANEL_WIDTH) {
            if (currentTool == EditorTool.PLATFORM && isDragging) {
                float width = Math.abs(snappedX - dragStartX) + GRID_SIZE;
                float height = Math.abs(snappedY - dragStartY) + GRID_SIZE;
                float x = Math.min(snappedX, dragStartX);
                float y = Math.min(snappedY, dragStartY);
                placementPreviewRect.set(x, y, width, height);
            } else {
                placementPreviewRect.set(snappedX, snappedY, GRID_SIZE, GRID_SIZE);
            }
        } else {
            placementPreviewRect.set(0,0,0,0);
        }
        handleEditorInput(snappedX, snappedY, camera);
    }

    private void handleEditorInput(float snappedX, float snappedY, OrthographicCamera camera) {
        boolean ctrlPressed = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        if (ctrlPressed && Gdx.input.isKeyJustPressed(Input.Keys.Z)) { undo(); return; }
        if (ctrlPressed && Gdx.input.isKeyJustPressed(Input.Keys.Y)) { redo(); return; }

        float camSpeed = 300 * Gdx.graphics.getDeltaTime();
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) camera.position.x -= camSpeed;
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) camera.position.x += camSpeed;
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) camera.position.y += camSpeed;
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) camera.position.y -= camSpeed;
        camera.update();

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) handleLeftClick(snappedX, snappedY);
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) handleRightClick(snappedX, snappedY);

        if (currentTool == EditorTool.PLATFORM && !Gdx.input.isButtonPressed(Input.Buttons.LEFT) && isDragging) {
            isDragging = false;
            if (placementPreviewRect.width >= GRID_SIZE && placementPreviewRect.height >= GRID_SIZE && mousePos.x > UI_PANEL_WIDTH) {
                Level.PlatformData newPlatform = new Level.PlatformData(
                    placementPreviewRect.x, placementPreviewRect.y, placementPreviewRect.width, placementPreviewRect.height, selectedPlatformType
                );
                executeCommand(new AddPlatformCommand(currentLevel, newPlatform));
            }
        }

        if (ctrlPressed && Gdx.input.isKeyJustPressed(Input.Keys.S)) saveLevel();
        if (ctrlPressed && Gdx.input.isKeyJustPressed(Input.Keys.L)) loadLevel();

        // Keyboard shortcuts
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) { currentTool = EditorTool.PLATFORM; selectedPlatformType = Platform.PlatformType.GROUND; clearSelections();}
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) { currentTool = EditorTool.PLATFORM; selectedPlatformType = Platform.PlatformType.GRAVEL_BLOCK; clearSelections();}
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) { currentTool = EditorTool.PLATFORM; selectedPlatformType = Platform.PlatformType.QUESTION_BLOCK; clearSelections();}
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4)) { currentTool = EditorTool.PLATFORM; selectedPlatformType = Platform.PlatformType.COIN; clearSelections();}
        if (Gdx.input.isKeyJustPressed(Input.Keys.G)) { currentTool = EditorTool.GOOMBA; clearSelections(); }
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) { currentTool = EditorTool.POWERUP; selectedPowerupType = Powerup.PowerupType.MUSHROOM; clearSelections(); } // NEW
        if (Gdx.input.isKeyJustPressed(Input.Keys.F)) { currentTool = EditorTool.POWERUP; selectedPowerupType = Powerup.PowerupType.FIRE_FLOWER; clearSelections(); } // NEW

        if (Gdx.input.isKeyJustPressed(Input.Keys.DEL) || Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE)) {
            if (selectedPlatform != null) {
                executeCommand(new RemovePlatformCommand(currentLevel, selectedPlatform));
                selectedPlatform = null;
            } else if (selectedEnemy != null) {
                executeCommand(new RemoveEnemyCommand(currentLevel, selectedEnemy));
                selectedEnemy = null;
            } else if (selectedPowerup != null) { // NEW
                executeCommand(new RemovePowerupCommand(currentLevel, selectedPowerup));
                selectedPowerup = null;
            }
        }
    }

    private void clearSelections() {
        selectedPlatform = null;
        selectedEnemy = null;
        selectedPowerup = null;
    }

    private void handleLeftClick(float snappedX, float snappedY) {
        Vector3 screenPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);

        // Check UI buttons first
        for (int i = 0; i < platformTypeButtons.size; i++) {
            if (isScreenButtonClicked(platformTypeButtons.get(i), screenPos)) {
                currentTool = EditorTool.PLATFORM;
                selectedPlatformType = buttonPlatformTypes.get(i);
                clearSelections();
                return;
            }
        }
        if (goombaButton != null && isScreenButtonClicked(goombaButton, screenPos)) {
            currentTool = EditorTool.GOOMBA;
            clearSelections();
            return;
        }

        // NEW: Check powerup buttons
        for (int i = 0; i < powerupTypeButtons.size; i++) {
            if (isScreenButtonClicked(powerupTypeButtons.get(i), screenPos)) {
                currentTool = EditorTool.POWERUP;
                selectedPowerupType = buttonPowerupTypes.get(i);
                clearSelections();
                return;
            }
        }

        if (saveButton != null && isScreenButtonClicked(saveButton, screenPos)) { saveLevel(); return; }
        if (loadButton != null && isScreenButtonClicked(loadButton, screenPos)) { loadLevel(); return; }
        if (clearButton != null && isScreenButtonClicked(clearButton, screenPos)) { clearLevel(); return; }
        if (previewButton != null && isScreenButtonClicked(previewButton, screenPos)) {
            currentUIMode = EditorUIMode.LEVEL_PREVIEW;
            clearSelections();
            isDragging = false;
            return;
        }
        if (playButton != null && isScreenButtonClicked(playButton, screenPos)) { return; }

        for (int i = 0; i < backgroundSelectorButtons.size; i++) {
            if (isScreenButtonClicked(backgroundSelectorButtons.get(i), screenPos)) {
                String newBgPath = availableBackgroundPaths.get(i);
                if (!newBgPath.equals(currentLevel.getBackgroundTexturePath())) {
                    currentLevel.setBackgroundTexturePath(newBgPath);
                    hasUnappliedChanges = true;
                }
                return;
            }
        }

        // If click is not on UI, it's in the game world
        if (mousePos.x > UI_PANEL_WIDTH) {
            clearSelections();
            boolean clickedExisting = false;

            // Check for clicking existing powerups first (NEW)
            for (Level.PowerupData powerup : currentLevel.getPowerupData()) {
                Rectangle rect = new Rectangle(powerup.x, powerup.y, GRID_SIZE, GRID_SIZE);
                if (rect.contains(mousePos.x, mousePos.y)) {
                    selectedPowerup = powerup;
                    clickedExisting = true;
                    currentTool = EditorTool.POWERUP;
                    try {
                        selectedPowerupType = Powerup.PowerupType.valueOf(powerup.type);
                    } catch (IllegalArgumentException e) {
                        selectedPowerupType = Powerup.PowerupType.MUSHROOM; // fallback
                    }
                    break;
                }
            }

            if (!clickedExisting) {
                // Check for clicking existing enemies
                for (Level.EnemyData enemy : currentLevel.getEnemyData()) {
                    Rectangle rect = new Rectangle(enemy.x, enemy.y, GRID_SIZE, GRID_SIZE);
                    if (rect.contains(mousePos.x, mousePos.y)) {
                        selectedEnemy = enemy;
                        clickedExisting = true;
                        currentTool = EditorTool.GOOMBA;
                        break;
                    }
                }
            }

            if (!clickedExisting) {
                // Check for clicking existing platforms
                for (Level.PlatformData platform : currentLevel.getPlatformData()) {
                    Rectangle rect = new Rectangle(platform.x, platform.y, platform.width, platform.height);
                    if (rect.contains(mousePos.x, mousePos.y)) {
                        selectedPlatform = platform;
                        clickedExisting = true;
                        currentTool = EditorTool.PLATFORM;
                        selectedPlatformType = platform.type;
                        break;
                    }
                }
            }

            // If nothing existing was clicked, try to place new element
            if (!clickedExisting) {
                if (currentTool == EditorTool.PLATFORM) {
                    boolean shiftPressed = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
                    if (selectedPlatformType == Platform.PlatformType.GROUND || shiftPressed) {
                        isDragging = true;
                        dragStartX = snappedX;
                        dragStartY = snappedY;
                        placementPreviewRect.set(snappedX, snappedY, GRID_SIZE, GRID_SIZE);
                    } else {
                        Level.PlatformData newPlatform = new Level.PlatformData(
                            snappedX, snappedY, GRID_SIZE, GRID_SIZE, selectedPlatformType
                        );
                        executeCommand(new AddPlatformCommand(currentLevel, newPlatform));
                    }
                } else if (currentTool == EditorTool.GOOMBA) {
                    Level.EnemyData newEnemy = new Level.EnemyData(snappedX, snappedY, "GOOMBA");
                    executeCommand(new AddEnemyCommand(currentLevel, newEnemy));
                } else if (currentTool == EditorTool.POWERUP) { // NEW
                    Level.PowerupData newPowerup = new Level.PowerupData(snappedX, snappedY, selectedPowerupType.name());
                    executeCommand(new AddPowerupCommand(currentLevel, newPowerup));
                }
            }
        }
    }

    private void handleRightClick(float snappedX, float snappedY) {
        if (mousePos.x <= UI_PANEL_WIDTH) return;

        // Try removing powerups first (NEW)
        for (int i = currentLevel.getPowerupData().size - 1; i >= 0; i--) {
            Level.PowerupData powerup = currentLevel.getPowerupData().get(i);
            Rectangle rect = new Rectangle(powerup.x, powerup.y, GRID_SIZE, GRID_SIZE);
            if (rect.contains(mousePos.x, mousePos.y)) {
                executeCommand(new RemovePowerupCommand(currentLevel, powerup));
                if (powerup == selectedPowerup) selectedPowerup = null;
                return;
            }
        }

        // Try removing enemies
        for (int i = currentLevel.getEnemyData().size - 1; i >= 0; i--) {
            Level.EnemyData enemy = currentLevel.getEnemyData().get(i);
            Rectangle rect = new Rectangle(enemy.x, enemy.y, GRID_SIZE, GRID_SIZE);
            if (rect.contains(mousePos.x, mousePos.y)) {
                executeCommand(new RemoveEnemyCommand(currentLevel, enemy));
                if (enemy == selectedEnemy) selectedEnemy = null;
                return;
            }
        }

        // Try removing platforms
        for (int i = currentLevel.getPlatformData().size - 1; i >= 0; i--) {
            Level.PlatformData platform = currentLevel.getPlatformData().get(i);
            Rectangle rect = new Rectangle(platform.x, platform.y, platform.width, platform.height);
            if (rect.contains(mousePos.x, mousePos.y)) {
                executeCommand(new RemovePlatformCommand(currentLevel, platform));
                if (platform == selectedPlatform) selectedPlatform = null;
                return;
            }
        }
    }

    private boolean isScreenButtonClicked(Rectangle button, Vector3 screenPos) {
        float GdxYtoUIY = Gdx.graphics.getHeight() - screenPos.y;
        return button.contains(screenPos.x, GdxYtoUIY);
    }

    public void renderEditorElements(SpriteBatch batch, OrthographicCamera camera) {
        // 1. Draw Grid
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 0.5f);
        float viewLeft = camera.position.x - camera.viewportWidth / 2f * camera.zoom;
        float viewRight = camera.position.x + camera.viewportWidth / 2f * camera.zoom;
        float viewBottom = camera.position.y - camera.viewportHeight / 2f * camera.zoom;
        float viewTop = camera.position.y + camera.viewportHeight / 2f * camera.zoom;
        for (float x = Math.round(viewLeft / GRID_SIZE) * GRID_SIZE; x < viewRight; x += GRID_SIZE) {
            shapeRenderer.line(x, viewBottom, x, viewTop);
        }
        for (float y = Math.round(viewBottom / GRID_SIZE) * GRID_SIZE; y < viewTop; y += GRID_SIZE) {
            shapeRenderer.line(viewLeft, y, viewRight, y);
        }
        shapeRenderer.end();

        // 2. Draw existing entities
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // Draw enemies (Goombas)
        if (goombaPreviewTexture != null) {
            for (Level.EnemyData enemyData : currentLevel.getEnemyData()) {
                if ("GOOMBA".equals(enemyData.type)) {
                    batch.draw(goombaPreviewTexture, enemyData.x, enemyData.y, GRID_SIZE, GRID_SIZE);
                }
            }
        }

        // NEW: Draw powerups
        for (Level.PowerupData powerupData : currentLevel.getPowerupData()) {
            try {
                Powerup.PowerupType type = Powerup.PowerupType.valueOf(powerupData.type);
                Texture texture = powerupPreviewTextures.get(type);
                if (texture != null) {
                    batch.draw(texture, powerupData.x, powerupData.y, GRID_SIZE, GRID_SIZE);
                }
            } catch (IllegalArgumentException e) {
                // Invalid powerup type, skip
            }
        }

        batch.end();

        // 3. Draw Selection Highlights
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        if (selectedPlatform != null) {
            shapeRenderer.setColor(Color.YELLOW);
            shapeRenderer.rect(selectedPlatform.x, selectedPlatform.y, selectedPlatform.width, selectedPlatform.height);
        }
        if (selectedEnemy != null) {
            shapeRenderer.setColor(Color.CYAN);
            shapeRenderer.rect(selectedEnemy.x, selectedEnemy.y, GRID_SIZE, GRID_SIZE);
        }
        if (selectedPowerup != null) { // NEW
            shapeRenderer.setColor(Color.MAGENTA);
            shapeRenderer.rect(selectedPowerup.x, selectedPowerup.y, GRID_SIZE, GRID_SIZE);
        }
        shapeRenderer.end();

        // 4. Draw Placement Preview
        if (showPlacementPreview && mousePos.x > UI_PANEL_WIDTH && placementPreviewRect.width > 0 && placementPreviewRect.height > 0) {
            if (currentTool == EditorTool.PLATFORM && isDragging ||
                currentTool != EditorTool.PLATFORM ||
                (currentTool == EditorTool.PLATFORM && !isDragging && !Gdx.input.isButtonPressed(Input.Buttons.LEFT))) {

                shapeRenderer.setProjectionMatrix(camera.combined);
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
                shapeRenderer.setColor(0.5f, 0.5f, 1f, 0.5f);
                shapeRenderer.rect(placementPreviewRect.x, placementPreviewRect.y, placementPreviewRect.width, placementPreviewRect.height);
                shapeRenderer.end();

                // Draw texture preview
                batch.setProjectionMatrix(camera.combined);
                batch.begin();
                batch.setColor(1, 1, 1, 0.5f);

                if (currentTool == EditorTool.GOOMBA && goombaPreviewTexture != null) {
                    batch.draw(goombaPreviewTexture, placementPreviewRect.x, placementPreviewRect.y, GRID_SIZE, GRID_SIZE);
                } else if (currentTool == EditorTool.POWERUP) { // NEW
                    Texture texture = powerupPreviewTextures.get(selectedPowerupType);
                    if (texture != null) {
                        batch.draw(texture, placementPreviewRect.x, placementPreviewRect.y, GRID_SIZE, GRID_SIZE);
                    }
                }

                batch.setColor(1, 1, 1, 1f);
                batch.end();
            }
        }

        // 5. Draw UI Panel
        drawUIScreenPanel(batch);
    }

    private void drawUIScreenPanel(SpriteBatch batch) {
        batch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapeRenderer.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        // 1. Draw Panel Background
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.1f, 0.1f, 0.1f, 0.9f);
        shapeRenderer.rect(0, 0, UI_PANEL_WIDTH, Gdx.graphics.getHeight());
        shapeRenderer.end();

        // 2. Draw Text
        batch.begin();
        font.setColor(Color.WHITE);
        font.getData().setScale(0.9f);

        float textX = 15f;
        float titleX = 10f;
        float currentDrawingY = Gdx.graphics.getHeight() - 10f;
        float sectionTitleVisualHeight = font.getCapHeight() * font.getData().scaleY;
        float spaceBelowTitle = 10f;
        float spaceBelowButtonBlock = 18f;
        float buttonTextPaddingY = 4f;

        // --- Platforms Section ---
        font.draw(batch, "Platforms:", titleX, currentDrawingY);
        currentDrawingY -= (sectionTitleVisualHeight + spaceBelowTitle);
        for (int i = 0; i < platformTypeButtons.size; i++) {
            Rectangle button = platformTypeButtons.get(i);
            Platform.PlatformType type = buttonPlatformTypes.get(i);
            font.draw(batch, type.toString(), textX, button.y + font.getCapHeight() + buttonTextPaddingY);
        }
        if (!platformTypeButtons.isEmpty()) {
            currentDrawingY = platformTypeButtons.peek().y - spaceBelowButtonBlock;
        } else {
            currentDrawingY -= spaceBelowButtonBlock;
        }

        // --- Enemies Section ---
        font.draw(batch, "Enemies:", titleX, currentDrawingY);
        currentDrawingY -= (sectionTitleVisualHeight + spaceBelowTitle);
        if (goombaButton != null) {
            font.draw(batch, "Goomba (G)", textX, goombaButton.y + font.getCapHeight() + buttonTextPaddingY);
            currentDrawingY = goombaButton.y - spaceBelowButtonBlock;
        } else {
            currentDrawingY -= spaceBelowButtonBlock;
        }

        // --- NEW: Powerups Section ---
        font.draw(batch, "Powerups:", titleX, currentDrawingY);
        currentDrawingY -= (sectionTitleVisualHeight + spaceBelowTitle);
        for (int i = 0; i < powerupTypeButtons.size; i++) {
            Rectangle button = powerupTypeButtons.get(i);
            Powerup.PowerupType type = buttonPowerupTypes.get(i);
            String displayName = type.getName();
            if (type == Powerup.PowerupType.MUSHROOM) displayName += " (M)";
            else if (type == Powerup.PowerupType.FIRE_FLOWER) displayName += " (F)";
            font.draw(batch, displayName, textX, button.y + font.getCapHeight() + buttonTextPaddingY);
        }
        Rectangle lastPowerupButton = null;
        if (!powerupTypeButtons.isEmpty()) {
            lastPowerupButton = powerupTypeButtons.peek();
            currentDrawingY = lastPowerupButton.y - spaceBelowButtonBlock;
        } else {
            currentDrawingY -= spaceBelowButtonBlock;
        }

        // --- Controls Section ---
        font.draw(batch, "Controls:", titleX, currentDrawingY);
        currentDrawingY -= (sectionTitleVisualHeight + spaceBelowTitle);
        Rectangle[] controlButtons = {saveButton, loadButton, clearButton, previewButton, playButton};
        String[] controlLabels = {"Save (Ctrl+S)", "Load (Ctrl+L)", "Clear Level", "Preview Level", "Play Mode (P)"};
        Rectangle lastControlButton = null;
        for (int i = 0; i < controlButtons.length; i++) {
            if (controlButtons[i] != null) {
                font.draw(batch, controlLabels[i], textX, controlButtons[i].y + font.getCapHeight() + buttonTextPaddingY);
                lastControlButton = controlButtons[i];
            }
        }
        if (lastControlButton != null) {
            currentDrawingY = lastControlButton.y - spaceBelowButtonBlock;
        } else {
            currentDrawingY -= spaceBelowButtonBlock;
        }

        // --- Backgrounds Section ---
        font.draw(batch, "Backgrounds:", titleX, currentDrawingY);
        currentDrawingY -= (sectionTitleVisualHeight + spaceBelowTitle);
        Rectangle lastBackgroundButton = null;
        for (int i = 0; i < backgroundSelectorButtons.size; i++) {
            Rectangle button = backgroundSelectorButtons.get(i);
            String bgName = availableBackgroundNames.get(i);
            font.draw(batch, bgName, textX, button.y + font.getCapHeight() + buttonTextPaddingY);
            lastBackgroundButton = button;
        }
        if (lastBackgroundButton != null) {
            currentDrawingY = lastBackgroundButton.y - spaceBelowButtonBlock;
        } else {
            currentDrawingY -= spaceBelowButtonBlock;
        }

        // --- Instructions ---
        float instructionStartY = 220f;
        if(lastBackgroundButton != null) instructionStartY = Math.min(instructionStartY, lastBackgroundButton.y - 25f);
        else if (lastControlButton != null) instructionStartY = Math.min(instructionStartY, lastControlButton.y - 25f);
        else if (lastPowerupButton != null) instructionStartY = Math.min(instructionStartY, lastPowerupButton.y - 25f);
        else if (goombaButton != null) instructionStartY = Math.min(instructionStartY, goombaButton.y - 25f);
        else if (!platformTypeButtons.isEmpty()) instructionStartY = Math.min(instructionStartY, platformTypeButtons.peek().y - 25f);

        float lineHeight = 18f;
        if (instructionStartY > lineHeight * 10) {
            String toolInfo = "Tool: " + currentTool.toString();
            if (currentTool == EditorTool.PLATFORM) {
                toolInfo += " (" + selectedPlatformType.toString() + ")";
            } else if (currentTool == EditorTool.POWERUP) { // NEW
                toolInfo += " (" + selectedPowerupType.getName() + ")";
            }
            font.draw(batch, toolInfo, titleX, instructionStartY); instructionStartY -= lineHeight;
            font.draw(batch, "Ctrl+Z: Undo, Ctrl+Y: Redo", titleX, instructionStartY); instructionStartY -= lineHeight;
            font.draw(batch, "LMB: Place/Select", titleX, instructionStartY); instructionStartY -= lineHeight;
            font.draw(batch, "Shift+Drag: Resize Ground", titleX, instructionStartY); instructionStartY -= lineHeight;
            font.draw(batch, "RMB: Remove", titleX, instructionStartY); instructionStartY -= lineHeight;
            font.draw(batch, "Arrows/WASD: Move Cam", titleX, instructionStartY); instructionStartY -= lineHeight;
            font.draw(batch, "1-4: Platforms, G: Goomba", titleX, instructionStartY); instructionStartY -= lineHeight;
            font.draw(batch, "M: Mushroom, F: Fire Flower", titleX, instructionStartY); instructionStartY -= lineHeight; // NEW
            font.draw(batch, "DEL/Bksp: Delete Selected", titleX, instructionStartY);
        }
        batch.end();

        // 3. Draw Button Borders
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        // Platforms
        for (int i = 0; i < platformTypeButtons.size; i++) {
            Rectangle button = platformTypeButtons.get(i);
            Platform.PlatformType type = buttonPlatformTypes.get(i);
            shapeRenderer.setColor((currentTool == EditorTool.PLATFORM && type == selectedPlatformType) ? Color.GREEN : Color.WHITE);
            shapeRenderer.rect(button.x, button.y, button.width, button.height);
        }
        // Enemies
        if (goombaButton != null) {
            shapeRenderer.setColor((currentTool == EditorTool.GOOMBA) ? Color.GREEN : Color.WHITE);
            shapeRenderer.rect(goombaButton.x, goombaButton.y, goombaButton.width, goombaButton.height);
        }
        // NEW: Powerups
        for (int i = 0; i < powerupTypeButtons.size; i++) {
            Rectangle button = powerupTypeButtons.get(i);
            Powerup.PowerupType type = buttonPowerupTypes.get(i);
            shapeRenderer.setColor((currentTool == EditorTool.POWERUP && type == selectedPowerupType) ? Color.GREEN : Color.WHITE);
            shapeRenderer.rect(button.x, button.y, button.width, button.height);
        }
        // Controls
        Color[] controlColors = {Color.WHITE, Color.WHITE, Color.WHITE, Color.ORANGE, Color.LIME};
        for (int i = 0; i < controlButtons.length; i++) {
            if (controlButtons[i] != null) {
                shapeRenderer.setColor(controlColors[i]);
                shapeRenderer.rect(controlButtons[i].x, controlButtons[i].y, controlButtons[i].width, controlButtons[i].height);
            }
        }
        // Backgrounds
        for (int i = 0; i < backgroundSelectorButtons.size; i++) {
            Rectangle button = backgroundSelectorButtons.get(i);
            shapeRenderer.setColor(availableBackgroundPaths.get(i).equals(currentLevel.getBackgroundTexturePath()) ? Color.CYAN : Color.LIGHT_GRAY);
            shapeRenderer.rect(button.x, button.y, button.width, button.height);
        }
        shapeRenderer.end();

        font.getData().setScale(1.0f);
    }

    public void renderPreviewNotification(SpriteBatch batch) {
        batch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.begin();
        notificationFont.setColor(Color.YELLOW);
        if (notificationFont != null) {
            notificationFont.draw(batch, "Preview Mode - Press ESC to exit", 20, Gdx.graphics.getHeight() - 20);
        }
        batch.end();
    }

    private void saveLevel() {
        Json json = new Json();
        FileHandle dir = Gdx.files.local("levels/");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        FileHandle file = Gdx.files.local("levels/" + currentLevel.getName() + ".json");
        file.writeString(json.prettyPrint(currentLevel), false);
        Gdx.app.log("LevelEditor", "Level saved: " + file.path());
        hasUnappliedChanges = false;
    }

    private void loadLevel() {
        String levelName = currentLevel.getName();
        FileHandle file = Gdx.files.local("levels/" + levelName + ".json");
        if (file.exists()) {
            Json json = new Json();
            try {
                currentLevel = json.fromJson(Level.class, file.readString());
                undoStack.clear(); redoStack.clear();
                hasUnappliedChanges = false;
                clearSelections();
                hasUnappliedChanges = true;
                Gdx.app.log("LevelEditor", "Level loaded: " + file.path());
            } catch (Exception e) {
                Gdx.app.error("LevelEditor", "Failed to load/parse level: " + file.path(), e);
            }
        } else {
            Gdx.app.log("LevelEditor", "Level file not found: " + file.path());
        }
    }

    private void clearLevel() {
        Array<Level.PlatformData> platformsToBackup = new Array<>(currentLevel.getPlatformData());
        Level.PlatformData defaultGroundInstance = new Level.PlatformData(
            0, 0, GRID_SIZE * DEFAULT_GROUND_WIDTH_MULTIPLIER, GRID_SIZE * DEFAULT_GROUND_HEIGHT_MULTIPLIER, Platform.PlatformType.GROUND
        );

        executeCommand(new ClearLevelCommand(currentLevel, platformsToBackup, defaultGroundInstance));
        // Clear enemies and powerups manually for simplicity
        currentLevel.getEnemyData().clear();
        currentLevel.getPowerupData().clear(); // NEW

        hasUnappliedChanges = true;
    }

    public Level getCurrentLevel() { return currentLevel; }
    public EditorUIMode getCurrentUIMode() { return currentUIMode; }
    public boolean hasUnappliedChanges() { return hasUnappliedChanges; }
    public void clearChangesFlag() { hasUnappliedChanges = false; }

    public boolean isPlayButtonClicked() {
        Vector3 screenPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        return playButton != null && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) &&
            isScreenButtonClicked(playButton, screenPos);
    }

    public void dispose() {
        if (shapeRenderer != null) shapeRenderer.dispose();
        if (font != null) font.dispose();
        if (notificationFont != null) notificationFont.dispose();
        if (goombaPreviewTexture != null) goombaPreviewTexture.dispose();

        // NEW: Dispose powerup preview textures
        if (powerupPreviewTextures != null) {
            for (Texture texture : powerupPreviewTextures.values()) {
                if (texture != null) texture.dispose();
            }
            powerupPreviewTextures.clear();
        }
    }
}
