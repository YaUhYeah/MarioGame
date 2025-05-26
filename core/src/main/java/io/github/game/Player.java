// File: core/src/main/java/io/github/game/Player.java
package io.github.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

public class Player {

    private Texture deathTexture;
    public static final float DEATH_ANIMATION_DURATION = 1.5f; // Already present, good duration
    public enum State {
        IDLE, FALLING, WALKING, RUNNING, JUMPING, DUCKING, OPENING_DOOR, FULL_SPEED_JUMPING, DEATH, GROUND_POUNDING, GOING_DOWN_PIPE;
    }
    private Vector2 position;
    private Vector2 velocity;
    private boolean facingRight;
    private boolean grounded;
    private State currentState;
    private State previousState;
    private float stateTimer;

    public static final int PLAYER_WIDTH = 32;
    public static final int PLAYER_HEIGHT = 48;

    private Rectangle bounds;
    private Texture idleTexture;
    private Texture duckTexture;
    private Texture jumpTexture;
    private Texture walkTexture0;
    private Texture walkTexture1;
    private Texture walkTexture2;
    private Texture fallTexture;
    private Texture pipeTexture;
    private Texture lookUpTexture;
    private Animation<Texture> walkAnimation;

    // Added for Mario-style death animation
    private static final float DEATH_JUMP_VELOCITY = 350f;
    private static final float DEATH_GRAVITY = -900f; // Independent gravity for death sequence

    public Player(float x, float y){
        position = new Vector2(x,y);
        velocity = new Vector2(0, 0);
        facingRight = true; // Player starts facing right
        grounded = false;
        currentState = State.IDLE;
        previousState = State.IDLE;
        bounds = new Rectangle(x, y, PLAYER_WIDTH, PLAYER_HEIGHT);
        idleTexture = new Texture("mario_sprites/playables/mario/mario_idle.png");
        walkTexture0 = new Texture("mario_sprites/playables/mario/mario_walk_0.png");
        walkTexture1 = new Texture("mario_sprites/playables/mario/mario_walk_1.png");
        walkTexture2 = new Texture("mario_sprites/playables/mario/mario_walk_2.png");
        duckTexture = new Texture("mario_sprites/playables/mario/mario_duck.png");
        jumpTexture = new Texture("mario_sprites/playables/mario/mario_jump.png");
        lookUpTexture = new Texture("mario_sprites/playables/mario/mario_look_up.png");
        pipeTexture = new Texture("mario_sprites/playables/mario/mario_pipe.png");
        fallTexture = new Texture("mario_sprites/playables/mario/mario_fall.png");

        // Corrected deathTexture loading
        String deathSpritePath = "mario_sprites/playables/mario/mario_death.png";
        if (Gdx.files.internal(deathSpritePath).exists()) {
            deathTexture = new Texture(Gdx.files.internal(deathSpritePath));
        } else {
            deathTexture = fallTexture; // Use fallTexture as a fallback
            Gdx.app.log("Player", "'" + deathSpritePath + "' not found, using fall texture for death animation.");
        }

        Array<Texture> walkFrames = new Array<Texture>();
        walkFrames.add(walkTexture0);
        walkFrames.add(walkTexture1);
        walkFrames.add(walkTexture2);
        walkAnimation = new Animation<Texture>(0.1f, walkFrames, Animation.PlayMode.LOOP);
    }

    public void update(float deltaTime){
        stateTimer = currentState == previousState ? stateTimer + deltaTime : 0;
        previousState = currentState;

        if (currentState == State.DEATH) {
            // Apply death physics (vertical jump and fall)
            velocity.y += DEATH_GRAVITY * deltaTime;
            position.y += velocity.y * deltaTime;
            // position.x remains unchanged as velocity.x is set to 0 in die()
            bounds.setPosition(position.x, position.y);
            // No platform collision checks during the death animation itself
        } else {
            // Existing update logic for non-death states
            bounds.setPosition(position.x, position.y);
            // Condition for falling state based on vertical velocity and not being grounded
            if (velocity.y < 0 && !grounded && currentState != State.FALLING && currentState != State.DUCKING && currentState != State.GOING_DOWN_PIPE) {
                setCurrentState(State.FALLING);
            }
        }
    }

