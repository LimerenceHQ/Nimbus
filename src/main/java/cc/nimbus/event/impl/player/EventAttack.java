package cc.nimbus.event.impl.player;

import cc.nimbus.event.Cancelable;
import net.minecraft.world.entity.Entity;

public class EventAttack extends Cancelable {
    public enum Stage {
        PRE,
        POST
    }

    private Entity entity;
    private Stage stage;

    public EventAttack() {
        this(null, Stage.PRE);
    }

    public EventAttack(Entity entity) {
        this(entity, Stage.PRE);
    }

    public EventAttack(Entity entity, Stage stage) {
        this.entity = entity;
        this.stage = stage;
    }

    public EventAttack set(Entity entity, Stage stage) {
        reset();
        this.entity = entity;
        this.stage = stage;
        return this;
    }

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
    }

    public Stage getStage() {
        return stage;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }
}
