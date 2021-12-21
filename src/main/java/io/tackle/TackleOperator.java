package io.tackle;

import io.javaoperatorsdk.operator.Operator;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

import javax.inject.Inject;

@QuarkusMain
public class TackleOperator implements QuarkusApplication {

    @Inject
    Operator operator;
    
    public static void main(String... args) {
        Quarkus.run(TackleOperator.class, args);
    }

    @Override
    public int run(String... args) throws Exception {
        System.out.println("I'm going to exit now!");
        System.exit(-1);
        operator.start();
        Quarkus.waitForExit();
        return 0;
    }
}
