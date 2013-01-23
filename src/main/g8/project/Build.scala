import sbt._
import sbt.Keys._


object Build extends sbt.Build {
  import Dependencies._

  val projectName = "$name$"
  val buildVersion = "0.1.0-SNAPSHOT"

  lazy val UnitTest = config("unit") extend Test
  lazy val runWorker = TaskKey[Unit]("runWorker")
  lazy val runSupervisor = TaskKey[Unit]("runSupervisor")
  lazy val myProject = Project(projectName, file("."))
    .configs(UnitTest)
    .settings(inConfig(UnitTest)(Defaults.testTasks) :_*)
    .settings(commands ++= Seq(dist))
    .settings(
      organization  := "com.trafficland",
      version       := buildVersion,
      scalaVersion  := "2.10.0",
      scalacOptions := Seq("-deprecation", "-encoding", "utf8"),
      resolvers     ++= Dependencies.resolutionRepos,
      libraryDependencies ++= Seq(
        CompileDeps.slf4j,
        CompileDeps.logback,
        CompileDeps.akkaagent,
        CompileDeps.akkaremote,
        TestDeps.mockito,
        TestDeps.junit,
        TestDeps.scalatest,
        TestDeps.akkatest
      ),
      testListeners += SbtTapReporting(),
      testOptions in UnitTest := Seq(
        Tests.Filter { _.contains(".unit.") }
      ),
      parallelExecution in Test := false,
      credentials += Credentials("Artifactory Realm", "build01.tl.com", "jenkins", "tlbuild"),
      publishTo <<= (version) {
        version: String =>
          val repo = "http://build01.tl.com:8081/artifactory/"
          if (version.trim endsWith "SNAPSHOT") Some("TrafficLand Snapshots" at (repo + "com.trafficland.snapshots"))
          else if (version.trim endsWith "RC") Some("TrafficLand Release Candidates" at (repo + "com.trafficland.releasecandidates"))
          else Some("TrafficLand Releases" at (repo + "com.trafficland.final"))
      },
      fullRunTask(runWorker, Runtime, "vqm.Runner"),
      fork in runWorker := true,
      javaOptions in runWorker += "-Dconfig.file=conf/worker.conf",
      fullRunTask(runSupervisor, Runtime, "$name$.Runner"),
      fork in runSupervisor := true,
      javaOptions in runSupervisor += "-Dconfig.file=conf/supervisor.conf"
    )

  def dist = Command.single("dist") { (state, environment) =>
    val dist = file("./dist")
    val packageName = "%s-%s".format(projectName, buildVersion)
    val packageDir = dist / packageName / projectName
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
          cnf, projectName
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


    val libs = (packageDir / "lib").listFiles().map(f => f -> "./%s/lib/%s".format(projectName, f.getName))
    val start = packageDir.listFiles().map(f => f -> "./%s/%s".format(projectName, f.getName))
    val conf = (packageDir / "conf").listFiles().map(f => f -> "./%s/conf/%s".format(projectName, f.getName))
    val logs = Seq((packageDir / "logs") -> "./%s/logs/".format(projectName))

    IO.zip(libs ++ start ++ conf ++ logs, zip)
    IO.delete(dist / packageName)

    state
  }
}

object Dependencies {
  val resolutionRepos = Seq(
    "TrafficLand Artifactory Server" at "http://build01.tl.com:8081/artifactory/repo",
    "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
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

  object CompileDeps {
    val akkaagent         = Group.akka                  %%  "akka-agent"               % V.akka
    val akkaremote        = Group.akka                  %%  "akka-remote"              % V.akka
    val slf4j             = "org.slf4j"                 %   "slf4j-api"                % V.slf4j
    val logback           = "ch.qos.logback"            %   "logback-classic"          % V.logback
  }

  object TestDeps {
    val junit       = "junit"                     %  "junit"                    % V.junit
    val mockito     = "org.mockito"               %  "mockito-core"             % V.mockito
    val akkatest    = Group.akka                  %% "akka-testkit"             % V.akka
    val scalatest   = "org.scalatest"             %% "scalatest"                % V.scalatest
  }
}