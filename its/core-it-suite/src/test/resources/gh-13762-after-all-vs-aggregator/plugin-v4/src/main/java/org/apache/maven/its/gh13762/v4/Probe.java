/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.its.gh13762.v4;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Records which child modules had completed their {@code touch} goal at the moment a probe goal runs, by writing
 * for example {@code module a packaged, module b not packaged} to {@code target/probe.txt} of the module it runs in.
 * This is the same observation as recorded by the probes of the Maven 3 plugin of this test.
 */
final class Probe {

    private Probe() {}

    static void record(Path basedir) throws IOException {
        boolean moduleAPackaged = Files.exists(basedir.resolve("a/target/touched.txt"));
        boolean moduleBPackaged = Files.exists(basedir.resolve("b/target/touched.txt"));
        String observation = describe("a", moduleAPackaged) + ", " + describe("b", moduleBPackaged);
        Path output = basedir.resolve("target/probe.txt");
        Files.createDirectories(output.getParent());
        Files.writeString(output, observation);
    }

    /**
     * Describes whether a child module had completed its {@code package} phase, for example
     * {@code module a packaged} or {@code module b not packaged}.
     */
    private static String describe(String module, boolean packaged) {
        return "module " + module + (packaged ? " packaged" : " not packaged");
    }
}
