package de.mannodermaus.gradle.plugins.junit5

import de.mannodermaus.gradle.plugins.junit5.util.ClasspathSplitter
import de.mannodermaus.gradle.plugins.junit5.util.FileLanguage
import de.mannodermaus.gradle.plugins.junit5.util.TestEnvironment
import org.apache.commons.lang.StringUtils
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Paths

/*
 * Functional tests of the plugin, verifying the integration
 * with the Android Gradle Plugin 3.
 *
 * Driven by Gradle TestKit, the execution of these unit tests
 * is embedded in "virtual projects", as if the plugin
 * was applied to an actual client project. To do this, there are
 * several factory-like methods that write pieces of the build script
 * into a temporary file and allow for a clean "given-when-then" approach
 * to setting up those temporary projects.
 *
 * This class tests the runtime behaviour of
 * the test tasks generated by the plugin,
 * rather than the structural integrity, which is enforced by PluginSpec.
 */

class FunctionalSpec extends Specification {

  @Rule
  final TemporaryFolder testProjectDir = new TemporaryFolder()
  final TestEnvironment environment = new TestEnvironment()

  private File buildFile
  private List<File> pluginClasspath
  private List<File> testCompileClasspath

  def setup() {
    pluginClasspath = loadClassPathManifestResource("plugin-classpath.txt")
    testCompileClasspath = loadClassPathManifestResource("functional-test-compile-classpath.txt")
    def localProperties = testProjectDir.newFile("local.properties")
    localProperties.withWriter {
      it.write("sdk.dir=${environment.androidSdkFolder.absolutePath}")
    }
    buildFile = testProjectDir.newFile("build.gradle")
    buildFile << """
      buildscript {
        dependencies {
          classpath files(${ClasspathSplitter.splitClasspath(pluginClasspath)})
        }
      }
    """
  }

  /*
   * ===============================================================================================
   * Tests
   * ===============================================================================================
   */

  def "Executes Java tests in default source set"() {
    given:
    androidPlugin()
    junit5Plugin()
    javaFile()
    javaTest()

    when:
    BuildResult result = runGradle()
        .withArguments("build")
        .build()

    then:
    result.task(":build").outcome == TaskOutcome.SUCCESS
    result.task(":junitPlatformTestDebug").outcome == TaskOutcome.SUCCESS
    result.task(":junitPlatformTestRelease").outcome == TaskOutcome.SUCCESS

    // 1 per build type (Debug & Release)
    StringUtils.countMatches(result.output, "1 tests successful") == 2
  }

  def "Executes Kotlin tests in default source set"() {
    given:
    androidPlugin()
    kotlinPlugin()
    junit5Plugin()
    javaFile()
    kotlinTest()

    when:
    BuildResult result = runGradle()
        .withArguments("build")
        .build()

    then:
    result.task(":build").outcome == TaskOutcome.SUCCESS
    result.task(":junitPlatformTestDebug").outcome == TaskOutcome.SUCCESS
    result.task(":junitPlatformTestRelease").outcome == TaskOutcome.SUCCESS

    // 1 per build type (Debug & Release)
    StringUtils.countMatches(result.output, "1 tests successful") == 2
  }

  def "Executes Java tests in build-type-specific source set"() {
    given:
    androidPlugin()
    junit5Plugin()
    javaFile()
    javaTest()
    javaTest(null, "debug")

    when:
    BuildResult result = runGradle()
        .withArguments("junitPlatformTestDebug")
        .build()

    then:
    result.task(":junitPlatformTestDebug").outcome == TaskOutcome.SUCCESS
    result.output.contains("2 tests successful")
    result.output.contains("JavaDebugAdderTest")
    result.output.contains("JavaAdderTest")

    when:
    result = runGradle()
        .withArguments("junitPlatformTestRelease")
        .build()

    then:
    result.task(":junitPlatformTestRelease").outcome == TaskOutcome.SUCCESS
    result.output.contains("1 tests successful")
    result.output.contains("JavaAdderTest")
  }

