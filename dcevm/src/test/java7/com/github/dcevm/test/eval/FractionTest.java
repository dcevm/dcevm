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

package com.github.dcevm.test.eval;

import com.github.dcevm.HotSwapTool;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;

/**
 * @author Thomas Wuerthinger
 */
public class FractionTest {

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
    assert __version__() == 0;
  }

  // Version 0
  public static class NoChange {

    int i1;
    int i2;
    int i3;
    Object o1;
    Object o2;
    Object o3;
  }

  public static class Change {

    int i1;
    int i2;
    int i3;
    Object o1;
    Object o2;
    Object o3;
  }

  // Version 1
  public static class Change___1 {

    int i1;
    int i2;
    int i3;
    Object o1;
    Object o2;
    Object o3;
    Object o4;
  }

  // Version 2
  public static class Change___2 {

    int i1;
    int i2;
    int i3;
    Object o1;
  }

  // Version 3
  public static class Change___3 {

    int i3;
    int i1;
    int i2;
    Object o3;
    Object o1;
    Object o2;
  }

  // Version 3
  public static class Change___4 {

    int i1;
    int i2;
    int i3;
    Object o1;
    Object o2;
    Object o3;
  }

  // Version 2
  public static class Change___5 {

  }

  private static List<Long> measurements = new ArrayList<Long>();
  private final int BASE = 10;
  private Object[] objects;

  private void clear() {
    objects = null;
    System.gc();
    System.gc();
    __toVersion__(0);
    System.gc();
    System.gc();

  }

  private void init(int count, int percent) {
    objects = new Object[count];
    int changed = 0;
    int unchanged = 0;
    for (int k = 0; k < count; k++) {
      if ((count / BASE) * percent <= k/* && k >= 200000*/) {
        objects[k] = new NoChange();
        unchanged++;
      } else {
        objects[k] = new Change();
        changed++;
      }
    }

    System.gc();

    System.out.println(changed + " changed objects allocated");
  }

  @Test
  public void testBase() {

    assert __version__() == 0;

    final int N = 1;
    final int INC = 4;
    final int SIZE = 4000;

    int[] benchmarking = new int[]{SIZE};
    int base = BASE;
    int start = 0;

    MicroBenchmark[] benchmarks = new MicroBenchmark[]{new GCMicroBenchmark(), new IncreaseMicroBenchmark(), new DecreaseMicroBenchmark(), new ReorderMicroBenchmark(), new NoRealChangeMicroBenchmark(), new BigDecreaseMicroBenchmark()};

    clear();
    for (int k = 0; k < N; k++) {
      for (MicroBenchmark m : benchmarks) {
        for (int i : benchmarking) {
          System.out.println(m.getClass().getName() + " with " + i + " objects");
          for (int j = start; j <= base; j += INC) {
            System.out.println(j);
            m.init(i);
            init(i, j);
            m.doit(i, measurements);
            clear();
          }
        }
      }
    }

    System.out.println("Results:");
    for (long l : measurements) {
      System.out.println(l);
    }
    measurements.clear();
  }
}

abstract class MicroBenchmark {

  public void init(int count) {
  }

  public abstract void doit(int count, List<Long> measurements);
}

class GCMicroBenchmark extends MicroBenchmark {

  @Override
  public void doit(int count, List<Long> measurements) {
    long startTime = System.currentTimeMillis();
    System.gc();
    long curTime = System.currentTimeMillis() - startTime;
    measurements.add(curTime);
  }
}

class IncreaseMicroBenchmark extends MicroBenchmark {

  @Override
  public void doit(int count, List<Long> measurements) {
    HotSwapTool.resetTimings();
    __toVersion__(1);
    measurements.add(HotSwapTool.getTotalTime());
  }
}

class DecreaseMicroBenchmark extends MicroBenchmark {

  @Override
  public void doit(int count, List<Long> measurements) {
    HotSwapTool.resetTimings();
    __toVersion__(2);
    measurements.add(HotSwapTool.getTotalTime());
  }
}

class ReorderMicroBenchmark extends MicroBenchmark {

  @Override
  public void doit(int count, List<Long> measurements) {
    HotSwapTool.resetTimings();
    __toVersion__(3);
    measurements.add(HotSwapTool.getTotalTime());
  }
}

class NoRealChangeMicroBenchmark extends MicroBenchmark {

  @Override
  public void doit(int count, List<Long> measurements) {
    HotSwapTool.resetTimings();
    __toVersion__(4);
    measurements.add(HotSwapTool.getTotalTime());
  }
}

class BigDecreaseMicroBenchmark extends MicroBenchmark {

  @Override
  public void doit(int count, List<Long> measurements) {
    HotSwapTool.resetTimings();
    __toVersion__(5);
    measurements.add(HotSwapTool.getTotalTime());
  }
}