    public void render(SpriteBatch batch){
        Texture currentFrame = getFrame();

        boolean flipX = !facingRight; // Player faces right by default, flip if not facingRight

        // Standard drawing logic
        batch.draw(
            currentFrame,
            flipX ? position.x + PLAYER_WIDTH : position.x, // Adjust x for flipped sprite
            position.y,
            flipX ? -PLAYER_WIDTH : PLAYER_WIDTH, // Negative width to flip
            PLAYER_HEIGHT
        );
    }

    public Texture getFrame() {
        switch(currentState){
            case WALKING:
                return walkAnimation.getKeyFrame(stateTimer, true);
            case JUMPING:
                return jumpTexture;
            case DUCKING:
                return duckTexture;
            case FALLING:
                return fallTexture;
            case DEATH: // Use deathTexture for the death animation
                return deathTexture;
            case IDLE:
            default:
                return idleTexture;
        }
    }

    public Vector2 getPosition() {
        return position;
    }

    public void setPosition(Vector2 position) {
        this.position = position;
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

    public void setCurrentState(State currentState) {
        if (this.currentState != currentState) {
            this.currentState = currentState;
            this.stateTimer = 0; // Reset state timer on state change
        }
    }
    public void dispose(){
        idleTexture.dispose();
        walkTexture0.dispose();
        walkTexture1.dispose();
        walkTexture2.dispose();
        jumpTexture.dispose();
        fallTexture.dispose();
        duckTexture.dispose();
        lookUpTexture.dispose();
        pipeTexture.dispose();
        if (deathTexture != null && deathTexture != fallTexture) { // Dispose only if it's a unique loaded texture
            deathTexture.dispose();
        }
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

    public void die() {
        if (currentState != State.DEATH) {
            setCurrentState(State.DEATH);
            velocity.y = DEATH_JUMP_VELOCITY; // Mario-style upward pop
            velocity.x = 0; // Stop horizontal movement
            grounded = false; // Player is in the air
            stateTimer = 0f; // Reset timer for the death sequence duration
            SoundManager.getInstance().playPlayerDeath(); // Play death sound
        }
    }
    public void respawn(float x, float y) {
        position.set(x, y);
        velocity.set(0, 0);
        setCurrentState(State.IDLE);
        grounded = true; // Assume respawn on ground
        facingRight = true; // Default facing direction
        stateTimer = 0f;
    }
    public Texture getIdleTexture() { return idleTexture; }
    public void setIdleTexture(Texture idleTexture) { this.idleTexture = idleTexture; }
    public Texture getDuckTexture() { return duckTexture; }
    public void setDuckTexture(Texture duckTexture) { this.duckTexture = duckTexture; }
    public Texture getJumpTexture() { return jumpTexture; }
    public void setJumpTexture(Texture jumpTexture) { this.jumpTexture = jumpTexture; }
    public Texture getWalkTexture0() { return walkTexture0; }
    public void setWalkTexture0(Texture walkTexture0) { this.walkTexture0 = walkTexture0; }
    public Texture getWalkTexture1() { return walkTexture1; }
    public void setWalkTexture1(Texture walkTexture1) { this.walkTexture1 = walkTexture1; }
    public Texture getFallTexture() { return fallTexture; }
    public void setFallTexture(Texture fallTexture) { this.fallTexture = fallTexture; }
    public Texture getPipeTexture() { return pipeTexture; }
    public void setPipeTexture(Texture pipeTexture) { this.pipeTexture = pipeTexture; }
    public Texture getLookUpTexture() { return lookUpTexture; }
    public void setLookUpTexture(Texture lookUpTexture) { this.lookUpTexture = lookUpTexture; }
    public Animation<Texture> getWalkAnimation() { return walkAnimation; }
    public void setWalkAnimation(Animation<Texture> walkAnimation) { this.walkAnimation = walkAnimation; }
}
