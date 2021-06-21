package io.tackle.operator;

import io.fabric8.kubernetes.api.model.Condition;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.ArrayList;
import java.util.List;

@RegisterForReflection
public class TackleStatus {

    private List<Condition> conditions = new ArrayList<>();

    public List<Condition> getConditions() {
        return conditions;
    }

}
