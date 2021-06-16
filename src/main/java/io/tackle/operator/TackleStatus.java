package io.tackle.operator;

import io.fabric8.kubernetes.api.model.Condition;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RegisterForReflection
public class TackleStatus {

    private List<Condition> conditions = new ArrayList<>();

    public List<Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<Condition> conditions) {
        this.conditions = conditions;
    }

    public void addCondition(Condition condition) {
        this.conditions.add(condition);
    }

    public void addConditions(Collection<Condition> conditions) {
        this.conditions.addAll(conditions);
    }

}
