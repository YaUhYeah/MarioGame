// File: core/src/main/java/io/github/game/Player.java
package io.github.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

public class Player {

    private Texture deathTexture;
    public static final float DEATH_ANIMATION_DURATION = 1.5f;

    public enum State {
        IDLE, FALLING, WALKING, RUNNING, JUMPING, DUCKING, OPENING_DOOR, FULL_SPEED_JUMPING, DEATH, GROUND_POUNDING, GOING_DOWN_PIPE;
    }

    public enum PowerState {
        SMALL, BIG, FIRE
    }

    private Vector2 position;
    private Vector2 velocity;
    private boolean facingRight;
    private boolean grounded;
    private State currentState;
    private State previousState;
    private PowerState powerState;
    private float stateTimer;

    public static final int PLAYER_WIDTH = 32;
    public static final int PLAYER_HEIGHT = 48;

    // UPDATED: Different collision sizes for different power states
    public static final int SMALL_MARIO_WIDTH = 28;
    public static final int SMALL_MARIO_HEIGHT = 30;
    public static final int BIG_MARIO_WIDTH = 30;
    public static final int BIG_MARIO_HEIGHT = 48;

    private Rectangle bounds;

    // NEW: Separate texture sets for different power states
    // Small Mario textures
    private Texture smallIdleTexture;
    private Texture smallDuckTexture;
    private Texture smallJumpTexture;
    private Texture smallWalkTexture0;
    private Texture smallWalkTexture1;
    private Texture smallWalkTexture2;
    private Texture smallFallTexture;
    private Texture smallPipeTexture;
    private Texture smallLookUpTexture;
    private Animation<Texture> smallWalkAnimation;

    // Big Mario textures
    private Texture bigIdleTexture;
    private Texture bigDuckTexture;
    private Texture bigJumpTexture;
    private Texture bigWalkTexture0;
    private Texture bigWalkTexture1;
    private Texture bigWalkTexture2;
    private Texture bigFallTexture;
    private Texture bigPipeTexture;
    private Texture bigLookUpTexture;
    private Animation<Texture> bigWalkAnimation;

    // Shared death texture (works for both states)
    private Texture marioDeathTexture;

    // NEW: Power-up transition state
    private boolean isTransitioning = false;
    private float transitionTimer = 0f;
    private static final float TRANSITION_DURATION = 1.0f;
    private PowerState targetPowerState;

    // NEW: Stop ducking
    public void stopDucking() {
        if (isDucking) {
            // Check if there's enough space above to stand up
            if (canStandUp()) {
                isDucking = false;
                updateBounds();

                // Return to appropriate state based on movement
                if (Math.abs(velocity.x) > 0) {
                    setCurrentState(State.WALKING);
                } else {
                    setCurrentState(State.IDLE);
                }

                Gdx.app.log("Player", "Mario stopped ducking. New height: " + bounds.height);
            } else {
                // Can't stand up yet, stay ducking
                Gdx.app.log("Player", "Can't stand up - not enough space above");
            }
        }
    }

    // NEW: Check if Mario can stand up (no obstacles above)
    private boolean canStandUp() {
        if (!isDucking) return true;

        // Calculate what the bounds would be if standing
        float standingHeight = (powerState == PowerState.SMALL) ? SMALL_MARIO_HEIGHT : BIG_MARIO_HEIGHT;
        float heightDifference = standingHeight - bounds.height;

        // Create a test rectangle for the space above Mario
        Rectangle testBounds = new Rectangle(
            bounds.x,
            bounds.y + bounds.height,
            bounds.width,
            heightDifference
        );

        // This would need to check against platforms - for now return true
        // In a full implementation, you'd check testBounds against all solid platforms
        return true;
    }

