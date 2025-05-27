package org.apache.spark.abac.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * Defines the target scope for a rule - similar to PolicyTarget but at rule level.
 */
public class RuleTarget {
    private final List<String> resources;
    private final List<String> actions;
    private final List<String> subjects;

    @JsonCreator
    public RuleTarget(
            @JsonProperty("resources") List<String> resources,
            @JsonProperty("actions") List<String> actions,
            @JsonProperty("subjects") List<String> subjects) {
        this.resources = resources;
        this.actions = actions;
        this.subjects = subjects;
    }

    public List<String> getResources() {
        return resources;
    }

    public List<String> getActions() {
        return actions;
    }

    public List<String> getSubjects() {
        return subjects;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RuleTarget that = (RuleTarget) o;
        return Objects.equals(resources, that.resources) &&
                Objects.equals(actions, that.actions) &&
                Objects.equals(subjects, that.subjects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resources, actions, subjects);
    }

    @Override
    public String toString() {
        return "RuleTarget{" +
                "resources=" + resources +
                ", actions=" + actions +
                ", subjects=" + subjects +
                '}';
    }
} 