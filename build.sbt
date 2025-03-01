import sbt.Keys._
import sbt._
import sbt.Tests._


val defaultSettings = Defaults.coreDefaultSettings ++ xerial.sbt.Sonatype.sonatypeSettings ++ Seq(
  organization := "com.github.spinalhdl",
  version      := SpinalVersion.all,
  crossScalaVersions := SpinalVersion.compilers,
  scalaVersion := SpinalVersion.compilers(0),
  scalacOptions ++= Seq("-unchecked","-target:jvm-1.8"/*, "-feature" ,"-deprecation"*/),
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
  fork := true,

  //Enable parallel tests
  Test / testForkedParallel := true,
  Test / testGrouping := (Test / testGrouping).value.flatMap { group =>
//    for(i <- 0 until 4) yield {
//      Group("g" + i,  group.tests.zipWithIndex.filter(_._2 % 4 == i).map(_._1), SubProcess(ForkOptions()))
//    }
    Seq(Group("g",  group.tests, SubProcess(ForkOptions())))
  },
//  concurrentRestrictions := Seq(Tags.limit(Tags.ForkedTestGroup, 4)),

  libraryDependencies += "org.scala-lang" % "scala-library" % scalaVersion.value,

  dependencyOverrides += "net.java.dev.jna" % "jna" % "5.5.0",
  dependencyOverrides += "net.java.dev.jna" % "jna-platform" % "5.5.0",
  dependencyOverrides += "org.slf4j" % "slf4j-api" % "1.7.25",
  dependencyOverrides += "org.scala-lang.modules" %% "scala-xml" % "1.2.0",

  //set SBT_OPTS="-Xmx2G"
  //sbt +clean +reload +publishSigned
  //https://oss.sonatype.org
  publishMavenStyle := true,
  Test / publishArtifact := false,
  pomIncludeRepository := (_ => false),
  pomExtra := {
    <url>github.com/SpinalHDL/SpinalHDL</url>
      <licenses>
        <license>
          <name>LGPL3</name>
          <url>https://www.gnu.org/licenses/lgpl.html</url>
        </license>
      </licenses>
      <scm>
        <connection>scm:git:github.com/SpinalHDL/SpinalHDL</connection>
        <developerConnection>scm:git:git@github.com:SpinalHDL/SpinalHDL</developerConnection>
        <url>github.com/SpinalHDL/SpinalHDL</url>
      </scm>
      <developers>
        <developer>
          <id>Dolu1990</id>
          <name>Dolu1990</name>
          <url>none</url>
        </developer>
      </developers>
  },

  publishTo := {
    val v = version.value
    val nexus = "https://oss.sonatype.org/"
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
  }
 )

lazy val all = (project in file("."))
  .enablePlugins(ScalaUnidocPlugin)
  .settings(
    defaultSettings,
    name := "SpinalHDL-all",
    version := SpinalVersion.all,
    publishArtifact := false,
    publishLocal := {},
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(lib, core)
  )
  .aggregate(sim, idslpayload, idslplugin, core, lib, debugger, tester)


import sys.process._
def gitHash(dir: File) = (try {
  s"git -C ${dir.toString} rev-parse HEAD".!!
} catch{
  case e : java.io.IOException => "???"
}).linesIterator.next()



lazy val idslpayload = (project in file("idslpayload"))
  .settings(
    defaultSettings,
    name := "SpinalHDL-idsl-payload",
    version := SpinalVersion.sim,
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
  )

lazy val idslplugin = (project in file("idslplugin"))
  .dependsOn(idslpayload)
  .settings(
    defaultSettings,
    name := "SpinalHDL-idsl-plugin",
    exportJars := true,
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value
    )
  )

lazy val sim = (project in file("sim"))
  .settings(
    defaultSettings,
    name := "SpinalHDL-sim",
    libraryDependencies += "commons-io" % "commons-io" % "2.4",
    libraryDependencies += "net.openhft" % "affinity" % "3.21ea1.1",
    libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.25",
    libraryDependencies += "com.github.oshi" % "oshi-core" % "5.2.0",
    version := SpinalVersion.sim
  )

val defaultSettingsWithPlugin = defaultSettings ++ Seq(
  scalacOptions += (idslplugin / Compile / packageBin / artifactPath).map { file =>
    s"-Xplugin:${file.getAbsolutePath}"
  }.value
)

lazy val core = (project in file("core"))
  .dependsOn(idslplugin)
  .settings(
    defaultSettingsWithPlugin,
    name := "SpinalHDL-core",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    libraryDependencies += "com.github.scopt" %% "scopt" % "3.7.1",
    libraryDependencies += "com.lihaoyi" %% "sourcecode" % "0.2.7",

    resolvers += Resolver.sonatypeRepo("public"),
    version := SpinalVersion.core,
    Compile / sourceGenerators += Def.task {
      val dir = (Compile / sourceManaged).value
      dir.mkdirs()
      val file = dir / "Info.scala"
      IO.write(file, """package spinal.core
                       |object Info {
                       |  val version = "%s"
                       |  val name = "%s"
                       |  val gitHash = "%s"
                       |}
                       |""".stripMargin.format(SpinalVersion.core, name, gitHash(dir)))
      Seq(file)
    }.taskValue
  )
  .dependsOn(sim)

lazy val lib = (project in file("lib"))
  .settings(
    defaultSettingsWithPlugin,
    name := "SpinalHDL-lib",
    libraryDependencies += "commons-io" % "commons-io" % "2.4",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.5",
    version := SpinalVersion.lib
  )
  .dependsOn (sim, core)


lazy val debugger = (project in file("debugger"))
  .settings(
    defaultSettingsWithPlugin,
    name := "SpinalHDL Debugger",
    version := SpinalVersion.debugger,
    resolvers += "sparetimelabs" at "https://www.sparetimelabs.com/maven2/",
    libraryDependencies += "com.github.purejavacomm" % "purejavacomm" % "1.0.2.RELEASE",
    libraryDependencies += "net.liftweb" %% "lift-json" % "3.4.3",
    publishArtifact := false,
    publishLocal := {}
  )
.dependsOn(sim, core, lib/*, ip*/)

lazy val demo = (project in file("demo"))
  .settings(
    defaultSettingsWithPlugin,
    name := "SpinalHDL-demo",
    version := SpinalVersion.demo,
    publishArtifact := false,
    publishLocal := {}
  )
  .dependsOn(sim, core, lib, debugger)


lazy val tester = (project in file("tester"))
  .settings(
    defaultSettingsWithPlugin,
    name := "SpinalHDL-tester",
    version := SpinalVersion.tester,
    Test / baseDirectory := file("./"),

    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.5",
    publishArtifact := false,
    publishLocal := {}
  )
  .dependsOn(sim, core, lib, debugger,demo)

// Assembly

assembly / assemblyJarName := "spinalhdl.jar"

assembly / test := {}

Test / testOptions += Tests.Argument("-l", "spinal.tester.formal")
addCommandAlias("testFormal", "testOnly * -- -n spinal.tester.formal")
addCommandAlias("testWithoutFormal", "testOnly * -- -l spinal.tester.formal")

assembly / assemblyOutputPath := file("./release/spinalhdl.jar")

//To publish the scala doc :
//rm -rf ghpages
//sbt clean compile unidoc
//git clone https://github.com/SpinalHDL/SpinalHDL.git -b gh-pages --depth=1 ghpages
//rm -rf ghpages/*
//cp -r target/scala-2.11/unidoc/* ghpages
//cd ghpages
//git add *
//git commit -m "publish doc"
//git push
//cd ..
//rm -rf ghpages