    // UPDATED: Enhanced update method to handle invincibility timer
    public void update(float deltaTime){
        stateTimer = currentState == previousState ? stateTimer + deltaTime : 0;
        previousState = currentState;

        // NEW: Handle invincibility timer
        if (isInvincible) {
            invincibilityTimer -= deltaTime;
            if (invincibilityTimer <= 0f) {
                isInvincible = false;
                invincibilityTimer = 0f;
                Gdx.app.log("Player", "Invincibility ended");
            }
        }

        // Handle power-up transition
        if (isTransitioning) {
            transitionTimer += deltaTime;
            if (transitionTimer >= TRANSITION_DURATION) {
                isTransitioning = false;
                transitionTimer = 0f;
            }
        }

        if (currentState == State.DEATH) {
            // Apply death physics (vertical jump and fall)
            velocity.y += DEATH_GRAVITY * deltaTime;
            position.y += velocity.y * deltaTime;
            bounds.setPosition(position.x, position.y);
        } else {
            // Existing update logic for non-death states
            bounds.setPosition(position.x, position.y);

            // Handle ducking state transitions
            if (currentState == State.DUCKING && !isDucking) {
                // Was ducking but no longer in ducking state
                startDucking();
            } else if (currentState != State.DUCKING && isDucking && grounded) {
                // No longer in ducking state but still flagged as ducking
                stopDucking();
            }

            // Condition for falling state based on vertical velocity and not being grounded
            if (velocity.y < 0 && !grounded && currentState != State.FALLING &&
                currentState != State.DUCKING && currentState != State.GOING_DOWN_PIPE) {
                setCurrentState(State.FALLING);
            }
        }
    }

    // UPDATED: Enhanced render method with invincibility flashing effect
    public void render(SpriteBatch batch){
        Texture currentFrame = getFrame();

        boolean flipX = !facingRight; // Player faces right by default, flip if not facingRight

        // UPDATED: Render with appropriate size based on power state AND ducking
        float renderWidth = PLAYER_WIDTH;
        float renderHeight = PLAYER_HEIGHT;

        // Adjust render size for ducking
        if (isDucking) {
            renderHeight = renderHeight * 0.5f; // Half height when ducking
        }

        // Adjust render position to center the sprite on the collision box
        float offsetX = (renderWidth - bounds.width) / 2f;
        float offsetY = 0; // Keep aligned to bottom for ground contact

        // NEW: Handle invincibility flashing effect
        boolean shouldRender = true;
        if (isInvincible) {
            // Flash effect during invincibility
            float flashCycle = invincibilityTimer * INVINCIBILITY_FLASH_RATE;
            shouldRender = ((int)flashCycle % 2) == 0;
        }

        // Handle transition effect (flashing between states)
        if (isTransitioning) {
            // Flash effect during transition
            float flashSpeed = 10f;
            boolean showAlternate = ((int)(transitionTimer * flashSpeed) % 2) == 0;

            if (showAlternate && targetPowerState != PowerState.SMALL) {
                // Show transition state briefly
                batch.setColor(1f, 1f, 1f, 0.7f);
            }
        }

        // Only render if not in flashing-off state during invincibility
        if (shouldRender) {
            // Standard drawing logic
            batch.draw(
                currentFrame,
                flipX ? position.x - offsetX + renderWidth : position.x - offsetX,
                position.y + offsetY,
                flipX ? -renderWidth : renderWidth,
                renderHeight
            );
        }

        // Reset color after effects
        if (isTransitioning || isInvincible) {
            batch.setColor(Color.WHITE);
        }
    }

    // UPDATED: Enhanced setCurrentState to handle ducking properly
    public void setCurrentState(State newState) {
        if (this.currentState != newState) {
            State oldState = this.currentState;
            this.currentState = newState;
            this.stateTimer = 0; // Reset state timer on state change

            // Handle ducking state changes
            if (newState == State.DUCKING && !isDucking) {
                startDucking();
            } else if (oldState == State.DUCKING && newState != State.DUCKING) {
                stopDucking();
            }
        }
    }

    // UPDATED: Method to change power state with ducking consideration
    public void setPowerState(PowerState newPowerState) {
        if (this.powerState != newPowerState) {
            PowerState oldPowerState = this.powerState;
            startPowerTransition(newPowerState);

            // If we were ducking, maintain ducking with new size
            if (isDucking) {
                updateBounds(); // Recalculate ducking bounds for new power state
                Gdx.app.log("Player", "Power state changed while ducking. New duck height: " + bounds.height);
            }
        }
    }

    // NEW: Getter for ducking state
    public boolean isDucking() {
        return isDucking;
    }

    // UPDATED: Enhanced respawn method to clear invincibility
    public void respawn(float x, float y) {
        position.set(x, y);
        velocity.set(0, 0);
        setCurrentState(State.IDLE);
        setPowerState(PowerState.SMALL); // Reset to small Mario on respawn
        isDucking = false; // Make sure not ducking on respawn
        grounded = true; // Assume respawn on ground
        facingRight = true; // Default facing direction
        stateTimer = 0f;
        isTransitioning = false; // Reset any transition
        transitionTimer = 0f;

        // NEW: Clear invincibility on respawn
        isInvincible = false;
        invincibilityTimer = 0f;

        updateBounds(); // Make sure bounds are correct
        bounds.setPosition(position.x, position.y);
        Gdx.app.log("Player", "Mario respawned as SMALL");
    }

