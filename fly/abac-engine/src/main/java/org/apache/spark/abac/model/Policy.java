package org.apache.spark.abac.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Represents an ABAC policy with rules and metadata.
 */
public class Policy {
    private final String id;
    private final String name;
    private final String description;
    private final String version;
    private final List<Rule> rules;
    private final PolicyTarget target;
    private final PolicyEffect defaultEffect;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final boolean enabled;
    private final int priority;

    @JsonCreator
    public Policy(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("version") String version,
            @JsonProperty("rules") List<Rule> rules,
            @JsonProperty("target") PolicyTarget target,
            @JsonProperty("defaultEffect") PolicyEffect defaultEffect,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("updatedAt") Instant updatedAt,
            @JsonProperty("enabled") boolean enabled,
            @JsonProperty("priority") int priority) {
        this.id = Objects.requireNonNull(id, "Policy ID cannot be null");
        this.name = Objects.requireNonNull(name, "Policy name cannot be null");
        this.description = description;
        this.version = version != null ? version : "1.0";
        this.rules = Objects.requireNonNull(rules, "Policy rules cannot be null");
        this.target = target;
        this.defaultEffect = defaultEffect != null ? defaultEffect : PolicyEffect.DENY;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
        this.enabled = enabled;
        this.priority = priority;
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

    public String getVersion() {
        return version;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public PolicyTarget getTarget() {
        return target;
    }

    public PolicyEffect getDefaultEffect() {
        return defaultEffect;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Policy policy = (Policy) o;
        return Objects.equals(id, policy.id) && Objects.equals(version, policy.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version);
    }

    @Override
    public String toString() {
        return "Policy{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", enabled=" + enabled +
                ", priority=" + priority +
                '}';
    }
} 