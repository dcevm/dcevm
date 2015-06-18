package com.github.dcevm.test.management;

import org.junit.Test;

import java.lang.management.ManagementFactory;

public class ManagementTest {

  @Test
  public void testJMXIsFine() {
    // https://github.com/dcevm/dcevm/issues/69
    // Verify management.dll could be loaded
    ManagementFactory.getRuntimeMXBean();
    ManagementFactory.getPlatformMBeanServer();
  }
}
