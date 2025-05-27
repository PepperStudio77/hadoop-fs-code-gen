package org.apache.spark.abac.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;

/**
 * Defines the target scope for a policy - what resources, actions, and subjects it applies to.
 */
public class PolicyTarget {
    private final List<String> resources;
    private final List<String> actions;
    private final List<String> subjects;
    private final List<String> environments;

    @JsonCreator
    public PolicyTarget(
            @JsonProperty("resources") List<String> resources,
            @JsonProperty("actions") List<String> actions,
            @JsonProperty("subjects") List<String> subjects,
            @JsonProperty("environments") List<String> environments) {
        this.resources = resources;
        this.actions = actions;
        this.subjects = subjects;
        this.environments = environments;
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

    public List<String> getEnvironments() {
        return environments;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PolicyTarget that = (PolicyTarget) o;
        return Objects.equals(resources, that.resources) &&
                Objects.equals(actions, that.actions) &&
                Objects.equals(subjects, that.subjects) &&
                Objects.equals(environments, that.environments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resources, actions, subjects, environments);
    }

    @Override
    public String toString() {
        return "PolicyTarget{" +
                "resources=" + resources +
                ", actions=" + actions +
                ", subjects=" + subjects +
                ", environments=" + environments +
                '}';
    }
} 