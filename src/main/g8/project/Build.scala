import sbt._
import sbt.Keys._
import trafficland.sbt.plugins.TrafficLandStandardPluginSet
import trafficland.opensource.sbt.plugins._

object Build extends sbt.Build {
  import Dependencies._

  val appName = "$name$"
  val appVersion = "0.1.0-SNAPSHOT".toReleaseFormat

  lazy val UnitTest = config("unit") extend Test
  lazy val runWorker = TaskKey[Unit]("runWorker")
  lazy val runSupervisor = TaskKey[Unit]("runSupervisor")
  lazy val myProject = Project(appName, file("."))
    .configs(UnitTest)
    .settings(TrafficLandStandardPluginSet.plugs :_*)
    .settings(inConfig(UnitTest)(Defaults.testTasks) :_*)
    .settings(commands ++= Seq(dist))
    .settings(
      version       := appVersion,
      resolvers     ++= Dependencies.resolutionRepos,
      libraryDependencies ++= compileDeps ++ testDeps,
      testListeners += SbtTapReporting(),
      testOptions in UnitTest := Seq(
        Tests.Filter { _.contains(".unit.") }
      ),
      parallelExecution in Test := false,
      fullRunTask(runWorker, Runtime, "$name$.Runner"),
      fork in runWorker := true,
      javaOptions in runWorker += "-Dconfig.file=conf/worker.conf",
      fullRunTask(runSupervisor, Runtime, "$name$.Runner"),
      fork in runSupervisor := true,
      javaOptions in runSupervisor += "-Dconfig.file=conf/supervisor.conf"
    )

  def dist = Command.single("dist") { (state, environment) =>
    val dist = file("./dist")
    val packageName = "%s-%s".format(appName, appVersion)
    val packageDir = dist / packageName / appName
    val zip = dist / (packageName + ".zip")
    IO.delete(dist)
    IO.createDirectories(Seq(packageDir / "conf", packageDir / "lib", packageDir / "logs"))

    IO.copyFile(file("./conf/application.conf"), packageDir / "conf" / "application.conf")
    IO.copyFile(file("./conf/supervisor-%s.conf".format(environment)), packageDir / "conf" / "supervisor.conf")
    IO.copyFile(file("./conf/worker-%s.conf".format(environment)), packageDir / "conf" / "worker.conf")

    Seq("supervisor", "worker") foreach { cnf =>
      IO.write(packageDir / "start-%s".format(cnf),
        """
          |#!/usr/bin/env sh
          |scriptdir=`dirname \$0`
          |classpath="\$scriptdir/lib/*"
          |exec /opt/jdk1.7.0/bin/java \$* -cp "\$classpath" -Dconfig.file="conf/%s.conf" %s.Runner `dirname \$0`
        """.stripMargin.format(
          cnf, appName
        )
      )
    }

    val extracted = Project.extract(state)
    extracted.runTask((fullClasspath in Compile), state) match {
      case (_, cp) =>
        cp.seq.map(_.data).filter(_.getName.endsWith(".jar")).sortBy(_.getName) foreach { f =>
          IO.copyFile(f, packageDir / "lib" / f.getName)
        }


        val jarName = extracted.runTask(packageBin in Compile, state) match {
          case (_, pkg) =>
            IO.copyFile(pkg, packageDir  / "lib" / pkg.getName)
            pkg.getName
        }
    }


    val libs = (packageDir / "lib").listFiles().map(f => f -> "./%s/lib/%s".format(appName, f.getName))
    val start = packageDir.listFiles().map(f => f -> "./%s/%s".format(appName, f.getName))
    val conf = (packageDir / "conf").listFiles().map(f => f -> "./%s/conf/%s".format(appName, f.getName))
    val logs = Seq((packageDir / "logs") -> "./%s/logs/".format(appName))

    IO.zip(libs ++ start ++ conf ++ logs, zip)
    IO.delete(dist / packageName)

    state
  }
}

object Dependencies {
  val resolutionRepos = Seq(
  )

  object Group {
    val akka        = "com.typesafe.akka"
  }

  object V {
    val mockito     = "1.9.0"
    val slf4j       = "1.6.4"
    val logback     = "1.0.0"
    val akka        = "2.1.0"
    val scalatest   = "2.0.M5b"
    val junit       = "4.10"
  }

  val compileDeps = Seq(
    Group.akka                  %%  "akka-agent"               % V.akka,
    Group.akka                  %%  "akka-remote"              % V.akka,
    "org.slf4j"                 %   "slf4j-api"                % V.slf4j,
    "ch.qos.logback"            %   "logback-classic"          % V.logback,
    "com.google.inject"         %   "guice"                    % "3.0",
  )

  val testDeps = Seq(
    "junit"                     %  "junit"                    % V.junit,
    "org.mockito"               %  "mockito-core"             % V.mockito,
    Group.akka                  %% "akka-testkit"             % V.akka,
    "org.scalatest"             %% "scalatest"                % V.scalatest
  )
}