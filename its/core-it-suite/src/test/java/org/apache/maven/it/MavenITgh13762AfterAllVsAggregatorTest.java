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
 * Checks whether a goal declared with {@code aggregator = true} can be migrated to a form without the deprecated
 * flag (<a href="https://github.com/apache/maven/issues/13762">#13762</a>) without changing its behavior.
 * <p>
 * Each test builds the same reactor twice with the concurrent builder: once with the probe goal as a legacy
 * aggregator bound to {@code package}, and once in a migrated form. It then asserts that both builds made the
 * same {@link Observation}. The legacy behavior is the expected value, because it is what plugin users rely on
 * today and what <a href="https://github.com/apache/maven/issues/13763">#13763</a> implemented for the concurrent
 * builder. Two migrated forms are checked:
 * <ul>
 *   <li>The form recommended by {@code src/site/markdown/aggregator-goals.md} (section 3.4): the same goal without
 *       the flag, bound to {@code after:all}. These tests are expected to fail, because {@code after:all} is a
 *       cleanup hook that runs in every module that inherits it, after failures, and for every requested phase.</li>
 *   <li>A Maven 4 goal bound to the same phase that declares {@code @After(phase = "package", type = CHILDREN)}
 *       instead of the flag. These tests are expected to pass once the concurrent builder gives such goals the
 *       same aggregation semantics as {@code aggregator = true}.</li>
 * </ul>
 * Two further groups of tests cover situations outside the {@code package} phase of the default lifecycle: a direct
 * invocation of the goal on the command line, and aggregation in the {@code site} lifecycle, where reports are
 * typically aggregated.
 * <p>
 * The probe writes {@code target/probe.txt} into the module it runs in, recording which of the child modules
 * {@code a} and {@code b} had completed their {@code package} phase at that moment, for example
 * {@code module a packaged, module b not packaged}.
 * <p>
 * The reactor builds run in a forked JVM and each test has a timeout, so that a build that never finishes fails
 * the test instead of blocking the whole suite, and the hanging Maven process can be inspected with {@code jstack}.
 */
@Timeout(value = 5, unit = TimeUnit.MINUTES, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class MavenITgh13762AfterAllVsAggregatorTest extends AbstractMavenIntegrationTestCase {

    private static final String RESOURCES = "gh-13762-after-all-vs-aggregator";

    /** Legacy form: {@code aggregator = true}, bound to {@code package}. */
    private static final String LEGACY = "aggregator-flag";

    /** Migrated form exactly as in the example of section 3.3 of the specification. */
    private static final String MIGRATED_AS_IN_EXAMPLE = "after-all";

    /** Migrated form with {@code <inherited>false</inherited>}, as required by section 3.2 of the specification. */
    private static final String MIGRATED = "after-all-not-inherited";

    /** Migrated form proposed instead: a Maven 4 goal with {@code @After(phase = "package", type = CHILDREN)}. */
    private static final String MIGRATED_WITH_AFTER_CHILDREN = "after-children";

    /** Legacy form in the {@code site} lifecycle: {@code aggregator = true}, bound to {@code site}. */
    private static final String LEGACY_SITE = "aggregator-flag-site";

    /** Proposed form in the {@code site} lifecycle: a Maven 4 goal with {@code @After(phase = "site", ...)}. */
    private static final String MIGRATED_WITH_AFTER_CHILDREN_SITE = "after-children-site";

    /** The probe as a legacy aggregator, for a direct invocation on the command line. */
    private static final String LEGACY_GOAL =
            "org.apache.maven.its.gh13762:probe-maven-plugin:1.0-SNAPSHOT:aggregator-probe";

    /** The probe without the {@code aggregator} flag, which is what remains of a goal migrated to {@code after:all}. */
    private static final String PLAIN_GOAL = "org.apache.maven.its.gh13762:probe-maven-plugin:1.0-SNAPSHOT:probe";

    /** The probe declaring {@code @After(type = CHILDREN)}, for a direct invocation on the command line. */
    private static final String AFTER_CHILDREN_GOAL =
            "org.apache.maven.its.gh13762:probe-v4-maven-plugin:1.0-SNAPSHOT:probe";

    private static final String MIGRATION_CLAIM =
            "Replacing aggregator=true by an after:all binding must not change whether, where and when the goal runs";

    private static final String AFTER_CHILDREN_CLAIM = "Replacing aggregator=true by @After(type = CHILDREN) must not"
            + " change whether, where and when the goal runs";

    /**
     * Fairness check: in a successful build, the migrated form behaves like the legacy aggregator. Expected to pass.
     */
    @Test
    void testMigrationKeepsBehaviorInSuccessfulBuild() throws Exception {
        // GIVEN
        final Path reactor = prepare();

        // WHEN
        final Observation legacy = observe(reactor, LEGACY, "package");
        final Observation migrated = observe(reactor, MIGRATED, "package");

        // THEN
        assertEquals(
                new Observation(true, "module a packaged, module b packaged", false, false),
                legacy,
                "Unexpected legacy behavior");
        assertEquals(legacy, migrated, MIGRATION_CLAIM);
    }

    /**
     * The example in section 3.3 of the specification states that the goal executes exactly once, on the root
     * project. Declared in the parent POM as shown there, it also runs in every child module.
     */
    @Test
    void testMigrationAsInSpecificationExampleRunsOnlyOnRootProject() throws Exception {
        // GIVEN
        final Path reactor = prepare();

        // WHEN
        final Observation legacy = observe(reactor, LEGACY, "package");
        final Observation migrated = observe(reactor, MIGRATED_AS_IN_EXAMPLE, "package");

        // THEN
        assertEquals(legacy, migrated, MIGRATION_CLAIM);
    }

    /**
     * When a child fails, the legacy aggregator does not run. The {@code after:all} binding runs anyway, because
     * {@code after:} steps are executed as cleanup steps, and aggregates incomplete results.
     */
    @Test
    void testMigrationKeepsBehaviorWhenChildFails() throws Exception {
        // GIVEN
        final Path reactor = prepare();

        // WHEN
        final Observation legacy = observe(reactor, LEGACY, "package", "--fail-at-end", "-DfailModule=b");
        final Observation migrated = observe(reactor, MIGRATED, "package", "--fail-at-end", "-DfailModule=b");

        // THEN
        assertEquals(legacy, migrated, MIGRATION_CLAIM);
    }

    /**
     * When the build does not reach the bound phase, the legacy aggregator does not run. The {@code after:all}
     * binding runs for every requested phase of the default lifecycle, here {@code compile}, before any child has
     * been packaged.
     */
    @Test
    void testMigrationKeepsBehaviorWhenBoundPhaseIsNotReached() throws Exception {
        // GIVEN
        final Path reactor = prepare();

        // WHEN
        final Observation legacy = observe(reactor, LEGACY, "compile");
        final Observation migrated = observe(reactor, MIGRATED, "compile");

        // THEN
        assertEquals(legacy, migrated, MIGRATION_CLAIM);
    }

    /**
     * In a successful build, the goal declaring {@code @After(type = CHILDREN)} runs once on the root project after
     * both children, although it is declared in the parent POM without disabling inheritance.
     */
    @Test
    void testMigrationToAfterChildrenKeepsBehaviorInSuccessfulBuild() throws Exception {
        // GIVEN
        final Path reactor = prepare();

        // WHEN
        final Observation legacy = observe(reactor, LEGACY, "package");
        final Observation migrated = observe(reactor, MIGRATED_WITH_AFTER_CHILDREN, "package");

        // THEN
        assertEquals(legacy, migrated, AFTER_CHILDREN_CLAIM);
    }

    /**
     * When a child fails, the goal declaring {@code @After(type = CHILDREN)} does not run, like the legacy
     * aggregator.
     */
    @Test
    void testMigrationToAfterChildrenKeepsBehaviorWhenChildFails() throws Exception {
        // GIVEN
        final Path reactor = prepare();

        // WHEN
        final Observation legacy = observe(reactor, LEGACY, "package", "--fail-at-end", "-DfailModule=b");
        final Observation migrated =
                observe(reactor, MIGRATED_WITH_AFTER_CHILDREN, "package", "--fail-at-end", "-DfailModule=b");

        // THEN
        assertEquals(legacy, migrated, AFTER_CHILDREN_CLAIM);
    }

    /**
     * When the build does not reach the bound phase, the goal declaring {@code @After(type = CHILDREN)} does not run,
     * like the legacy aggregator, because the binding keeps the phase that the aggregation needs.
     */
    @Test
    void testMigrationToAfterChildrenKeepsBehaviorWhenBoundPhaseIsNotReached() throws Exception {
        // GIVEN
        final Path reactor = prepare();

        // WHEN
        final Observation legacy = observe(reactor, LEGACY, "compile");
        final Observation migrated = observe(reactor, MIGRATED_WITH_AFTER_CHILDREN, "compile");

        // THEN
        assertEquals(legacy, migrated, AFTER_CHILDREN_CLAIM);
    }

    /**
     * Invoked directly on the command line (together with {@code validate}, see {@link #observeGoal}), the legacy
     * aggregator runs once, on the root project. A binding to {@code after:all} plays no role in a direct invocation,
     * so the migrated goal runs in every module.
     */
    @Test
    void testDirectInvocationOfAfterAllMigrationRunsOnlyOnRootProject() throws Exception {
        // GIVEN
        final Path reactor = prepare();

        // WHEN
        final Observation legacy = observeGoal(reactor, "cli-legacy", LEGACY_GOAL);
        final Observation migrated = observeGoal(reactor, "cli-plain", PLAIN_GOAL);

        // THEN
        assertEquals(legacy, migrated, MIGRATION_CLAIM);
    }

    /**
     * Invoked directly on the command line (together with {@code validate}, see {@link #observeGoal}), the legacy
     * aggregator runs once, on the root project. The goal declaring
     * {@code @After(type = CHILDREN)} runs in every module, because a direct invocation is only treated as aggregating
     * for the {@code aggregator} flag, and an {@code @After} constraint only applies to lifecycle bindings. Expected to
     * fail even with the proposed change of the concurrent builder.
     */
    @Test
    void testDirectInvocationOfAfterChildrenMigrationRunsOnlyOnRootProject() throws Exception {
        // GIVEN
        final Path reactor = prepare();

        // WHEN
        final Observation legacy = observeGoal(reactor, "cli-legacy", LEGACY_GOAL);
        final Observation migrated = observeGoal(reactor, "cli-after-children", AFTER_CHILDREN_GOAL);

        // THEN
        assertEquals(legacy, migrated, AFTER_CHILDREN_CLAIM);
    }

    /**
     * An aggregator bound to {@code site} runs once, on the root project, after the children. The {@code site}
     * lifecycle has no {@code all} phase, so a goal migrated to {@code after:all} never runs in {@code mvn site}.
     * The children are not packaged in this build; what matters is whether and where the goal runs.
     */
    @Test
    void testAfterAllMigrationRunsInSiteLifecycle() throws Exception {
        // GIVEN
        final Path reactor = prepare();

        // WHEN
        final Observation legacy = observe(reactor, LEGACY_SITE, "site");
        final Observation migrated = observe(reactor, MIGRATED, "site");

        // THEN
        assertEquals(legacy, migrated, MIGRATION_CLAIM);
    }

    /**
     * An aggregator bound to {@code site} runs once, on the root project, after the children. The goal declaring
     * {@code @After(phase = "site", type = CHILDREN)}, bound to {@code site}, behaves the same once the concurrent
     * builder gives such goals the aggregation semantics.
     */
    @Test
    void testAfterChildrenMigrationKeepsBehaviorInSiteLifecycle() throws Exception {
        // GIVEN
        final Path reactor = prepare();

        // WHEN
        final Observation legacy = observe(reactor, LEGACY_SITE, "site");
        final Observation migrated = observe(reactor, MIGRATED_WITH_AFTER_CHILDREN_SITE, "site");

        // THEN
        assertEquals(legacy, migrated, AFTER_CHILDREN_CLAIM);
    }

    /**
     * What a single build of the reactor showed about the probe goal.
     *
     * @param buildSucceeded whether the Maven build succeeded
     * @param seenByRootProbe what the probe recorded on the root project, or {@code null} if it did not run there
     * @param probeRanInModuleA whether the probe also ran in module {@code a}
     * @param probeRanInModuleB whether the probe also ran in module {@code b}
     */
    record Observation(
            boolean buildSucceeded, String seenByRootProbe, boolean probeRanInModuleA, boolean probeRanInModuleB) {

        @Override
        public String toString() {
            return (buildSucceeded ? "build succeeded" : "build failed")
                    + "; probe in root: " + (seenByRootProbe == null ? "did not run" : "ran, saw " + seenByRootProbe)
                    + "; probe in module a: " + (probeRanInModuleA ? "ran" : "did not run")
                    + "; probe in module b: " + (probeRanInModuleB ? "ran" : "did not run");
        }
    }

    private Path prepare() throws Exception {
        final Path testDir = extractResources(RESOURCES);
        install(testDir.resolve("plugin"));
        install(testDir.resolve("plugin-v4"));
        return testDir.resolve("reactor");
    }

    private void install(final Path project) throws Exception {
        final Verifier verifier = newVerifier(project);
        verifier.addCliArgument("install");
        verifier.execute();
        verifier.verifyErrorFreeLog();
    }

    /**
     * Builds the reactor up to the given phase, with the given profile active.
     */
    private Observation observe(final Path reactor, final String profile, final String phase, final String... args)
            throws Exception {
        final List<String> arguments = new ArrayList<>();
        arguments.add("-P" + profile);
        arguments.addAll(List.of(args));
        arguments.add(phase);
        return observeBuild(reactor, profile + "-" + phase, arguments);
    }

    /**
     * Invokes the given goal directly on the command line, without a profile. The goal is preceded by
     * {@code validate}, which adds a non-aggregating task segment with all projects of the reactor.
     */
    private Observation observeGoal(final Path reactor, final String label, final String goal) throws Exception {
        return observeBuild(reactor, label, List.of("validate", goal));
    }

    private Observation observeBuild(final Path reactor, final String label, final List<String> arguments)
            throws Exception {
        clean(reactor);

        final Verifier verifier = newVerifier(reactor);
        verifier.setForkJvm(true);
        verifier.setLogFileName("log-" + label + ".txt");
        verifier.addCliArguments("-b", "concurrent", "-T2");
        verifier.addCliArguments(arguments.toArray(String[]::new));
        boolean buildSucceeded;
        try {
            verifier.execute();
            verifier.verifyErrorFreeLog();
            buildSucceeded = true;
        } catch (VerificationException e) {
            buildSucceeded = false;
        }

        final Path rootProbe = reactor.resolve("target/probe.txt");
        final String seenByRootProbe = Files.exists(rootProbe) ? Files.readString(rootProbe).trim() : null;
        return new Observation(
                buildSucceeded,
                seenByRootProbe,
                Files.exists(reactor.resolve("a/target/probe.txt")),
                Files.exists(reactor.resolve("b/target/probe.txt")));
    }

    private void clean(final Path reactor) throws Exception {
        final Verifier verifier = newVerifier(reactor);
        for (String path : new String[] {"target", "a/target", "b/target"}) {
            verifier.deleteDirectory(path);
        }
    }
}
