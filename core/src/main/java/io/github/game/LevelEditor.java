// File: core/src/main/java/io/github/game/LevelEditor.java
package io.github.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture; // Keep for potential other previews if needed
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas; // For Goomba preview
import com.badlogic.gdx.graphics.g2d.TextureRegion; // For Goomba preview
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;

import java.util.ArrayDeque;

import io.github.game.editor.EditorCommand;
import io.github.game.editor.AddPlatformCommand;
import io.github.game.editor.RemovePlatformCommand;
import io.github.game.editor.ClearLevelCommand;
import io.github.game.editor.AddEnemyCommand; // New
import io.github.game.editor.RemoveEnemyCommand; // New

public class LevelEditor {
    private static final float GRID_SIZE = 32f;
    private static final float UI_PANEL_WIDTH = 220f; // Slightly wider for more text/buttons
    private static final float DEFAULT_GROUND_WIDTH_MULTIPLIER = 100f;
    private static final float DEFAULT_GROUND_HEIGHT_MULTIPLIER = 2f;

    private Level currentLevel;

    // Tool selection
    private enum EditorTool {
        PLATFORM,
        GOOMBA
        // Add other enemy types or placeable items here
    }
    private EditorTool currentTool = EditorTool.PLATFORM;
    private Platform.PlatformType selectedPlatformType = Platform.PlatformType.GRAVEL_BLOCK;
    // private String selectedEnemyType = "GOOMBA"; // If supporting multiple enemy types via one tool

    private Level.PlatformData selectedPlatform;
    private Level.EnemyData selectedEnemy; // For selecting placed enemies

    private boolean isDragging = false;
    private float dragStartX, dragStartY;

    private ShapeRenderer shapeRenderer;
    private BitmapFont font;
    private Vector3 mousePos;

    // UI Rectangles
    private Array<Rectangle> platformTypeButtons;
    private Array<Platform.PlatformType> buttonPlatformTypes;
    private Rectangle goombaButton; // Button for selecting Goomba tool
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

    // Texture resources for editor previews
    private TextureAtlas enemyAtlasForEditor;
    private TextureRegion goombaPreviewTexture;

    public LevelEditor() {
        this.currentLevel = new Level("New Level");
        this.shapeRenderer = new ShapeRenderer();
        this.font = new BitmapFont();
        this.notificationFont = new BitmapFont();
        this.mousePos = new Vector3();
        this.placementPreviewRect = new Rectangle();

        // Load enemy atlas for editor previews
        try {
            enemyAtlasForEditor = new TextureAtlas(Gdx.files.internal("mario_sprites/enemies/atlas/enemies.atlas"));
            // Try finding by indexed name first, common from TexturePacker
            goombaPreviewTexture = enemyAtlasForEditor.findRegion("goomba", 0);
            if (goombaPreviewTexture == null) { // Fallback to simple name if not indexed
                goombaPreviewTexture = enemyAtlasForEditor.findRegion("goomba_walk_0"); // Or just "goomba" if it's a single packed image
            }
            if (goombaPreviewTexture == null && enemyAtlasForEditor.findRegion("goomba") != null && enemyAtlasForEditor.findRegion("goomba").getRegionWidth() > 16) {
                TextureRegion goombaSheet = enemyAtlasForEditor.findRegion("goomba");
                goombaPreviewTexture = new TextureRegion(goombaSheet, 0,0,16,16); // Assuming first frame of a strip
            }

            if (goombaPreviewTexture == null && enemyAtlasForEditor.getRegions().size > 0) {
                goombaPreviewTexture = enemyAtlasForEditor.getRegions().first(); // Absolute fallback
                Gdx.app.error("LevelEditor", "Goomba preview texture specific name not found, using first available region from enemy atlas.");
            } else if (goombaPreviewTexture == null) {
                Gdx.app.error("LevelEditor", "CRITICAL: Goomba preview texture not found and enemy atlas is empty or missing suitable regions.");
                // Potentially load a default placeholder texture here if goombaPreviewTexture is still null
            }
        } catch (Exception e) {
            Gdx.app.error("LevelEditor", "Failed to load enemy atlas for editor: " + e.getMessage(), e);
            // Handle error: goombaPreviewTexture might be null. Render methods should check.
        }


        this.availableBackgroundPaths = new Array<>();
        this.availableBackgroundNames = new Array<>();
        this.backgroundSelectorButtons = new Array<>();

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
        currentLevel.addPlatform(initialGround); // Add initial ground
        // No need to executeCommand for initial setup unless you want it in undo history
        this.hasUnappliedChanges = true; // Mark initial state as changeable
    }

