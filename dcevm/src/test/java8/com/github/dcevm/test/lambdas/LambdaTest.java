/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

package com.github.dcevm.test.lambdas;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests for lambda expressions.
 * <p>
 * These lambdas are sneaky. First, it seems like generated lambda method names are arbitrary and depend
 * on the compilation order. However, for redefinition test we want to make sure that generated method names would
 * actually match in old and new versions, so we have keep classes being redefined outside of this inner class.
 *
 * @author Ivan Dubrov
 */
public class LambdaTest {

  @Before
  @After
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  @Test
  public void testMethodLambda() throws Exception {
    LambdaA a = new LambdaA();
    Callable<Integer> lambda = a.createLambda();
    Callable<Integer> lambda2 = a.createLambda2();

    assertEquals(10, (int) lambda.call());
    assertEquals(20, (int) lambda2.call());

    __toVersion__(1, LambdaA___1.class);

    assertEquals(30, (int) lambda.call());
    assertEquals(40, (int) lambda2.call());
  }

  // version 0
  public static class LambdaC2 {
    public int i = 0;

    public void doit() {
      LambdaTest.methodWithLambdaParam(integer -> i += integer);
    }
  }

  // version 1
  public static class LambdaC2___1 {
    int i = 0;

    public void doit() {
      LambdaTest.methodWithLambdaParam(integer -> i -= integer);
    }
  }

  public static void methodWithLambdaParam(Consumer<Integer> consumer) {
    consumer.accept(1);
  }

  @Test
  public void testMethodLambda2() throws Exception {
    assert __version__() == 0;

    final LambdaC2 instance = new LambdaC2();
    instance.doit();
    Assert.assertEquals(1, instance.i);

    __toVersion__(1);

    instance.doit();
    Assert.assertEquals(0, instance.i);
  }

  // version 0
  public static class LambdaC3 {
    public int i = 0;
    public Consumer<Integer> l = x -> i += x;

    public void doit() {
      LambdaTest.methodWithLambdaParam(l);
    }
  }

  // version 1
  public static class LambdaC3___1 {
    int i = 0;
    public Consumer<Integer> l = null;

    public void doit() {
      LambdaTest.methodWithLambdaParam(l);
    }
  }

  @Test
  public void testMethodLambda3() throws Exception {
    assert __version__() == 0;

    final LambdaC3 instance = new LambdaC3();
    instance.doit();
    Assert.assertEquals(1, instance.i);

    __toVersion__(1);

    try {
      instance.doit();
      fail("Should get NoSuchMethodError as method implementing lambda should be gone in version 1");
    } catch (NoSuchMethodError e) {
      // Ok!
    }
  }
}