    // UPDATED: Enhanced die method to clear invincibility
    public void die() {
        if (currentState != State.DEATH) {
            setCurrentState(State.DEATH);
            velocity.y = DEATH_JUMP_VELOCITY; // Mario-style upward pop
            velocity.x = 0; // Stop horizontal movement
            grounded = false; // Player is in the air
            stateTimer = 0f; // Reset timer for the death sequence duration

            // NEW: Clear invincibility when dying
            isInvincible = false;
            invincibilityTimer = 0f;

            SoundManager.getInstance().playPlayerDeath(); // Play death sound
            Gdx.app.log("Player", "Mario died");
        }
    }

    // UPDATED: Enhanced getFrame method to handle ducking textures
    public Texture getFrame() {
        // Use death texture for death state (same for all power states)
        if (currentState == State.DEATH) {
            return marioDeathTexture;
        }

        // Choose texture set based on power state
        boolean useBigMario = (powerState == PowerState.BIG || powerState == PowerState.FIRE);

        switch(currentState){
            case WALKING:
                // Don't show walking animation while ducking
                if (isDucking) {
                    return useBigMario ? bigDuckTexture : smallDuckTexture;
                }
                return useBigMario ? bigWalkAnimation.getKeyFrame(stateTimer, true)
                    : smallWalkAnimation.getKeyFrame(stateTimer, true);
            case JUMPING:
                // Can't duck while jumping
                return useBigMario ? bigJumpTexture : smallJumpTexture;
            case DUCKING:
                return useBigMario ? bigDuckTexture : smallDuckTexture;
            case FALLING:
                // Can't duck while falling
                return useBigMario ? bigFallTexture : smallFallTexture;
            case IDLE:
            default:
                // Show ducking texture if ducking, even in idle state
                if (isDucking) {
                    return useBigMario ? bigDuckTexture : smallDuckTexture;
                }
                return useBigMario ? bigIdleTexture : smallIdleTexture;
        }
    }

    // NEW: Method to check if Mario can move while ducking
    public boolean canMoveWhileDucking() {
        return isDucking && grounded;
    }

    // NEW: Get the height difference between normal and ducking states
    public float getDuckingHeightDifference() {
        if (powerState == PowerState.SMALL) {
            return SMALL_MARIO_HEIGHT - SMALL_MARIO_DUCK_HEIGHT;
        } else {
            return BIG_MARIO_HEIGHT - BIG_MARIO_DUCK_HEIGHT;
        }
    }
    // Added for Mario-style death animation
    private static final float DEATH_JUMP_VELOCITY = 350f;
    private static final float DEATH_GRAVITY = -900f;

    public Player(float x, float y){
        position = new Vector2(x,y);
        velocity = new Vector2(0, 0);
        facingRight = true; // Player starts facing right
        grounded = false;
        currentState = State.IDLE;
        previousState = State.IDLE;
        powerState = PowerState.SMALL; // Start as small Mario

        // Initialize bounds based on power state
        updateBounds();

        // Load all texture sets
        loadSmallMarioTextures();
        loadBigMarioTextures();
        loadSharedTextures();

        // Create animations
        createAnimations();
    }

    // NEW: Load Small Mario textures
    private void loadSmallMarioTextures() {
        try {
            smallIdleTexture = new Texture("mario_sprites/playables/mario/mario_idle.png");
            smallWalkTexture0 = new Texture("mario_sprites/playables/mario/mario_walk_0.png");
            smallWalkTexture1 = new Texture("mario_sprites/playables/mario/mario_walk_1.png");
            smallWalkTexture2 = new Texture("mario_sprites/playables/mario/mario_walk_2.png");
            smallDuckTexture = new Texture("mario_sprites/playables/mario/mario_duck.png");
            smallJumpTexture = new Texture("mario_sprites/playables/mario/mario_jump.png");
            smallLookUpTexture = new Texture("mario_sprites/playables/mario/mario_look_up.png");
            smallPipeTexture = new Texture("mario_sprites/playables/mario/mario_pipe.png");
            smallFallTexture = new Texture("mario_sprites/playables/mario/mario_fall.png");

            Gdx.app.log("Player", "Successfully loaded Small Mario textures");
        } catch (Exception e) {
            Gdx.app.error("Player", "Failed to load some Small Mario textures", e);
        }
    }

