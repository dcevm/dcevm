package com.github.dcevm.agent;

import java.lang.instrument.Instrumentation;

/**
 * Simple agent to get access to the Instrumentation API.
 */
public class InstrumentationAgent {
    public static Instrumentation INSTRUMENTATION;

    public static void agentmain(String args, Instrumentation instr) {
        INSTRUMENTATION = instr;
    }

    public static void premain(String args, Instrumentation instr) {
        INSTRUMENTATION = instr;
    }
}
