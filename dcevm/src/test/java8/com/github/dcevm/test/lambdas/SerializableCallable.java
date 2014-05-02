package com.github.dcevm.test.lambdas;

import java.io.Serializable;
import java.util.concurrent.Callable;

/**
 * Created by idubrov on 5/2/14.
 */
public interface SerializableCallable<T> extends Callable<T>, Serializable {
}
