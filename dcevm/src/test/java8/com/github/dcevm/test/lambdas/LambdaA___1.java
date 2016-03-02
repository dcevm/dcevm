package com.github.dcevm.test.lambdas;

import java.io.Serializable;

public class LambdaA___1 implements Serializable {

  public SerializableCallable<Integer> createLambda() {
    return () -> 30;
  }

  public SerializableCallable<Integer> createLambda2() {
    return () -> 40;
  }
}
