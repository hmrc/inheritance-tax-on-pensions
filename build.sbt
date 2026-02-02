import uk.gov.hmrc.DefaultBuildSettings

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "3.5.2"
ThisBuild / scalacOptions += "-Wconf:msg=Flag.*repeatedly:s"

inThisBuild(
  List(
    scalaVersion := "3.5.2",
    majorVersion := 0,
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision
  )
)

lazy val microservice = Project("inheritance-tax-on-pensions", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    scalafmtOnCompile := true,
    scalafixOnCompile := true,
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress warnings in generated routes files
    scalacOptions += "-Wconf:src=routes/.*:s",
    PlayKeys.playDefaultPort := 10710
  )
  .settings(inConfig(Test)(testSettings) *)
  .settings(CodeCoverageSettings.settings: _*)

lazy val testSettings: Seq[Def.Setting[?]] = Seq(
  fork := true,
  Test / scalafmtOnCompile := true,
  Test / scalafixOnCompile := true,
  unmanagedSourceDirectories += baseDirectory.value / "test-utils"
)

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.it)
  .settings(
    libraryDependencies ++= AppDependencies.test,
    Test / fork := true,
    Test / scalafmtOnCompile := true,
    Test / unmanagedResourceDirectories += baseDirectory.value / "it" / "test" / "resources"
  )

