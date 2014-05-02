package com.github.dcevm.test.lambdas;

import java.util.concurrent.Callable;

public class LambdaA {
    public Callable<Integer> createLambda() {
        return () -> 10;
    }

    public Callable<Integer> createLambda2() {
        return () -> 20;
    }
}
