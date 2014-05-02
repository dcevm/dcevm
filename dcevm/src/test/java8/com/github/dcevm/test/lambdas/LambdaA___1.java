package com.github.dcevm.test.lambdas;

import java.util.concurrent.Callable;

public class LambdaA___1 {

    public Callable<Integer> createLambda() {
        return () -> 30;
    }

    public Callable<Integer> createLambda2() {
        return () -> 40;
    }
}