    // NEW: Load Big Mario textures
    private void loadBigMarioTextures() {
        try {
            bigIdleTexture = new Texture("mario_sprites/playables/big_mario/big_mario_idle.png");
            bigWalkTexture0 = new Texture("mario_sprites/playables/big_mario/big_mario_walk_0.png");
            bigWalkTexture1 = new Texture("mario_sprites/playables/big_mario/big_mario_walk_1.png");
            bigWalkTexture2 = new Texture("mario_sprites/playables/big_mario/big_mario_walk_2.png");
            bigDuckTexture = new Texture("mario_sprites/playables/big_mario/big_mario_duck.png");
            bigJumpTexture = new Texture("mario_sprites/playables/big_mario/big_mario_jump.png");
            bigLookUpTexture = new Texture("mario_sprites/playables/big_mario/big_mario_look_up.png");
            bigPipeTexture = new Texture("mario_sprites/playables/big_mario/big_mario_pipe.png");
            bigFallTexture = new Texture("mario_sprites/playables/big_mario/big_mario_fall.png");

            Gdx.app.log("Player", "Successfully loaded Big Mario textures");
        } catch (Exception e) {
            Gdx.app.error("Player", "Failed to load some Big Mario textures, using Small Mario textures as fallback", e);
            // Use small mario textures as fallback
            bigIdleTexture = smallIdleTexture;
            bigWalkTexture0 = smallWalkTexture0;
            bigWalkTexture1 = smallWalkTexture1;
            bigWalkTexture2 = smallWalkTexture2;
            bigDuckTexture = smallDuckTexture;
            bigJumpTexture = smallJumpTexture;
            bigLookUpTexture = smallLookUpTexture;
            bigPipeTexture = smallPipeTexture;
            bigFallTexture = smallFallTexture;
        }
    }

    // NEW: Load shared textures (like death)
    private void loadSharedTextures() {
        try {
            // Try to load mario death texture
            String deathSpritePath = "mario_sprites/playables/mario/mario_death.png";
            if (Gdx.files.internal(deathSpritePath).exists()) {
                marioDeathTexture = new Texture(Gdx.files.internal(deathSpritePath));
            } else {
                marioDeathTexture = smallFallTexture; // Use fall texture as fallback
                Gdx.app.log("Player", "mario_death.png not found, using fall texture for death animation.");
            }
        } catch (Exception e) {
            Gdx.app.error("Player", "Failed to load shared textures", e);
            marioDeathTexture = smallFallTexture;
        }
    }

    // NEW: Create animations for both power states
    private void createAnimations() {
        // Small Mario walk animation
        Array<Texture> smallWalkFrames = new Array<>();
        smallWalkFrames.add(smallWalkTexture0);
        smallWalkFrames.add(smallWalkTexture1);
        smallWalkFrames.add(smallWalkTexture2);
        smallWalkAnimation = new Animation<>(0.1f, smallWalkFrames, Animation.PlayMode.LOOP);

        // Big Mario walk animation
        Array<Texture> bigWalkFrames = new Array<>();
        bigWalkFrames.add(bigWalkTexture0);
        bigWalkFrames.add(bigWalkTexture1);
        bigWalkFrames.add(bigWalkTexture2);
        bigWalkAnimation = new Animation<>(0.1f, bigWalkFrames, Animation.PlayMode.LOOP);
    }

    // UPDATED: Method to update bounds based on power state AND ducking state
    private void updateBounds() {
        float width, height;

        // Determine base width and height
        switch (powerState) {
            case SMALL:
                width = SMALL_MARIO_WIDTH;
                height = isDucking ? SMALL_MARIO_DUCK_HEIGHT : SMALL_MARIO_HEIGHT;
                break;
            case BIG:
            case FIRE:
                width = BIG_MARIO_WIDTH;
                height = isDucking ? BIG_MARIO_DUCK_HEIGHT : BIG_MARIO_HEIGHT;
                break;
            default:
                width = SMALL_MARIO_WIDTH;
                height = isDucking ? SMALL_MARIO_DUCK_HEIGHT : SMALL_MARIO_HEIGHT;
                break;
        }

        if (bounds == null) {
            bounds = new Rectangle(position.x, position.y, width, height);
        } else {
            // When changing size, keep the bottom position consistent
            float oldBottom = bounds.y;
            bounds.width = width;
            bounds.height = height;
            bounds.y = oldBottom; // Keep Mario grounded
            bounds.x = position.x;
        }

        // Store bounds for ducking state management
        if (isDucking) {
            duckingBounds = new Rectangle(bounds);
        } else {
            normalBounds = new Rectangle(bounds);
        }
    }

