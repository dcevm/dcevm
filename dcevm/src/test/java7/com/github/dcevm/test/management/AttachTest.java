package com.github.dcevm.test.management;

import com.sun.tools.attach.VirtualMachine;
import org.junit.Test;

import java.lang.management.ManagementFactory;

public class AttachTest {

  @Test
  public void attachToSelfIsOk() throws Exception {
    String nameOfRunningVM = ManagementFactory.getRuntimeMXBean().getName();
    int p = nameOfRunningVM.indexOf('@');
    String pid = nameOfRunningVM.substring(0, p);
    VirtualMachine vm = VirtualMachine.attach(pid);
    vm.detach();
  }
}
