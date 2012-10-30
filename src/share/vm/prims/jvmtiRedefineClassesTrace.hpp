/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
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

#define IF_TRACE_RC1 if (TraceRedefineClasses >= 1) 
#define IF_TRACE_RC2 if (TraceRedefineClasses >= 2) 
#define IF_TRACE_RC3 if (TraceRedefineClasses >= 3) 
#define IF_TRACE_RC4 if (TraceRedefineClasses >= 4) 
#define IF_TRACE_RC5 if (TraceRedefineClasses >= 5) 

#define TRACE_RC1 if (TraceRedefineClasses >= 1) tty->print("TraceRedefineClasses-1: "); if (TraceRedefineClasses >= 1) tty->print_cr
#define TRACE_RC2 if (TraceRedefineClasses >= 2) tty->print("   TraceRedefineClasses-2: "); if (TraceRedefineClasses >= 2) tty->print_cr
#define TRACE_RC3 if (TraceRedefineClasses >= 3) tty->print("      TraceRedefineClasses-3: "); if (TraceRedefineClasses >= 3) tty->print_cr
#define TRACE_RC4 if (TraceRedefineClasses >= 4) tty->print("         TraceRedefineClasses-4: "); if (TraceRedefineClasses >= 4) tty->print_cr
#define TRACE_RC5 if (TraceRedefineClasses >= 5) tty->print("            TraceRedefineClasses-5: "); if (TraceRedefineClasses >= 5) tty->print_cr

// Timer support macros. Only do timer operations if timer tracing
// is enabled. The "while (0)" is so we can use semi-colon at end of
// the macro.
#define RC_TIMER_START(t) \
  if (TimeRedefineClasses) { \
    t.start(); \
  } while (0)
#define RC_TIMER_STOP(t) \
  if (TimeRedefineClasses) { \
    t.stop(); \
  } while (0)
