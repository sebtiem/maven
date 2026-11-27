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
package org.apache.maven.its.gh13762;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Records which child modules had completed their {@code touch} goal at the moment this goal runs, by writing
 * for example {@code module a packaged, module b not packaged} to {@code target/probe.txt} of the module it runs in.
 * <p>
 * The two subclasses only differ in the {@code aggregator} flag, so the same probe can be run once as a legacy
 * aggregator and once in the form recommended for migration (a plain goal bound to {@code after:all}).
 */
abstract class AbstractProbeMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project.basedir}", readonly = true, required = true)
    private File basedir;

    @Override
    public void execute() throws MojoExecutionException {
        Path root = basedir.toPath();
        boolean moduleAPackaged = Files.exists(root.resolve("a/target/touched.txt"));
        boolean moduleBPackaged = Files.exists(root.resolve("b/target/touched.txt"));
        String observation = describe("a", moduleAPackaged) + ", " + describe("b", moduleBPackaged);
        Path output = root.resolve("target/probe.txt");
        try {
            Files.createDirectories(output.getParent());
            Files.write(output, observation.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MojoExecutionException("Could not write " + output, e);
        }
    }

    /**
     * Describes whether a child module had completed its {@code package} phase, for example
     * {@code module a packaged} or {@code module b not packaged}.
     */
    private static String describe(String module, boolean packaged) {
        return "module " + module + (packaged ? " packaged" : " not packaged");
    }
}