    // NEW: Start ducking
    public void startDucking() {
        if (!isDucking && grounded) { // Can only duck when on ground
            isDucking = true;
            updateBounds();
            setCurrentState(State.DUCKING);

            // Reduce horizontal velocity when ducking
            velocity.x *= 0.3f;

            Gdx.app.log("Player", "Mario started ducking. New height: " + bounds.height);
        }
    }

    // NEW: Method to start power state transition
    private void startPowerTransition(PowerState newPowerState) {
        if (this.powerState != newPowerState) {
            this.targetPowerState = newPowerState;
            this.isTransitioning = true;
            this.transitionTimer = 0f;

            // Immediately update bounds for gameplay
            this.powerState = newPowerState;
            updateBounds();
        }
    }
    public PowerState getPowerState() {
        return powerState;
    }
    private boolean isInvincible = false;
    private float invincibilityTimer = 0f;
    private static final float INVINCIBILITY_DURATION = 2.0f; // 2 seconds of invincibility
    private static final float INVINCIBILITY_FLASH_RATE = 8f; // Flashing speed during invincibility
    /**
     * Start invincibility frames (typically after taking damage)
     */
    public void startInvincibility() {
        isInvincible = true;
        invincibilityTimer = INVINCIBILITY_DURATION;
        Gdx.app.log("Player", "Started invincibility for " + INVINCIBILITY_DURATION + " seconds");
    }


    // Method to power up Mario
    public void powerUp() {
        switch (powerState) {
            case SMALL:
                setPowerState(PowerState.BIG);
                SoundManager.getInstance().playGrow();
                Gdx.app.log("Player", "Mario powered up to BIG");
                break;
            case BIG:
                setPowerState(PowerState.FIRE);
                SoundManager.getInstance().playPowerup();
                Gdx.app.log("Player", "Mario powered up to FIRE");
                break;
            case FIRE:
                // Already at max power, just give points
                SoundManager.getInstance().playItemGet();
                Gdx.app.log("Player", "Mario already at max power");
                break;
        }
    }

    /**
     * Check if player is currently invincible
     */
    public boolean isInvincible() {
        return isInvincible;
    }

    // UPDATED: Enhanced powerDown method with invincibility
    public void powerDown() {
        switch (powerState) {
            case FIRE:
                setPowerState(PowerState.BIG);
                startInvincibility(); // NEW: Start invincibility after power down
                SoundManager.getInstance().playGrow(); // You might want a different sound here
                Gdx.app.log("Player", "Mario powered down to BIG with invincibility");
                break;
            case BIG:
                setPowerState(PowerState.SMALL);
                startInvincibility(); // NEW: Start invincibility after power down
                SoundManager.getInstance().playGrow(); // You might want a different sound here
                Gdx.app.log("Player", "Mario powered down to SMALL with invincibility");
                break;
            case SMALL:
                // Already small, Mario dies (no invincibility can save from this)
                die();
                return;
        }
    }



    // NEW: Check if player is currently transitioning between power states
    public boolean isTransitioning() {
        return isTransitioning;
    }

    // Existing getters and setters...
    public Vector2 getPosition() {
        return position;
    }

    public void setPosition(Vector2 position) {
        this.position = position;
        bounds.setPosition(position.x, position.y);
    }

    public Vector2 getVelocity() {
        return velocity;
    }

    public void setVelocity(Vector2 velocity) {
        this.velocity = velocity;
    }

    public boolean isFacingRight() {
        return facingRight;
    }

    public void setFacingRight(boolean facingRight) {
        this.facingRight = facingRight;
    }

    public boolean isGrounded() {
        return grounded;
    }

    public void setGrounded(boolean grounded) {
        this.grounded = grounded;
    }

    public State getCurrentState() {
        return currentState;
    }


    public State getPreviousState() {
        return previousState;
    }

    public void setPreviousState(State previousState) {
        this.previousState = previousState;
    }

    public float getStateTimer() {
        return stateTimer;
    }

    public void setStateTimer(float stateTimer) {
        this.stateTimer = stateTimer;
    }

    public Rectangle getBounds() {
        return bounds;
    }

    public void setBounds(Rectangle bounds) {
        this.bounds = bounds;
    }

