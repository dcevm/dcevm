package com.github.dcevm;


import com.github.dcevm.agent.InstrumentationAgent;

import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.util.Map;

public class InstrumentationRedefiner implements Redefiner {
  public void redefineClasses(Map<Class<?>, byte[]> classes) throws ClassNotFoundException, UnmodifiableClassException {
    Instrumentation instrumentation = InstrumentationAgent.INSTRUMENTATION;
    if (instrumentation == null) {
      throw new IllegalStateException("Instrumentation agent is not properly installed!");
    }

    ClassDefinition[] definitions = new ClassDefinition[classes.size()];
    int i = 0;
    for (Map.Entry<Class<?>, byte[]> entry : classes.entrySet()) {
      definitions[i++] = new ClassDefinition(entry.getKey(), entry.getValue());
    }
    instrumentation.redefineClasses(definitions);
  }

  @Override
  public void close() throws IOException {
    // Do nothing.
  }
}
