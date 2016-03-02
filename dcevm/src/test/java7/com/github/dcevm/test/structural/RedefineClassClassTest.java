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
package com.github.dcevm.test.structural;

import com.github.dcevm.ClassRedefinitionPolicy;
import com.github.dcevm.test.category.Full;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static com.github.dcevm.test.util.HotSwapTestHelper.__toVersion__;
import static com.github.dcevm.test.util.HotSwapTestHelper.__version__;

/**
 * Smallest test case for redefining the interface java/lang/reflect/Type (causes java/lang/Class being redefined)
 *
 * @author Thomas Wuerthinger
 */
@Category(Full.class)
@Ignore
public class RedefineClassClassTest {

  // Version 0
  public interface Type {
  }

  // Version 1
  @ClassRedefinitionPolicy(alias = java.lang.reflect.Type.class)
  public interface Type___1 {
  }

  @Before
  public void setUp() throws Exception {
    __toVersion__(0);
  }

  @Test
  public void testRedefineClass() {

    assert __version__() == 0;

    __toVersion__(1);

    __toVersion__(0);

    __toVersion__(1);

    __toVersion__(0);


  }
}
