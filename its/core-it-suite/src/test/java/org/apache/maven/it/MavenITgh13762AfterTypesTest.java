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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks the {@code @After} types besides {@code CHILDREN} in the concurrent builder, complementing
 * {@link MavenITgh13762AfterAllVsAggregatorTest}.
 * <ul>
 *   <li>{@code PROJECT}: section 3.5 of {@code src/site/markdown/aggregator-goals.md} proposes to replace a forked
 *       lifecycle, {@code @Execute(phase = "package")}, by {@code @After(phase = "package", type = PROJECT)}. A fork
 *       runs the phase, while the constraint only orders the goal relative to the phase if the phase is part of the
 *       build anyway, and an ordering against a later phase of the same project is silently dropped by
 *       {@code BuildStep.executeAfter()}. The tests compare what a forking goal and its replacement see.</li>
 *   <li>{@code DEPENDENCIES}: the dependency-based workaround for aggregation is a reporting module that depends on
 *       the modules it reports on. The default lifecycle of the concurrent builder only makes a module wait for
 *       {@code ready} and {@code package} of its dependencies, not for their tests. The tests check whether a goal
 *       bound to {@code verify} in the reporting module sees its dependencies verified, with a plain Maven 3 goal
 *       and with a Maven 4 goal declaring {@code @After(phase = "verify", type = DEPENDENCIES)}. Module {@code b}
 *       delays its {@code verify} marker by five seconds, so that the result does not depend on timing.</li>
 * </ul>
 * <p>
 * Each probe writes what it observed to {@code target/probe.txt} of the module it runs in.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class MavenITgh13762AfterTypesTest extends AbstractMavenIntegrationTestCase {

    private static final String RESOURCES = "gh-13762-after-all-vs-aggregator";

    private static final String FORK_CLAIM = "Replacing @Execute(phase = \"package\") by"
            + " @After(phase = \"package\", type = PROJECT) must not change what the goal sees";

    private static final String REPORT_CLAIM =
            "A goal in a reporting module bound to verify must see all of its dependencies verified";

    private static final String BOTH_DEPENDENCIES_VERIFIED = "module a verified, module b verified";

    private static final String FORK_GOAL = "org.apache.maven.its.gh13762:probe-maven-plugin:1.0-SNAPSHOT:fork-probe";

    private static final String AFTER_PROJECT_GOAL =
            "org.apache.maven.its.gh13762:probe-v4-maven-plugin:1.0-SNAPSHOT:after-project-probe";

    /**
     * Fairness check: bound to a phase after {@code package}, the forking goal and its replacement both see their
     * module packaged. Expected to pass.
     */
    @Test
    void testAfterProjectReplacesForkWhenBoundAfterTargetPhase() throws Exception {
        // GIVEN
        final Path single = prepare("single");

        // WHEN
        final String forked = observe(single, "fork-at-verify", true, "-Pfork-at-verify", "verify");
        final String migrated = observe(single, "after-project-at-verify", true, "-Pafter-project-at-verify", "verify");

        // THEN
        assertEquals("own module packaged", forked, "Unexpected behavior of the forking goal");
        assertEquals(forked, migrated, FORK_CLAIM);
    }

    /**
     * Bound to {@code compile}, which precedes {@code package}, the forking goal still sees its module packaged,
     * because the fork runs {@code package} first. For the replacement, the ordering against the later phase would
     * be a cycle; {@code BuildStep.executeAfter()} drops it without notice, and the goal runs at {@code compile}.
     */
    @Test
    void testAfterProjectReplacesForkWhenBoundBeforeTargetPhase() throws Exception {
        // GIVEN
        final Path single = prepare("single");

        // WHEN
        final String forked = observe(single, "fork-at-compile", true, "-Pfork-at-compile", "package");
        final String migrated =
                observe(single, "after-project-at-compile", true, "-Pafter-project-at-compile", "package");

        // THEN
        assertEquals(forked, migrated, FORK_CLAIM);
    }

    /**
     * Invoked directly on the command line, the forking goal sees its module packaged. {@code @After} constraints
     * only apply to lifecycle bindings, so the replacement runs without anything being packaged.
     */
    @Test
    void testAfterProjectReplacesForkInDirectInvocation() throws Exception {
        // GIVEN
        final Path single = prepare("single");

        // WHEN
        final String forked = observe(single, "fork-cli", true, FORK_GOAL);
        final String migrated = observe(single, "after-project-cli", true, AFTER_PROJECT_GOAL);

        // THEN
        assertEquals(forked, migrated, FORK_CLAIM);
    }

    /**
     * Fairness check: with the default builder, every module is built completely before the modules depending on it,
     * so a plain goal in the reporting module sees both dependencies verified. Expected to pass.
     */
    @Test
    void testReportingModuleSeesVerifiedDependenciesWithDefaultBuilder() throws Exception {
        // GIVEN
        final Path reporting = prepare("reporting");

        // WHEN
        final String observed = observe(reporting, "report-plain-default", false, "-Preport-plain", "verify");

        // THEN
        assertEquals(BOTH_DEPENDENCIES_VERIFIED, observed, REPORT_CLAIM);
    }

    /**
     * With the concurrent builder, the reporting module only waits for {@code package} of its dependencies. A plain
     * goal bound to {@code verify}, which is what Maven 3 plugins like aggregated coverage reports are limited to,
     * runs while module {@code b} is still verifying.
     */
    @Test
    void testReportingModuleSeesVerifiedDependenciesWithConcurrentBuilder() throws Exception {
        // GIVEN
        final Path reporting = prepare("reporting");

        // WHEN
        final String observed = observe(reporting, "report-plain-concurrent", true, "-Preport-plain", "verify");

        // THEN
        assertEquals(BOTH_DEPENDENCIES_VERIFIED, observed, REPORT_CLAIM);
    }

    /**
     * With the concurrent builder, a Maven 4 goal declaring {@code @After(phase = "verify", type = DEPENDENCIES)}
     * waits for {@code verify} of the dependencies of its module. Expected to pass.
     */
    @Test
    void testReportingModuleWithAfterDependenciesSeesVerifiedDependencies() throws Exception {
        // GIVEN
        final Path reporting = prepare("reporting");

        // WHEN
        final String observed = observe(
                reporting, "report-after-dependencies", true, "-Preport-after-dependencies", "verify");

        // THEN
        assertEquals(BOTH_DEPENDENCIES_VERIFIED, observed, REPORT_CLAIM);
    }

    private Path prepare(final String fixture) throws Exception {
        final Path testDir = extractResources(RESOURCES);
        install(testDir.resolve("plugin"));
        install(testDir.resolve("plugin-v4"));
        return testDir.resolve(fixture);
    }

    private void install(final Path project) throws Exception {
        final Verifier verifier = newVerifier(project);
        verifier.addCliArgument("install");
        verifier.execute();
        verifier.verifyErrorFreeLog();
    }

    /**
     * Builds the given fixture and returns what the probe recorded: the content of {@code target/probe.txt} of the
     * module in which the probe runs ({@code single} or {@code report}), {@code "did not run"}, or
     * {@code "build failed"}.
     */
    private String observe(final Path fixture, final String label, final boolean concurrent, final String... args)
            throws Exception {
        final Path probe = Files.exists(fixture.resolve("report"))
                ? fixture.resolve("report/target/probe.txt")
                : fixture.resolve("target/probe.txt");
        clean(fixture);

        final Verifier verifier = newVerifier(fixture);
        verifier.setForkJvm(true);
        verifier.setLogFileName("log-" + label + ".txt");
        final List<String> arguments = new ArrayList<>();
        if (concurrent) {
            arguments.addAll(List.of("-b", "concurrent", "-T2"));
        }
        arguments.addAll(List.of(args));
        verifier.addCliArguments(arguments.toArray(String[]::new));
        try {
            verifier.execute();
            verifier.verifyErrorFreeLog();
        } catch (VerificationException e) {
            return "build failed";
        }
        return Files.exists(probe) ? Files.readString(probe).trim() : "did not run";
    }

    private void clean(final Path fixture) throws Exception {
        final Verifier verifier = newVerifier(fixture);
        for (String path : new String[] {"target", "a/target", "b/target", "report/target"}) {
            verifier.deleteDirectory(path);
        }
    }
}
