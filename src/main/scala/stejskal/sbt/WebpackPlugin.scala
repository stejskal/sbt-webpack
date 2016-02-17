package stejskal.sbt

import com.typesafe.sbt.jse.JsTaskImport.JsTaskKeys._
import com.typesafe.sbt.jse.SbtJsEngine.autoImport.JsEngineKeys._
import com.typesafe.sbt.jse._
import com.typesafe.sbt.web.SbtWeb.autoImport.WebKeys._
import com.typesafe.sbt.web.SbtWeb.autoImport._
import com.typesafe.sbt.web.{SbtWeb, incremental}
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
  }

  import autoImport._

  override def requires: Plugins = SbtJsTask

  override def trigger: PluginTrigger = allRequirements

  override def projectSettings = Seq(
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
      val resolveDir = (target in Assets).value / "webpack"
      val webpackMappings = mappings.filter(f => !f._1.isDirectory && include.accept(f._1) && !exclude.accept(f._1))
      val (jsMappings, jsHashes) = mappings.foldLeft((List[(File, String)](), Array[Byte]()))
      {
        case ((mappingAgg, hashAgg), mapping) =>
          if (mapping._1.getName.endsWith(".js")) (mapping :: mappingAgg, hashAgg ++ mapping._1.hash)
          else (mappingAgg, hashAgg)
      }

      SbtWeb.syncMappings(
        streams.value.cacheDirectory,
        jsMappings,
        resolveDir
      )

      implicit val opInputHasher = OpInputHasher[(File, String)](path => OpInputHash.hashBytes(jsHashes))

      val configFile = baseDirectory.value / "webpack.config.js" 

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
                val (_, relativePath) = input
                val inputFile = resolveDir / relativePath
                val outputFile = (resourceManaged in webpack).value / relativePath.replace(".js", ".packed.js")
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