    // UPDATED: Dispose method to handle all textures
    public void dispose(){
        // Small Mario textures
        if (smallIdleTexture != null) smallIdleTexture.dispose();
        if (smallWalkTexture0 != null) smallWalkTexture0.dispose();
        if (smallWalkTexture1 != null) smallWalkTexture1.dispose();
        if (smallWalkTexture2 != null) smallWalkTexture2.dispose();
        if (smallJumpTexture != null) smallJumpTexture.dispose();
        if (smallFallTexture != null) smallFallTexture.dispose();
        if (smallDuckTexture != null) smallDuckTexture.dispose();
        if (smallLookUpTexture != null) smallLookUpTexture.dispose();
        if (smallPipeTexture != null) smallPipeTexture.dispose();

        // Big Mario textures (only dispose if different from small mario)
        if (bigIdleTexture != null && bigIdleTexture != smallIdleTexture) bigIdleTexture.dispose();
        if (bigWalkTexture0 != null && bigWalkTexture0 != smallWalkTexture0) bigWalkTexture0.dispose();
        if (bigWalkTexture1 != null && bigWalkTexture1 != smallWalkTexture1) bigWalkTexture1.dispose();
        if (bigWalkTexture2 != null && bigWalkTexture2 != smallWalkTexture2) bigWalkTexture2.dispose();
        if (bigJumpTexture != null && bigJumpTexture != smallJumpTexture) bigJumpTexture.dispose();
        if (bigFallTexture != null && bigFallTexture != smallFallTexture) bigFallTexture.dispose();
        if (bigDuckTexture != null && bigDuckTexture != smallDuckTexture) bigDuckTexture.dispose();
        if (bigLookUpTexture != null && bigLookUpTexture != smallLookUpTexture) bigLookUpTexture.dispose();
        if (bigPipeTexture != null && bigPipeTexture != smallPipeTexture) bigPipeTexture.dispose();

        // Shared textures
        if (marioDeathTexture != null && marioDeathTexture != smallFallTexture) {
            marioDeathTexture.dispose();
        }
    }

    // Legacy getters for compatibility (now delegate to appropriate power state)
    public Texture getIdleTexture() {
        return powerState == PowerState.SMALL ? smallIdleTexture : bigIdleTexture;
    }

    public Texture getDuckTexture() {
        return powerState == PowerState.SMALL ? smallDuckTexture : bigDuckTexture;
    }

    public Texture getJumpTexture() {
        return powerState == PowerState.SMALL ? smallJumpTexture : bigJumpTexture;
    }

    public Texture getWalkTexture0() {
        return powerState == PowerState.SMALL ? smallWalkTexture0 : bigWalkTexture0;
    }

    public Texture getWalkTexture1() {
        return powerState == PowerState.SMALL ? smallWalkTexture1 : bigWalkTexture1;
    }

    public Texture getFallTexture() {
        return powerState == PowerState.SMALL ? smallFallTexture : bigFallTexture;
    }

    public Texture getPipeTexture() {
        return powerState == PowerState.SMALL ? smallPipeTexture : bigPipeTexture;
    }

    public Texture getLookUpTexture() {
        return powerState == PowerState.SMALL ? smallLookUpTexture : bigLookUpTexture;
    }

    public Animation<Texture> getWalkAnimation() {
        return powerState == PowerState.SMALL ? smallWalkAnimation : bigWalkAnimation;
    }
    private boolean isDucking = false;
    private Rectangle normalBounds; // Store normal bounds when ducking
    private Rectangle duckingBounds; // Store ducking bounds
    public static final int SMALL_MARIO_DUCK_HEIGHT = 15; // Half of normal small mario height
    public static final int BIG_MARIO_DUCK_HEIGHT = 24;   // Half of normal big mario height

    // Legacy setters (no longer needed but kept for compatibility)
    public void setIdleTexture(Texture idleTexture) { /* No-op */ }
    public void setDuckTexture(Texture duckTexture) { /* No-op */ }
    public void setJumpTexture(Texture jumpTexture) { /* No-op */ }
    public void setWalkTexture0(Texture walkTexture0) { /* No-op */ }
    public void setWalkTexture1(Texture walkTexture1) { /* No-op */ }
    public void setFallTexture(Texture fallTexture) { /* No-op */ }
    public void setPipeTexture(Texture pipeTexture) { /* No-op */ }
    public void setLookUpTexture(Texture lookUpTexture) { /* No-op */ }
    public void setWalkAnimation(Animation<Texture> walkAnimation) { /* No-op */ }
}
