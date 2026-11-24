package com.banca.seguridad.runners;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Runner principal para ejecutar los escenarios de pruebas de seguridad con Cucumber y Serenity
 * sobre JUnit Platform (JUnit 5). Coordina la ejecución de todos los features de seguridad,
 * configura los informes y los paquetes donde se encuentran los step definitions.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features/seguridad")
@ConfigurationParameter(
    key = GLUE_PROPERTY_NAME,
    value = "com.banca.seguridad.steps,com.banca.seguridad.tasks,com.banca.seguridad.questions,"
        + "com.banca.seguridad.utils,com.banca.seguridad.config,com.banca.seguridad.exceptions"
)
@ConfigurationParameter(
    key = PLUGIN_PROPERTY_NAME,
    value = "io.cucumber.core.plugin.SerenityReporterParallel,"
        + "pretty,"
        + "json:target/cucumber-reports/cucumber.json,"
        + "html:target/cucumber-reports/cucumber-html-reports.html,"
        + "junit:target/cucumber-reports/cucumber-junit-results.xml"
)
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@seguridad")
public class RunCucumberTest {
}