    private void initializeUI() {
        platformTypeButtons = new Array<>();
        buttonPlatformTypes = new Array<>();
        backgroundSelectorButtons.clear(); // Clear before re-populating in case of resize

        float viewportHeight = Gdx.graphics.getHeight();
        float currentButtonTopY = viewportHeight - 20f; // Start from top
        float buttonHeight = 28f; // Smaller buttons for more items
        float buttonSpacing = 7f;
        float uiElementTotalHeight = buttonHeight + buttonSpacing;
        float sectionTitleSpacing = 15f; // Space for section titles
        float sectionPadding = 5f; // Space after a section title before buttons

        // --- Platforms Section ---
        currentButtonTopY -= sectionTitleSpacing; // Space for "Platforms:" title
        for (Platform.PlatformType type : Platform.PlatformType.values()) {
            currentButtonTopY -= uiElementTotalHeight;
            Rectangle button = new Rectangle(10f, currentButtonTopY, UI_PANEL_WIDTH - 20f, buttonHeight);
            platformTypeButtons.add(button);
            buttonPlatformTypes.add(type);
        }

        // --- Enemies Section ---
        currentButtonTopY -= (sectionTitleSpacing + sectionPadding); // Space for "Enemies:" title
        // Goomba Button
        currentButtonTopY -= uiElementTotalHeight;
        goombaButton = new Rectangle(10f, currentButtonTopY, UI_PANEL_WIDTH - 20f, buttonHeight);

        // --- Controls Section ---
        currentButtonTopY -= (sectionTitleSpacing + sectionPadding); // Space for "Controls:" title
        currentButtonTopY -= uiElementTotalHeight;
        saveButton = new Rectangle(10f, currentButtonTopY, UI_PANEL_WIDTH - 20f, buttonHeight);
        currentButtonTopY -= uiElementTotalHeight;
        loadButton = new Rectangle(10f, currentButtonTopY, UI_PANEL_WIDTH - 20f, buttonHeight);
        currentButtonTopY -= uiElementTotalHeight;
        clearButton = new Rectangle(10f, currentButtonTopY, UI_PANEL_WIDTH - 20f, buttonHeight);
        currentButtonTopY -= uiElementTotalHeight;
        previewButton = new Rectangle(10f, currentButtonTopY, UI_PANEL_WIDTH - 20f, buttonHeight);
        currentButtonTopY -= uiElementTotalHeight;
        playButton = new Rectangle(10f, currentButtonTopY, UI_PANEL_WIDTH - 20f, buttonHeight);

        // --- Backgrounds Section ---
        currentButtonTopY -= (sectionTitleSpacing + sectionPadding); // Space for "Backgrounds:" title
        for (int i = 0; i < availableBackgroundPaths.size; i++) {
            currentButtonTopY -= uiElementTotalHeight;
            Rectangle button = new Rectangle(10f, currentButtonTopY, UI_PANEL_WIDTH - 20f, buttonHeight);
            backgroundSelectorButtons.add(button);
        }
    }

    public void resize(int width, int height) {
        // Re-initialize UI, which recalculates positions based on new height
        initializeUI();
    }

