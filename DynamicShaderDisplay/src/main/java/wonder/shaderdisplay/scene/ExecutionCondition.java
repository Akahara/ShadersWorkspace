package wonder.shaderdisplay.scene;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import wonder.shaderdisplay.Time;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.DEDUCTION;

@JsonTypeInfo(use = DEDUCTION)
@JsonSubTypes({
        @JsonSubTypes.Type(ExecutionCondition.OnPresetCondition.class),
        @JsonSubTypes.Type(ExecutionCondition.TimeBasedCondition.class)
})
public abstract class ExecutionCondition {

    public int count = 1;

    public abstract boolean isConditionPassing(ExecutionContext context);

    public static ExecutionCondition alwaysPassing() {
        return new OnPresetCondition(OnPresetCondition.Type.ALWAYS);
    }

    public static ExecutionCondition[] alwaysPassOnce() {
        return new ExecutionCondition[] { alwaysPassing() };
    }

    public static class TimeBasedCondition extends ExecutionCondition {

        public int afterFrame = -1, beforeFrame = -1;
        public float afterTime = -1, beforeTime = -1;

        @Override
        public boolean isConditionPassing(ExecutionContext context) {
            return (afterFrame == -1 || Time.getFrame() >= afterFrame)
                    && (beforeFrame == -1 || Time.getFrame() <= beforeFrame)
                    && (afterTime == -1 || Time.getTime() >= afterTime)
                    && (beforeTime == -1 || Time.getTime() <= beforeTime);
        }
    }

    public static class OnPresetCondition extends ExecutionCondition {

        public enum Type {
            ON_RESET,
            ALWAYS;

            @JsonValue
            public String serialName() { return name().toLowerCase(); }
        }

        @JsonProperty(required = true)
        public Type type;

        @SuppressWarnings("unused")
        public OnPresetCondition() {}
        public OnPresetCondition(Type type) { this.type = type; }

        @Override
        public boolean isConditionPassing(ExecutionContext context) {
            return switch (type) {
                case ON_RESET -> context.hasReset;
                case ALWAYS -> true;
            };
        }

    }

    public static class ExecutionContext {

        public boolean hasReset;

    }

}
