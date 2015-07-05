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

package com.github.dcevm.test.fields;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static org.junit.Assert.*;

/**
 * @author Ivan Dubrov
 */
public class EnumTest {

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  enum A {
    FIRST,
    SECOND {
    },
    OTHER,
    ANOTHER;


    private final static A THIRD = FIRST;
    private final static A FOURTH = null;
  }

  enum A___1 {
    SECOND {
    },
    THIRD,
    FOURTH;

    public final static A___1 FIRST = FOURTH;
    public final static A___1 OTHER = null;
  }

  @Test
  public void testEnumFields() throws Exception {
    A second = A.SECOND;
    assertEquals(4, A.values().length);
    assertNotNull(A.values()[0]);
    assertNotNull(A.values()[1]);

    __toVersion__(1);

    assertEquals(3, A.values().length);
    assertNotNull(A.values()[0]);
    assertNotNull(A.values()[1]);
    assertNotNull(A.values()[2]);

    assertSame("literal instance is the same", second, A.SECOND);
    assertEquals("THIRD static non-literal field should not be copied", "THIRD", A.values()[1].name());
    assertEquals("FOURTH static non-literal field should not be copied", "FOURTH", A.values()[2].name());
    assertSame("literal should not be copied to a non-literal static field", A.FIRST, A.valueOf("FOURTH"));
    assertNull("literal should not be copied to a non-literal static field", A.OTHER);

    // Ordinal was transferred
    assertEquals(0, second.ordinal());

    // Array was updated
    assertSame(second, A.values()[0]);
  }
}
