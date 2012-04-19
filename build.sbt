import AssemblyKeys._

name           := "ScalaColliderSwing"

version        := "0.34"

organization   := "de.sciss"

scalaVersion   := "2.9.2"

description := "A Swing and REPL front-end for ScalaCollider"

homepage := Some( url( "https://github.com/Sciss/ScalaColliderSwing" ))

licenses := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

resolvers += "Clojars Repository" at "http://clojars.org/repo"  // for jsyntaxpane

libraryDependencies ++= Seq(
   "de.sciss" %% "scalacollider" % "0.34",
   "de.sciss" %% "scalainterpreterpane" % "0.21",
   "de.sciss" % "prefuse-core" % "0.21",
   "de.sciss" %% "scalaaudiowidgets" % "0.11"
)

retrieveManaged := true

// this should make it possible to launch from sbt, but there is still a class path issue?
// fork in run := true

scalacOptions ++= Seq( "-deprecation", "-unchecked" )

// ---- publishing ----

publishMavenStyle := true

publishTo <<= version { (v: String) =>
   Some( if( v.endsWith( "-SNAPSHOT" ))
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
   else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
   )
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra :=
<scm>
  <url>git@github.com:Sciss/ScalaColliderSwing.git</url>
  <connection>scm:git:git@github.com:Sciss/ScalaColliderSwing.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>

// ---- packaging ----

seq( assemblySettings: _* )

test in assembly := {}

seq( appbundle.settings: _* )

appbundle.icon := Some( file( "application.icns" ))

// ---- ls.implicit.ly ----

seq( lsSettings :_* )

(LsKeys.tags in LsKeys.lsync) := Seq( "sound-synthesis", "sound", "music", "supercollider" )

(LsKeys.ghUser in LsKeys.lsync) := Some( "Sciss" )

(LsKeys.ghRepo in LsKeys.lsync) := Some( "ScalaColliderSwing" )

// bug in ls -- doesn't find the licenses from global scope
(licenses in LsKeys.lsync) := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))