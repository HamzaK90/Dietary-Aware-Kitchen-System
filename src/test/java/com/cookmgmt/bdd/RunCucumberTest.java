package com.cookmgmt.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

/**
 * Runs every Gherkin feature under {@code src/test/resources/features}.
 *
 * <p>Uses the JUnit 5 platform engine rather than the older JUnit 4 Cucumber runner, so the runner
 * and the assertions in the step definitions belong to the same JUnit generation.
 *
 * <p>The class name ends in {@code Test} so it matches Surefire's default include pattern, and the
 * features are selected as a classpath resource rather than by a path relative to the working
 * directory, so the suite runs wherever it is launched from.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.cookmgmt.bdd")
public class RunCucumberTest {
}
