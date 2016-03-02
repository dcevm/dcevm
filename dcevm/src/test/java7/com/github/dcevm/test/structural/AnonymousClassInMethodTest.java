package com.github.dcevm.test.structural;

import org.junit.Assert;
import org.junit.Before;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;

/**
 * Test insertion and swap of anonymous classes.
 */
public class AnonymousClassInMethodTest {

  public static interface I {
    public boolean m();
  }

  ;

  public static interface I2 {
  }

  ;

  // Version 0
  public static class A {
    public boolean test() {
      I anonymous = new I() {
        @Override
        public boolean m() {
          return true;
        }
      };
      return anonymous.m();
    }
  }

  // Version 1
  public static class A___1 {
    public boolean test() {
      I2 insertedAnonymous = new I2() {
      };

      I anonymous = new I() {
        @Override
        public boolean m() {
          return false;
        }
      };
      return anonymous.m();
    }
  }


  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  // TODO this test fails, because conent of A$1 is now interface I2 instead of interface I (not compatible change)
  // HotswapAgent plugin AnonymousClassPatch solves this on Java instrumentation level by exchanging content of class files.
  // @see https://github.com/HotswapProjects/HotswapAgent/tree/master/HotswapAgent/src/main/java/org/hotswap/agent/plugin/jvm
  //@Test
  public void testAnonymous() {
    assert __version__() == 0;
    Assert.assertTrue(new A().test());
    __toVersion__(1);
    Assert.assertFalse(new A().test());
    __toVersion__(0);
    Assert.assertTrue(new A().test());
  }
}
