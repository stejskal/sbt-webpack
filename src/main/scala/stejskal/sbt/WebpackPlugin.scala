package stejskal.sbt

import com.typesafe.sbt.jse.JsTaskImport.JsTaskKeys._
import com.typesafe.sbt.jse.SbtJsEngine.autoImport.JsEngineKeys._
import com.typesafe.sbt.jse._
import com.typesafe.sbt.web.SbtWeb.autoImport.WebKeys._
import com.typesafe.sbt.web.SbtWeb.autoImport._
import com.typesafe.sbt.web.incremental
import com.typesafe.sbt.web.incremental.{OpFailure, OpInputHash, OpInputHasher, OpSuccess}
import com.typesafe.sbt.web.pipeline.Pipeline
import sbt.Keys._
import sbt._

object WebpackPlugin extends AutoPlugin
{
  object autoImport
  {
    case class External(internalDep: String, externalDep: String)

    lazy val webpack = taskKey[Pipeline.Stage]("uses webpack to combine javascript into a single file")
    lazy val webpackExternals = settingKey[Seq[External]]("list of modules mapped to their eventual global variable name to exclude from webpack concatenation. Maps to 'externals' webpack configuration parameter.")
  }

  import autoImport._

  override def requires: Plugins = SbtJsTask
  override def trigger: PluginTrigger = allRequirements
  override def projectSettings = Seq(
    webpackExternals in webpack := Seq(),
    excludeFilter in webpack := new SimpleFileFilter({
      f =>
        def fileStartsWith(dir: File): Boolean = f.getPath.startsWith(dir.getPath)
        fileStartsWith((resourceDirectory in Assets).value) || fileStartsWith((WebKeys.webModuleDirectory in Assets).value)
    }),
    resourceManaged in webpack := webTarget.value / webpack.key.label,
    webpack := runWebpack.dependsOn(webJarsNodeModules in Plugin).dependsOn(npmNodeModules in Assets).value
  )

  private def runWebpack: Def.Initialize[Task[Pipeline.Stage]] = Def.task
  {
    mappings =>
      val include = (includeFilter in webpack).value
      val exclude = (excludeFilter in webpack).value
      val webpackMappings = mappings.filter(f => !f._1.isDirectory && include.accept(f._1) && !exclude.accept(f._1))

      implicit val opInputHasher = OpInputHasher[(File, String)](path => OpInputHash.hashString(path._1.getAbsolutePath))

      val configFile = (target in Assets).value / "webpack" / "config.js"

      val externalsJsArray = (webpackExternals in webpack).value.map
      {
        case External(inside, outside) => s"""'$inside': '$outside'"""
      }.mkString(",")

      IO.write(configFile,
        s"""|module.exports = {
            |  externals: {$externalsJsArray}
            |};
        """.stripMargin)

      val (outputFiles, _) = incremental.syncIncremental(streams.value.cacheDirectory / "run", webpackMappings)
      {
        inputs: Seq[(File, String)] =>
          if (inputs.nonEmpty)
          {
            streams.value.log.info(s"packing with webpack")

            val nodeModulePaths = (nodeModuleDirectories in Plugin).value.map(_.getPath)
            val webpackjsShell = baseDirectory.value / "node_modules" / "webpack" / "bin" / "webpack.js"

            val result = inputs.map
            {
              input =>
                val (inputFile, outputPath) = input
                val outputFile = (resourceManaged in webpack).value / outputPath.replace(".js", ".packed.js")
                val args = Seq(inputFile.getAbsolutePath, outputFile.getAbsolutePath, "--config", configFile.getAbsolutePath)
                val execution = SbtJsTask.executeJs(
                  state.value,
                  (engineType in webpack).value,
                  (command in webpack).value,
                  nodeModulePaths,
                  webpackjsShell,
                  args,
                  (timeoutPerSource in webpack).value
                )

                if (execution.headOption.fold(true)(_ => false))
                {
                  (input, OpSuccess(Set(inputFile), Set(outputFile)))
                }
                else
                {
                  (input, OpFailure)
                }
            }
            (result.toMap, ())
          }
          else
          {
            (Map.empty, ())
          }
      }

      (mappings.toSet ++ outputFiles.pair(relativeTo((resourceManaged in webpack).value))).toSeq
  }
}