  def "Executes Kotlin tests in build-type-specific source set"() {
    given:
    androidPlugin()
    kotlinPlugin()
    junit5Plugin()
    javaFile()
    kotlinTest()
    kotlinTest(null, "debug")

    when:
    BuildResult result = runGradle()
        .withArguments("junitPlatformTestDebug")
        .build()

    then:
    result.task(":junitPlatformTestDebug").outcome == TaskOutcome.SUCCESS
    result.output.contains("2 tests successful")
    result.output.contains("KotlinDebugAdderTest")
    result.output.contains("KotlinAdderTest")

    when:
    result = runGradle()
        .withArguments("junitPlatformTestRelease")
        .build()

    then:
    result.task(":junitPlatformTestRelease").outcome == TaskOutcome.SUCCESS
    result.output.contains("1 tests successful")
    result.output.contains("KotlinAdderTest")
  }

  def "Executes Java tests in flavor-specific source set"() {
    given:
    androidPlugin(flavorNames: ["free"])
    junit5Plugin()
    javaFile()
    javaTest()
    javaTest("free", null)

    when:
    BuildResult result = runGradle()
        .withArguments("build")
        .build()

    then:
    result.task(":build").outcome == TaskOutcome.SUCCESS
    result.task(":junitPlatformTestFreeDebug").outcome == TaskOutcome.SUCCESS
    result.task(":junitPlatformTestFreeRelease").outcome == TaskOutcome.SUCCESS

    // 1 per build type (Debug & Release)
    StringUtils.countMatches(result.output, "2 tests successful") == 2
  }

  def "Executes Kotlin tests in flavor-specific source set"() {
    given:
    androidPlugin(flavorNames: ["free"])
    junit5Plugin()
    kotlinPlugin()
    javaFile()
    kotlinTest()
    kotlinTest("free", null)

    when:
    BuildResult result = runGradle()
        .withArguments("build")
        .build()

    then:
    result.task(":build").outcome == TaskOutcome.SUCCESS
    result.task(":junitPlatformTestFreeDebug").outcome == TaskOutcome.SUCCESS
    result.task(":junitPlatformTestFreeRelease").outcome == TaskOutcome.SUCCESS

    // 1 per build type (Debug & Release)
    StringUtils.countMatches(result.output, "2 tests successful") == 2
  }

  def "Executes Java tests in build-type-and-flavor-specific source set"() {
    given:
    androidPlugin(flavorNames: ["free"])
    junit5Plugin()
    javaFile()
    javaTest()
    javaTest(null, "debug")
    javaTest("free", "debug")
    javaTest(null, "release")
    GradleRunner runner = runGradle()

    when:
    BuildResult result = runner
        .withArguments("junitPlatformTestFreeDebug")
        .build()

    then:
    result.task(":junitPlatformTestFreeDebug").outcome == TaskOutcome.SUCCESS
    result.output.contains("3 tests successful")
    result.output.contains("JavaFreeDebugAdderTest")
    result.output.contains("JavaDebugAdderTest")
    result.output.contains("JavaAdderTest")

    when:
    result = runner
        .withArguments("junitPlatformTestFreeRelease")
        .build()

    then:
    result.task(":junitPlatformTestFreeRelease").outcome == TaskOutcome.SUCCESS
    result.output.contains("2 tests successful")
    result.output.contains("JavaReleaseAdderTest")
    result.output.contains("JavaAdderTest")
  }

