package org.apache.spark.abac.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * Represents a single rule within an ABAC policy.
 */
public class Rule {
    private final String id;
    private final String name;
    private final String description;
    private final RuleTarget target;
    private final List<Condition> conditions;
    private final PolicyEffect effect;
    private final int priority;
    private final boolean enabled;

    @JsonCreator
    public Rule(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("target") RuleTarget target,
            @JsonProperty("conditions") List<Condition> conditions,
            @JsonProperty("effect") PolicyEffect effect,
            @JsonProperty("priority") int priority,
            @JsonProperty("enabled") boolean enabled) {
        this.id = Objects.requireNonNull(id, "Rule ID cannot be null");
        this.name = Objects.requireNonNull(name, "Rule name cannot be null");
        this.description = description;
        this.target = target;
        this.conditions = conditions;
        this.effect = Objects.requireNonNull(effect, "Rule effect cannot be null");
        this.priority = priority;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public RuleTarget getTarget() {
        return target;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public PolicyEffect getEffect() {
        return effect;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Rule rule = (Rule) o;
        return Objects.equals(id, rule.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Rule{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", effect=" + effect +
                ", priority=" + priority +
                ", enabled=" + enabled +
                '}';
    }
} 