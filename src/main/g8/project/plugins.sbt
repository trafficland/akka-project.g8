resolvers += "TrafficLand Artifactory Server" at "http://build01.tl.com:8081/artifactory/repo"

// The Typesafe repository 
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "sbt-idea-repo" at "http://mpeltonen.github.com/maven/"

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.0.0")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.1.0")

resolvers += Resolver.url("TrafficLand Artifactory Plugins Server", url("http://build01.tl.com:8081/artifactory/repo"))(Resolver.ivyStylePatterns)

addSbtPlugin("com.trafficland" % "tl-sbt-plugins" % "0.6.6")