    private void executeCommand(EditorCommand command) {
        command.execute();
        undoStack.addLast(command);
        if (undoStack.size() > MAX_HISTORY_SIZE) undoStack.removeFirst();
        redoStack.clear();
        hasUnappliedChanges = true;
        selectedPlatform = null; // Deselect after any command
        selectedEnemy = null;    // Deselect after any command
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
        }
    }

    public void update(OrthographicCamera camera) {
        mousePos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mousePos); // Convert screen coordinates to world coordinates

        if (currentUIMode == EditorUIMode.LEVEL_PREVIEW) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                currentUIMode = EditorUIMode.EDITING;
            }
            return; // No editor updates in preview mode
        }

        // Snapped mouse position for grid alignment
        float snappedX = Math.round(mousePos.x / GRID_SIZE) * GRID_SIZE;
        float snappedY = Math.round(mousePos.y / GRID_SIZE) * GRID_SIZE;

        // Update placement preview rectangle
        if (showPlacementPreview && mousePos.x > UI_PANEL_WIDTH) { // Only show preview in game world area
            if (currentTool == EditorTool.PLATFORM && isDragging) {
                float width = Math.abs(snappedX - dragStartX) + GRID_SIZE;
                float height = Math.abs(snappedY - dragStartY) + GRID_SIZE;
                float x = Math.min(snappedX, dragStartX);
                float y = Math.min(snappedY, dragStartY);
                placementPreviewRect.set(x, y, width, height);
            } else { // Single block preview for non-dragging platforms or enemies
                placementPreviewRect.set(snappedX, snappedY, GRID_SIZE, GRID_SIZE);
            }
        } else {
            placementPreviewRect.set(0,0,0,0); // Hide preview if mouse is over UI or preview is off
        }
        handleEditorInput(snappedX, snappedY, camera);
    }

    private void handleEditorInput(float snappedX, float snappedY, OrthographicCamera camera) {
        boolean ctrlPressed = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        if (ctrlPressed && Gdx.input.isKeyJustPressed(Input.Keys.Z)) { undo(); return; }
        if (ctrlPressed && Gdx.input.isKeyJustPressed(Input.Keys.Y)) { redo(); return; }

        // Camera movement
        float camSpeed = 300 * Gdx.graphics.getDeltaTime();
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) camera.position.x -= camSpeed;
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) camera.position.x += camSpeed;
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) camera.position.y += camSpeed;
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) camera.position.y -= camSpeed;
        camera.update();


        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) handleLeftClick(snappedX, snappedY);
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) handleRightClick(snappedX, snappedY);

        // Handle platform dragging release
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

        // Hotkeys for tools/types (Example)
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) { currentTool = EditorTool.PLATFORM; selectedPlatformType = Platform.PlatformType.GROUND; }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) { currentTool = EditorTool.PLATFORM; selectedPlatformType = Platform.PlatformType.GRAVEL_BLOCK; }
        if (Gdx.input.isKeyJustPressed(Input.Keys.G)) { currentTool = EditorTool.GOOMBA; } // G for Goomba

        if (Gdx.input.isKeyJustPressed(Input.Keys.DEL) || Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE)) {
            if (selectedPlatform != null) {
                executeCommand(new RemovePlatformCommand(currentLevel, selectedPlatform));
                // selectedPlatform already set to null by executeCommand
            } else if (selectedEnemy != null) {
                executeCommand(new RemoveEnemyCommand(currentLevel, selectedEnemy));
                // selectedEnemy already set to null by executeCommand
            }
        }
    }

    private void handleLeftClick(float snappedX, float snappedY) {
        Vector3 screenPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0); // Screen coordinates for UI buttons

        // Check Platform Type Buttons
        for (int i = 0; i < platformTypeButtons.size; i++) {
            if (isScreenButtonClicked(platformTypeButtons.get(i), screenPos)) {
                currentTool = EditorTool.PLATFORM;
                selectedPlatformType = buttonPlatformTypes.get(i);
                return;
            }
        }
        // Check Goomba Button
        if (isScreenButtonClicked(goombaButton, screenPos)) {
            currentTool = EditorTool.GOOMBA;
            return;
        }

        // Check other UI buttons
        if (isScreenButtonClicked(saveButton, screenPos)) { saveLevel(); return; }
        if (isScreenButtonClicked(loadButton, screenPos)) { loadLevel(); return; }
        if (isScreenButtonClicked(clearButton, screenPos)) { clearLevel(); return; }
        if (isScreenButtonClicked(previewButton, screenPos)) {
            currentUIMode = EditorUIMode.LEVEL_PREVIEW;
            selectedPlatform = null; selectedEnemy = null; isDragging = false;
            return;
        }
        if (isScreenButtonClicked(playButton, screenPos)) {
            // Main.java handles the actual switch via isPlayButtonClicked()
            return;
        }

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

        // If clicked on the game world area (not UI)
        if (mousePos.x > UI_PANEL_WIDTH) {
            selectedPlatform = null; // Deselect previous
            selectedEnemy = null;   // Deselect previous
            boolean clickedExisting = false;

            // Priority 1: Try selecting an existing platform
            for (Level.PlatformData platform : currentLevel.getPlatformData()) {
                Rectangle rect = new Rectangle(platform.x, platform.y, platform.width, platform.height);
                if (rect.contains(mousePos.x, mousePos.y)) {
                    selectedPlatform = platform;
                    clickedExisting = true;
                    currentTool = EditorTool.PLATFORM; // Good idea to switch to platform tool
                    break;
                }
            }
            // Priority 2: Try selecting an existing enemy (if no platform was clicked)
            if (!clickedExisting) {
                for (Level.EnemyData enemy : currentLevel.getEnemyData()) {
                    Rectangle rect = new Rectangle(enemy.x, enemy.y, GRID_SIZE, GRID_SIZE); // Use GRID_SIZE for clickable area
                    if (rect.contains(mousePos.x, mousePos.y)) {
                        selectedEnemy = enemy;
                        clickedExisting = true;
                        if ("GOOMBA".equals(enemy.type)) currentTool = EditorTool.GOOMBA;
                        // Add logic for other enemy types if necessary
                        break;
                    }
                }
            }

            // If nothing existing was clicked, try placing new item based on current tool
            if (!clickedExisting) {
                if (currentTool == EditorTool.PLATFORM) {
                    boolean shiftPressed = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
                    if (selectedPlatformType == Platform.PlatformType.GROUND || shiftPressed) { // Drag for ground type or if shift is held
                        isDragging = true;
                        dragStartX = snappedX;
                        dragStartY = snappedY;
                        placementPreviewRect.set(snappedX, snappedY, GRID_SIZE, GRID_SIZE); // Initial preview for drag
                    } else { // Place single block for other platform types
                        Level.PlatformData newPlatform = new Level.PlatformData(
                            snappedX, snappedY, GRID_SIZE, GRID_SIZE, selectedPlatformType
                        );
                        executeCommand(new AddPlatformCommand(currentLevel, newPlatform));
                    }
                } else if (currentTool == EditorTool.GOOMBA) {
                    Level.EnemyData newEnemy = new Level.EnemyData(snappedX, snappedY, "GOOMBA");
                    executeCommand(new AddEnemyCommand(currentLevel, newEnemy));
                }
                // Add other tool placement logic here
            }
        }
    }

    private void handleRightClick(float snappedX, float snappedY) {
        if (mousePos.x <= UI_PANEL_WIDTH) return; // Ignore right clicks on UI panel

        // Try removing an enemy first (usually smaller targets)
        for (int i = currentLevel.getEnemyData().size - 1; i >= 0; i--) {
            Level.EnemyData enemy = currentLevel.getEnemyData().get(i);
            Rectangle rect = new Rectangle(enemy.x, enemy.y, GRID_SIZE, GRID_SIZE);
            if (rect.contains(mousePos.x, mousePos.y)) {
                executeCommand(new RemoveEnemyCommand(currentLevel, enemy));
                return;
            }
        }
        // Then try removing a platform
        for (int i = currentLevel.getPlatformData().size - 1; i >= 0; i--) {
            Level.PlatformData platform = currentLevel.getPlatformData().get(i);
            Rectangle rect = new Rectangle(platform.x, platform.y, platform.width, platform.height);
            if (rect.contains(mousePos.x, mousePos.y)) {
                executeCommand(new RemovePlatformCommand(currentLevel, platform));
                return;
            }
        }
    }

    private boolean isScreenButtonClicked(Rectangle button, Vector3 screenPos) {
        // Convert Gdx.input.getY() (origin top-left) to UI's ortho Y (origin bottom-left)
        float GdxYtoUIY = Gdx.graphics.getHeight() - screenPos.y;
        return button.contains(screenPos.x, GdxYtoUIY);
    }

    public void renderEditorElements(SpriteBatch batch, OrthographicCamera camera) {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 0.5f); // Grid color

        // Render Grid
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
        shapeRenderer.end(); // End grid rendering

        // Render Placed Enemies (using SpriteBatch for textures)
        if (goombaPreviewTexture != null) { // Check if texture loaded successfully
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            for (Level.EnemyData enemyData : currentLevel.getEnemyData()) {
                if ("GOOMBA".equals(enemyData.type)) {
                    batch.draw(goombaPreviewTexture, enemyData.x, enemyData.y, GRID_SIZE, GRID_SIZE);
                }
                // Add rendering for other enemy types here
            }
            batch.end();
        }

        // Highlights for selected items (after batch rendering)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        if (selectedPlatform != null) {
            shapeRenderer.setColor(Color.YELLOW);
            shapeRenderer.rect(selectedPlatform.x, selectedPlatform.y, selectedPlatform.width, selectedPlatform.height);
        }
        if (selectedEnemy != null) {
            shapeRenderer.setColor(Color.CYAN); // Different color for enemy selection
            shapeRenderer.rect(selectedEnemy.x, selectedEnemy.y, GRID_SIZE, GRID_SIZE);
        }
        shapeRenderer.end();


        // Render Placement Preview (outline and texture if applicable)
        if (showPlacementPreview && mousePos.x > UI_PANEL_WIDTH && placementPreviewRect.width > 0 && placementPreviewRect.height > 0) {
            if (isDragging || !Gdx.input.isButtonPressed(Input.Buttons.LEFT)) { // Show if dragging or if mouse button is up
                // Draw outline
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
                shapeRenderer.setColor(0.5f, 0.5f, 1f, 0.5f); // Preview outline color
                shapeRenderer.rect(placementPreviewRect.x, placementPreviewRect.y, placementPreviewRect.width, placementPreviewRect.height);
                shapeRenderer.end();

                // Draw texture preview if Goomba tool is active
                if (currentTool == EditorTool.GOOMBA && goombaPreviewTexture != null) {
                    batch.setProjectionMatrix(camera.combined);
                    batch.begin();
                    batch.setColor(1, 1, 1, 0.5f); // Semi-transparent
                    batch.draw(goombaPreviewTexture, placementPreviewRect.x, placementPreviewRect.y, GRID_SIZE, GRID_SIZE);
                    batch.setColor(1, 1, 1, 1f); // Reset color
                    batch.end();
                }
                // Add texture previews for other tools if needed
            }
        }
        drawUIScreenPanel(batch); // Draw UI panel last, on top of everything
    }

    private void drawUIScreenPanel(SpriteBatch batch) {
        batch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapeRenderer.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        // Draw UI Panel Background
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.1f, 0.1f, 0.1f, 0.9f);
        shapeRenderer.rect(0, 0, UI_PANEL_WIDTH, Gdx.graphics.getHeight());
        shapeRenderer.end();

        batch.begin(); // Begin batch for all text rendering
        font.setColor(Color.WHITE);
        font.getData().setScale(0.85f); // Slightly smaller font for UI text

        float currentTextY = Gdx.graphics.getHeight() - 15f;
        float titleSpacing = 15f;
        float buttonTextOffsetY = (platformTypeButtons.first().height - font.getCapHeight()) / 2f + 2f; // Center text in button

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line); // Begin for all button borders

        // --- Platforms Section ---
        font.draw(batch, "Platforms:", 10, currentTextY);
        currentTextY -= titleSpacing;
        for (int i = 0; i < platformTypeButtons.size; i++) {
            Rectangle button = platformTypeButtons.get(i);
            Platform.PlatformType type = buttonPlatformTypes.get(i);
            // Position text based on updated button Y from initializeUI
            font.draw(batch, type.toString(), button.x + 5, button.y + buttonTextOffsetY);
            shapeRenderer.setColor((currentTool == EditorTool.PLATFORM && type == selectedPlatformType) ? Color.GREEN : Color.WHITE);
            shapeRenderer.rect(button.x, button.y, button.width, button.height);
        }
        currentTextY = platformTypeButtons.isEmpty() ? currentTextY : platformTypeButtons.peek().y -5f;


        // --- Enemies Section ---
        font.draw(batch, "Enemies:", 10, currentTextY);
        currentTextY -= titleSpacing;
        // Goomba Button Text & Border
        font.draw(batch, "Goomba (G)", goombaButton.x + 5, goombaButton.y + buttonTextOffsetY);
        shapeRenderer.setColor((currentTool == EditorTool.GOOMBA) ? Color.GREEN : Color.WHITE);
        shapeRenderer.rect(goombaButton.x, goombaButton.y, goombaButton.width, goombaButton.height);
        currentTextY = goombaButton.y - 5f;

        // --- Controls Section ---
        font.draw(batch, "Controls:", 10, currentTextY);
        currentTextY -= titleSpacing;
        drawButtonWithText(shapeRenderer, batch, saveButton, "Save (Ctrl+S)", Color.WHITE, buttonTextOffsetY);
        drawButtonWithText(shapeRenderer, batch, loadButton, "Load (Ctrl+L)", Color.WHITE, buttonTextOffsetY);
        drawButtonWithText(shapeRenderer, batch, clearButton, "Clear Level", Color.WHITE, buttonTextOffsetY);
        drawButtonWithText(shapeRenderer, batch, previewButton, "Preview Level", Color.ORANGE, buttonTextOffsetY);
        drawButtonWithText(shapeRenderer, batch, playButton, "Play Mode (P)", Color.LIME, buttonTextOffsetY);
        currentTextY = playButton.y - 5f;

        // --- Backgrounds Section ---
        font.draw(batch, "Backgrounds:", 10, currentTextY);
        // currentTextY -= titleSpacing; // No, buttons are drawn from their own Y
        for (int i = 0; i < backgroundSelectorButtons.size; i++) {
            Rectangle button = backgroundSelectorButtons.get(i);
            String bgName = availableBackgroundNames.get(i);
            String bgPath = availableBackgroundPaths.get(i);
            Color bgColor = bgPath.equals(currentLevel.getBackgroundTexturePath()) ? Color.CYAN : Color.LIGHT_GRAY;
            drawButtonWithText(shapeRenderer, batch, button, bgName, bgColor, buttonTextOffsetY);
        }
        currentTextY = backgroundSelectorButtons.isEmpty() ? currentTextY : backgroundSelectorButtons.peek().y - 20f;


        shapeRenderer.end(); // End for all button borders

        // Instructions Text (at the bottom)
        float instructionBaseY = 150; // Start higher up and draw downwards
        if (!backgroundSelectorButtons.isEmpty()) {
            instructionBaseY = Math.min(instructionBaseY, backgroundSelectorButtons.peek().y - 25f);
        } else if (goombaButton != null){
            instructionBaseY = Math.min(instructionBaseY, goombaButton.y - 25f);
        }


        float lineHeight = 15f;
        if (instructionBaseY > lineHeight * 6) { // Ensure enough space for instructions
            font.draw(batch, "Tool: " + currentTool.toString() +
                    (currentTool == EditorTool.PLATFORM ? " (" + selectedPlatformType.toString() + ")" : ""),
                10, instructionBaseY); instructionBaseY -= lineHeight;
            font.draw(batch, "Ctrl+Z: Undo, Ctrl+Y: Redo", 10, instructionBaseY); instructionBaseY -= lineHeight;
            font.draw(batch, "Left Click: Place/Select", 10, instructionBaseY); instructionBaseY -= lineHeight;
            font.draw(batch, "Shift+Drag: Resize Ground", 10, instructionBaseY); instructionBaseY -= lineHeight;
            font.draw(batch, "Right Click: Remove", 10, instructionBaseY); instructionBaseY -= lineHeight;
            font.draw(batch, "Arrow/WASD: Move Cam", 10, instructionBaseY); instructionBaseY -= lineHeight;
            font.draw(batch, "1-2: Platform Hotkeys", 10, instructionBaseY); instructionBaseY -= lineHeight;
            font.draw(batch, "G: Goomba Hotkey", 10, instructionBaseY); instructionBaseY -= lineHeight;
            font.draw(batch, "DEL/Bksp: Delete Sel.", 10, instructionBaseY);
        }

        batch.end(); // End batch for all text rendering
        font.getData().setScale(1f); // Reset font scale
    }

    // Helper to draw button and its text, reducing repetition
    private void drawButtonWithText(ShapeRenderer sr, SpriteBatch batch, Rectangle button, String text, Color borderColor, float textOffsetY) {
        font.draw(batch, text, button.x + 5, button.y + textOffsetY);
        sr.setColor(borderColor);
        sr.rect(button.x, button.y, button.width, button.height);
    }
    // Overload for drawButtonUI to fit the new pattern
    private void drawButtonUI(ShapeRenderer sr, SpriteBatch batch, Rectangle button, String text) {
        float textOffsetY = (button.height - font.getCapHeight()) / 2f + 2f;
        drawButtonWithText(sr, batch, button, text, sr.getColor(), textOffsetY);
    }


    public void renderPreviewNotification(SpriteBatch batch) {
        batch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.begin();
        notificationFont.setColor(Color.YELLOW);
        notificationFont.draw(batch, "Preview Mode - Press ESC to exit", 20, Gdx.graphics.getHeight() - 20);
        batch.end();
    }

    private void saveLevel() {
        Json json = new Json();
        // Ensure "levels" directory exists
        FileHandle dir = Gdx.files.local("levels/");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        FileHandle file = Gdx.files.local("levels/" + currentLevel.getName() + ".json");
        file.writeString(json.prettyPrint(currentLevel), false);
        Gdx.app.log("LevelEditor", "Level saved: " + file.path());
        hasUnappliedChanges = false; // Changes are now saved
    }

    private void loadLevel() {
        // Simple prompt for now. For a real app, use a file chooser dialog.
        String levelName = currentLevel.getName(); // Or prompt user for a name
        FileHandle file = Gdx.files.local("levels/" + levelName + ".json");
        if (file.exists()) {
            Json json = new Json();
            try {
                currentLevel = json.fromJson(Level.class, file.readString());
                undoStack.clear();
                redoStack.clear();
                hasUnappliedChanges = false; // Freshly loaded level has no unsaved changes relative to file
                selectedPlatform = null;
                selectedEnemy = null;
                Gdx.app.log("LevelEditor", "Level loaded: " + file.path() + ". Undo/Redo history cleared.");
            } catch (Exception e) {
                Gdx.app.error("LevelEditor", "Failed to load/parse level: " + file.path(), e);
                // Optionally, revert to a new empty level or keep the current one.
            }
        } else {
            Gdx.app.log("LevelEditor", "Level file not found: " + file.path());
        }
    }

    private void clearLevel() {
        Array<Level.PlatformData> platformsToBackup = new Array<>(currentLevel.getPlatformData());
        // Also backup enemies if ClearLevelCommand should handle them.
        // For now, ClearLevelCommand only handles platforms.
        Level.PlatformData defaultGroundInstance = new Level.PlatformData(
            0, 0, GRID_SIZE * DEFAULT_GROUND_WIDTH_MULTIPLIER, GRID_SIZE * DEFAULT_GROUND_HEIGHT_MULTIPLIER, Platform.PlatformType.GROUND
        );
        // Create a new command that also clears enemies
        // For simplicity, we'll just clear directly and make a new ClearLevelAndEnemiesCommand if full undo is needed.
        executeCommand(new ClearLevelCommand(currentLevel, platformsToBackup, defaultGroundInstance)); // This only clears platforms
        currentLevel.getEnemyData().clear(); // Manually clear enemies
        hasUnappliedChanges = true; // Mark as changed after clear
    }

    public Level getCurrentLevel() { return currentLevel; }
    public EditorUIMode getCurrentUIMode() { return currentUIMode; }
    public boolean hasUnappliedChanges() { return hasUnappliedChanges; }
    public void clearChangesFlag() { hasUnappliedChanges = false; }

    public boolean isPlayButtonClicked() {
        Vector3 screenPos = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        if (playButton != null && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) &&
            isScreenButtonClicked(playButton, screenPos)) {
            // currentUIMode = EditorUIMode.EDITING; // This is handled by Main.java
            return true;
        }
        return false;
    }

    public void dispose() {
        if (shapeRenderer != null) shapeRenderer.dispose();
        if (font != null) font.dispose();
        if (notificationFont != null) notificationFont.dispose();
        if (enemyAtlasForEditor != null) enemyAtlasForEditor.dispose();
    }
}
