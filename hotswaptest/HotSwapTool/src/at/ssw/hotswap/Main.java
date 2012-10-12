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

package at.ssw.hotswap;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Small program for using class HotSwapTool from the command line.
 * Example:
 * java at.ssw.hotswap.Main File1.class File2.class
 * 
 * @author Thomas Wuerthinger
 */
public class Main {

	public static final int PORT = 4000;
	public static final int TOOL_PORT = 1234;

	public static void main(String[] args) {
		System.out.println("Starting HotSwapTool");
		if (args.length == 0) {
			System.out.println("Usage: Specify class file names you want to hotswap.");
			return;
		}
		
		try {
			System.out.println("Using port " + PORT + " to connect to target VM");
			System.out.println("Redefining classes");
			
			List<File> klasses = new ArrayList<File>();
			for(int i=0; i<args.length; i++) {
				klasses.add(new File(args[i]));
			}
			
			try {
				HotSwapTool.redefine(klasses, new HashMap<String, String>());
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} finally {
				HotSwapTool.shutdown();
			}
				
		} catch(HotSwapException e) {
			e.printStackTrace();
		}
	}
}