  def "Executes Kotlin tests in build-type-and-flavor-specific source set"() {
    given:
    androidPlugin(flavorNames: ["free"])
    kotlinPlugin()
    junit5Plugin()
    javaFile()
    kotlinTest()
    kotlinTest("free", "debug")
    kotlinTest(null, "debug")
    kotlinTest(null, "release")
    GradleRunner runner = runGradle()

    when:
    BuildResult result = runner
        .withArguments("junitPlatformTestFreeDebug")
        .build()

    then:
    result.task(":junitPlatformTestFreeDebug").outcome == TaskOutcome.SUCCESS
    result.output.contains("3 tests successful")
    result.output.contains("KotlinFreeDebugAdderTest")
    result.output.contains("KotlinDebugAdderTest")
    result.output.contains("KotlinAdderTest")

    when:
    result = runner
        .withArguments("junitPlatformTestFreeRelease")
        .build()

    then:
    result.task(":junitPlatformTestFreeRelease").outcome == TaskOutcome.SUCCESS
    result.output.contains("2 tests successful")
    result.output.contains("KotlinReleaseAdderTest")
    result.output.contains("KotlinAdderTest")
  }

  def "Returns default values successfully"() {
    given:
    androidPlugin()
    junit5Plugin("""
      unitTests {
        returnDefaultValues = true
      }
    """)
    test(language: FileLanguage.Java,
        content: """
        package de.mannodermaus.app;

        import static org.junit.jupiter.api.Assertions.assertNull;

        import org.junit.jupiter.api.Test;
        import android.content.Intent;

        class AndroidTest {
          @Test
          void test() {
            Intent intent = new Intent();
            assertNull(intent.getAction());
          }
        }
      """)
    GradleRunner runner = runGradle()

    when:
    BuildResult result = runner
        .withArguments("junitPlatformTestDebug")
        .build()

    then:
    result.task(":junitPlatformTestDebug").outcome == TaskOutcome.SUCCESS
    result.output.contains("1 tests successful")
    result.output.contains("AndroidTest")
  }

  def "Includes Android resources successfully"() {
    given:
    androidPlugin()
    junit5Plugin("""
        unitTests {
          includeAndroidResources = true
        }
      """)
    test(language: FileLanguage.Java,
        content: """
          package de.mannodermaus.app;

          import static org.junit.jupiter.api.Assertions.assertNotNull;

          import org.junit.jupiter.api.Test;
          import java.io.InputStream;

          class AndroidTest {
            @Test
            void test() {
              InputStream is = getClass().getResourceAsStream("/com/android/tools/test_config.properties");
              assertNotNull(is);
            }
          }
        """)
    GradleRunner runner = runGradle()

    when:
    BuildResult result = runner
        .withArguments("junitPlatformTestDebug")
        .build()

    then:
    result.task(":junitPlatformTestDebug").outcome == TaskOutcome.SUCCESS
    result.output.contains("1 tests successful")
    result.output.contains("AndroidTest")
  }

  /*
   * ===============================================================================================
   * Helpers, Factories & Utilities
   * ===============================================================================================
   */

  protected final def androidPlugin(Map properties) {
    List<String> flavorNames = properties?.flavorNames

    // Require AndroidManifest.xml
    def manifestPath = Paths.get(testProjectDir.root.toString(),
        "src", "main", "AndroidManifest.xml")
    Files.createDirectories(manifestPath.parent)
    manifestPath.withWriter {
      it.write("""<manifest package="de.mannodermaus.app"/>""")
    }

    def productFlavors = ""
    if (flavorNames) {
      productFlavors = """
        flavorDimensions "tier"
        productFlavors {
          ${flavorNames.collect { """$it { dimension "tier" }""" }.join("\n")}
        }
      """
    }

    buildFile << """
      apply plugin: "com.android.application"

      android {
        compileSdkVersion "${environment.compileSdkVersion}"
        buildToolsVersion "${environment.buildToolsVersion}"

        defaultConfig {
          applicationId "de.mannodermaus.app"
          minSdkVersion ${environment.minSdkVersion}
          targetSdkVersion ${environment.targetSdkVersion}
          versionCode 1
          versionName "1.0"
        }

        $productFlavors

        lintOptions {
          abortOnError false
        }
      }

      // Disabled because the Lint library dependency
      // can't be resolved within the offline-only virtual project execution
      lint.enabled false

      dependencies {
        testImplementation files(${
      ClasspathSplitter.splitClasspath(testCompileClasspath)
    })
      }
    """
  }

