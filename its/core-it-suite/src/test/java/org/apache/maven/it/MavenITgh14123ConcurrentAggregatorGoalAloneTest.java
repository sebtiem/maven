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
package org.apache.maven.it;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Reproduces a failure of the concurrent builder: an aggregator goal ({@code aggregator = true}) invoked alone on the
 * command line in a multi-module build fails with a {@code NullPointerException} in {@code BuildPlan.addStep()}
 * before any goal runs.
 * <p>
 * The goal forms an aggregating task segment, which {@code BuildPlanExecutor.buildInitialPlan()} calculates for the
 * root project only, but the planning steps are then added for every project of the reactor. A lifecycle phase in
 * front of the goal adds a non-aggregating task segment with all projects and hides the problem, which the second
 * test shows.
 * <p>
 * The builds run in a forked JVM and each test has a timeout, so that a build that never finishes fails the test
 * instead of blocking the whole suite.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class MavenITgh14123ConcurrentAggregatorGoalAloneTest extends AbstractMavenIntegrationTestCase {

    private static final String RESOURCES = "gh-14123-concurrent-aggregator-goal-alone";

    private static final String AGGREGATOR_GOAL =
            "org.apache.maven.its.gh14123:aggregator-maven-plugin:1.0-SNAPSHOT:aggregate";

    private static final String MARKER = "target/aggregate.txt";

    /**
     * Invoked alone, the aggregator goal must run once, on the root project, and the build must succeed. Currently
     * fails with a {@code NullPointerException} in {@code BuildPlan.addStep()}.
     */
    @Test
    void testAggregatorGoalAloneRunsOnceOnRootProject() throws Exception {
        // GIVEN
        final Path reactor = prepare();

        // WHEN
        final Verifier verifier = build(reactor, "goal-alone", AGGREGATOR_GOAL);

        // THEN
        verifier.verifyErrorFreeLog();
        verifier.verifyFilePresent(MARKER);
        verifier.verifyFileNotPresent("a/" + MARKER);
        verifier.verifyFileNotPresent("b/" + MARKER);
    }

    /**
     * Control case: with a lifecycle phase in front of it, the aggregator goal runs once, on the root project.
     * Passes today.
     */
    @Test
    void testAggregatorGoalAfterPhaseRunsOnceOnRootProject() throws Exception {
        // GIVEN
        final Path reactor = prepare();

        // WHEN
        final Verifier verifier = build(reactor, "goal-after-phase", "validate", AGGREGATOR_GOAL);

        // THEN
        verifier.verifyErrorFreeLog();
        verifier.verifyFilePresent(MARKER);
        verifier.verifyFileNotPresent("a/" + MARKER);
        verifier.verifyFileNotPresent("b/" + MARKER);
    }

    private Path prepare() throws Exception {
        final Path testDir = extractResources(RESOURCES);

        final Verifier plugin = newVerifier(testDir.resolve("plugin"));
        plugin.addCliArgument("install");
        plugin.execute();
        plugin.verifyErrorFreeLog();

        final Path reactor = testDir.resolve("reactor");
        final Verifier cleaner = newVerifier(reactor);
        for (String path : new String[] {"target", "a/target", "b/target"}) {
            cleaner.deleteDirectory(path);
        }
        return reactor;
    }

    private Verifier build(final Path reactor, final String label, final String... tasks) throws Exception {
        final Verifier verifier = newVerifier(reactor);
        verifier.setForkJvm(true);
        verifier.setLogFileName("log-" + label + ".txt");
        verifier.addCliArguments("-b", "concurrent", "-T2");
        verifier.addCliArguments(tasks);
        verifier.execute();
        return verifier;
    }
}
