package com.github.dcevm.test.lambdas;

import java.io.Serializable;

public class LambdaA implements Serializable {
  public SerializableCallable<Integer> createLambda() {
    return () -> 10;
  }

  public SerializableCallable<Integer> createLambda2() {
    return () -> 20;
  }
}
