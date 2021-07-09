/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 */

package jdk.jfr.event.runtime;

import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.jfr.TestClassLoader;

/**
 * @test
 * @summary The test verifies that classes overriding finalize(), loaded as well as unloaded, are represented as events.
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm -Xlog:class+unload -Xlog:gc -Xmx16m jdk.jfr.event.runtime.TestFinalizerEvent
 */

public final class TestFinalizerEvent {
    private final static String TEST_CLASS_NAME = "jdk.jfr.event.runtime.TestFinalizerEvent$TestClassOverridingFinalize";
    private final static String TEST_CLASS_UNLOAD_NAME = "jdk.jfr.event.runtime.TestFinalizerEvent$TestClassUnloadOverridingFinalize";
    private final static String EVENT_PATH = EventNames.Finalizer;

    // Declare unloadableClassLoader as "public static"
    // to prevent the compiler to optimize away all unread writes
    public static TestClassLoader unloadableClassLoader;

    public static void main(String[] args) throws Throwable {
        Recording recording1 = new Recording();
        recording1.enable(EVENT_PATH);
        Recording recording2 = new Recording();
        recording2.enable(EVENT_PATH);
        TestClassLoader cl = new TestClassLoader();
        unloadableClassLoader = new TestClassLoader();

        recording1.start();
        cl.loadClass(TEST_CLASS_NAME);
        recording2.start(); // rotation writes an event for TEST_CLASS_NAME into recording1
        unloadableClassLoader.loadClass(TEST_CLASS_UNLOAD_NAME);
        unloadableClassLoader = null;
        System.gc(); // the unloading of class TEST_CLASS_UNLOAD_NAME is intercepted and an event is written into both recording1 and recording2

        recording2.stop(); // rotation writes an event for TEST_CLASS_NAME into both recording1 and recording2
        recording1.stop(); // rotation writes an event for TEST_CLASS_NAME into recording1 which now has 4 events reflecting this test case (3 chunks + 1 unload)

        try {
            verify(recording2);
            verify(recording1);
        }
        finally {
            recording2.close();
            recording1.close();
        }
    }

    private static void verify(Recording recording) throws Throwable {
        boolean foundTestClassName = false;
        boolean foundTestClassUnloadName = false;
        List<RecordedEvent> events = Events.fromRecording(recording);
        Events.hasEvents(events);
        for (RecordedEvent event : events) {
          System.out.println("Event:" + event);
          RecordedClass overridingClass = event.getValue("overridingClass");
          switch (overridingClass.getName()) {
              case TEST_CLASS_NAME: {
                  foundTestClassName = true;
                  break;
              }
              case TEST_CLASS_UNLOAD_NAME: {
                  foundTestClassUnloadName = true;
                  break;
              }
          }
          if (foundTestClassName && foundTestClassUnloadName) {
              break;
          }
        }
        Asserts.assertTrue(foundTestClassName, "The class: " + TEST_CLASS_NAME + " overriding finalize() is not found");
        Asserts.assertTrue(foundTestClassUnloadName, "The class: " + TEST_CLASS_UNLOAD_NAME + " overriding finalize() is not found");
    }

    static class TestClassOverridingFinalize {
        public boolean finalized = false;

        @Override
        protected void finalize() {
            finalized = true;
        }
    }

    static class TestClassUnloadOverridingFinalize {
        public boolean finalized = false;

        @Override
        protected void finalize() {
            finalized = true;
        }
    }
}