  protected final def kotlinPlugin() {
    buildFile << """
      apply plugin: "kotlin-android"

      android {
        sourceSets {
          main.java.srcDir "src/main/kotlin"
          test.java.srcDir "src/test/kotlin"
        }
      }
    """
  }

  protected final def junit5Plugin(String extraConfig = "") {
    buildFile << """
      apply plugin: "de.mannodermaus.android-junit5"

      android.testOptions {
        junitPlatform {
          details "flat"
          $extraConfig
        }
      }

      dependencies {
        // Use local dependencies so that defaultDependencies are not used
        junitPlatform files(${
      ClasspathSplitter.splitClasspath(testCompileClasspath)
    })
      }
    """
  }

  protected final def javaFile() {
    def path = Paths.get(testProjectDir.root.toString(), "src", "main", "java", "de",
        "mannodermaus", "app", "Adder.java")
    Files.createDirectories(path.parent)
    path.withWriter {
      it.write("""
        package de.mannodermaus.app;

        public class Adder {
          public int add(int a, int b) {
            return a + b;
          }
        }
      """)
    }
  }

  // Generic factory to write out a test file to the temp project.
  // Don't use this directly: Instead, use the abstractions "javaTest()" and "kotlinTest()".
  private final def test(Map properties) {
    FileLanguage language = properties.language
    String flavorName = properties.flavorName
    String buildType = properties.buildType
    String content = properties.content

    if (!flavorName) {
      flavorName = ""
    }
    if (!buildType) {
      buildType = ""
    }

    def variant = "${flavorName.capitalize()}${buildType.capitalize()}"
    def testName = "${language.name()}${variant}AdderTest"
    def sourceSet = "test${variant}"
    def fileName = language.appendExtension(testName)

    def filePath = Paths.get(testProjectDir.root.toString(),
        // e.g. "src/test/java" or "src/testFreeDebug/kotlin"
        "src", sourceSet, language.sourceDirectoryName,
        // Package name of test file
        "de", "mannodermaus", "app", fileName)
    Files.createDirectories(filePath.parent)

    filePath.withWriter { it.write(content.replace("__NAME__", testName)) }
  }

  protected final def javaTest(String flavorName = null, String buildType = null) {
    this.test(language: FileLanguage.Java,
        flavorName: flavorName,
        buildType: buildType,
        content: """
          package de.mannodermaus.app;

          import static org.junit.jupiter.api.Assertions.assertEquals;

          import org.junit.jupiter.api.Test;

          class __NAME__ {
            @Test
            void test() {
              Adder adder = new Adder();
              assertEquals(4, adder.add(2, 2), "This should succeed!");
            }
          }
        """)
  }

  protected final def kotlinTest(String flavorName = null, String buildType = null) {
    this.test(language: FileLanguage.Kotlin,
        flavorName: flavorName,
        buildType: buildType,
        content: """
          package de.mannodermaus.app

          import org.junit.jupiter.api.Assertions.assertEquals
          import org.junit.jupiter.api.Test

          class __NAME__ {
            @Test
            fun myTest() {
              val adder = Adder()
              assertEquals(4, adder.add(2, 2), "This should succeed!")
            }
          }
        """)
  }

  private GradleRunner runGradle() {
    return GradleRunner.create()
        .withProjectDir(testProjectDir.root)
        .withPluginClasspath(pluginClasspath)
  }

  private List<File> loadClassPathManifestResource(String name) {
    InputStream classpathResource = getClass().classLoader.getResourceAsStream(name)
    if (classpathResource == null) {
      throw new IllegalStateException("Did not find required resource with name ${name}")
    }
    return classpathResource.readLines().collect { new File(it) }
  }
}
