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
import java.nio.file.Path;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * A goal that forks the lifecycle up to {@code package} ({@code @Execute(phase = PACKAGE)}), as goals like
 * {@code source:jar} or {@code javadoc:javadoc} do. Records whether its own module had been packaged, for example
 * {@code own module packaged}, in {@code target/probe.txt}.
 */
@Mojo(name = "fork-probe")
@Execute(phase = LifecyclePhase.PACKAGE)
public class ForkProbeMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project.basedir}", readonly = true, required = true)
    private File basedir;

    @Override
    public void execute() throws MojoExecutionException {
        Path module = basedir.toPath();
        Markers.write(
                module.resolve("target/probe.txt"),
                Markers.describe("own module", module.resolve("target/touched.txt"), "packaged"));
    }
}
