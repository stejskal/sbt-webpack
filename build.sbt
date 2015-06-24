import bintray.Keys._
name := """sbt-webpack"""
description := "sbt plugin to bundle javascript using webpack"
organization := "stejskal"
version := "0.1"
sbtPlugin := true
publishMavenStyle := false
licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))
repository in bintray := "sbt-plugins"
bintrayOrganization in bintray := None

addSbtPlugin("com.typesafe.sbt" % "sbt-js-engine" % "1.1.3")

bintrayPublishSettings
