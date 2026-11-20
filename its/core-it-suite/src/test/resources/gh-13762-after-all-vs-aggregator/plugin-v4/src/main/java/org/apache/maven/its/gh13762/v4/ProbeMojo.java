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

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.api.Project;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.plugin.annotations.After;
import org.apache.maven.api.plugin.annotations.Mojo;

/**
 * The probe in the migrated form proposed instead of {@code after:all}: a Maven 4 goal without the deprecated
 * {@code aggregator} flag that declares its dependency on the child modules with {@code @After(type = CHILDREN)}.
 * It records the same observation as the probes of the Maven 3 plugin of this test.
 */
@Mojo(name = "probe", defaultPhase = "package")
@After(phase = "package", type = After.Type.CHILDREN)
public class ProbeMojo implements org.apache.maven.api.plugin.Mojo {

    @Inject
    private Project project;

    @Override
    public void execute() throws Exception {
        Path root = project.getBasedir();
        boolean moduleAPackaged = Files.exists(root.resolve("a/target/touched.txt"));
        boolean moduleBPackaged = Files.exists(root.resolve("b/target/touched.txt"));
        String observation = describe("a", moduleAPackaged) + ", " + describe("b", moduleBPackaged);
        Path output = root.resolve("target/probe.txt");
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
