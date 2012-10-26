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

package at.ssw.hotswap.test.structural;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import at.ssw.hotswap.HotSwapTool;

import java.util.Arrays;

/**
 * A small geometry example application including a Point and a Rectangle class.
 *
 * @author Thomas Wuerthinger
 */
public class GeometryScenario {

    @Before
    public void setUp() throws Exception {
        HotSwapTool.toVersion(GeometryScenario.class, 0);
    }

    // Version 0
    public static class Point {

        private int x;
        private int y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public boolean isBottomRightOf(Point p) {
            return p.x >= x && p.y >= y;
        }

        public boolean isTopLeftOf(Point p) {
            return p.x <= x && p.y <= y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }
    }

    public static interface IFigure {

        public boolean isHitAt(Point p);
    }

    public static class Rectangle {

        private Point topLeft;
        private Point bottomRight;

        public Rectangle(Point p1, Point p2) {
            topLeft = p1;
            bottomRight = p2;
        }

        public boolean isHitAt(Point p) {
            return p.isBottomRightOf(topLeft) && !p.isTopLeftOf(bottomRight);
        }

        public Point getTopLeft() {
            return topLeft;
        }

        public Point getBottomRight() {
            return bottomRight;
        }

        public static Rectangle create(Point p) {
            return (Rectangle) (Object) (new Rectangle___1(p));
        }
    }

    // Version 1
    public static class Rectangle___1 implements IFigure {

        private Point topLeft;
        private Point center;
        private Point bottomRight;

        public Point getCenter() {
            return center;
        }

        public Rectangle___1(Point p) {
            System.err.println("!!!");
            System.err.println(Arrays.toString(getClass().getDeclaredFields()));
            topLeft = p;
            bottomRight = p;
        }

        public boolean isHitAt(Point p) {
            return p.isBottomRightOf(topLeft) && !p.isTopLeftOf(bottomRight);
        }

        public Point getTopLeft() {
            return topLeft;
        }

        public Point getBottomRight() {
            return bottomRight;
        }

        public static Rectangle create(Point p) {
            return (Rectangle) (Object) (new Rectangle___1(p));
        }
    }

    public static class Point___1 {

        private char x1;
        private int y;
        private char x2;

        public boolean isBottomRightOf(Point p) {
            return p.x >= x1 && p.y >= y;
        }

        public boolean isTopLeftOf(Point p) {
            return p.x <= x1 && p.y <= y;
        }

        public int getY() {
            return y;
        }

        public int getX() {
            return x1;
        }

        public char getX2() {
            return x2;
        }
    }

    @Test
    public void testContructorChange() {

        assert HotSwapTool.getCurrentVersion(GeometryScenario.class) == 0;

        Point p1 = new Point(1, 2);
        Point p2 = new Point(3, 4);
        Rectangle r1 = new Rectangle(p1, p2);

        assertEquals(1, p1.getX());
        assertEquals(2, p1.getY());
        assertEquals(3, p2.getX());
        assertEquals(4, p2.getY());
        assertEquals(p1, r1.getTopLeft());
        assertEquals(p2, r1.getBottomRight());

        HotSwapTool.toVersion(GeometryScenario.class, 1);

        r1 = Rectangle.create(p1);
        assertEquals(0, p1.getX());
        assertEquals(2, p1.getY());
        assertEquals(0, p2.getX());
        assertEquals(4, p2.getY());
        assertEquals(p1, r1.getTopLeft());
        assertEquals(p1, r1.getBottomRight());

        HotSwapTool.toVersion(GeometryScenario.class, 0);

        assertEquals(0, p1.getX());
        assertEquals(2, p1.getY());
        assertEquals(0, p2.getX());
        assertEquals(4, p2.getY());
        assertEquals(p1, r1.getTopLeft());
        assertEquals(p1, r1.getBottomRight());
    }

    @Test
    public void testSingleGeometry() {

        assert HotSwapTool.getCurrentVersion(GeometryScenario.class) == 0;

        Point p1 = new Point(1, 2);
        Point p2 = new Point(3, 4);
        Rectangle r1 = new Rectangle(p1, p2);

        assertEquals(1, p1.getX());
        assertEquals(2, p1.getY());
        assertEquals(3, p2.getX());
        assertEquals(4, p2.getY());
        assertEquals(p1, r1.getTopLeft());
        assertEquals(p2, r1.getBottomRight());

        HotSwapTool.toVersion(GeometryScenario.class, 1);

        System.out.println("after redef");
        assertEquals(0, p1.getX());
        System.out.println("1");
        assertEquals(2, p1.getY());
        System.out.println("2");
        assertEquals(0, p2.getX());
        System.out.println("3");
        assertEquals(4, p2.getY());
        System.out.println("4");
        assertEquals(p1, r1.getTopLeft());
        System.out.println("5");
        assertEquals(p2, r1.getBottomRight());
        System.out.println("6");

        HotSwapTool.toVersion(GeometryScenario.class, 0);

        assertEquals(0, p1.getX());
        assertEquals(2, p1.getY());
        assertEquals(0, p2.getX());
        assertEquals(4, p2.getY());
        assertEquals(p1, r1.getTopLeft());
        assertEquals(p2, r1.getBottomRight());
    }
}
