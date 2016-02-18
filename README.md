sbt-webpack
==========

An SBT plugin to bundle javascript modules using the [webpack module bundler](http://webpack.github.io/).

To use this plugin use the addSbtPlugin command within your project's `plugins.sbt` file:

    addSbtPlugin("stejskal" % "sbt-webpack" % "0.2")

Your project's build file also needs to enable sbt-web plugins. For example with build.sbt:

    lazy val root = (project in file(".")).enablePlugins(SbtWeb)

As with all sbt-web asset pipeline plugins you must declare their order of execution e.g.:
```scala
pipelineStages := Seq(webpack)
```

finally you will need to add a package.json file at the root of your project that contains:
```json
{
  "devDependencies": {
    "webpack": "^1.9.11"
  }
}
```

The plugin processes all files that match the filter:
```scala
includeFilter in webpack
```

each file that matches will be processed as the root of a webpack bundle and will output a file named
{base original filename}.packed.js.  for example if the file main.js was processed, it would output a file
named main.packed.js.

Currently this project builds with all the default webpack options except externals.  You can
supply a list of modules that should be considered external by wepback.  For example, if you want to require
the reactjs module in your own modules, but not have it bundled into the resulting bundle you would add
External("react", "React") to the webpackExternals setting:

```scala
webpackExternals in webpack += External("react", "React")
```

this will cause webpack to skip inclusion of the module named "react" in the resulting bundle and instead
expose a dummy module that only exports the global javascript variable named "React" (notice the title case difference).

If you would like to supply your own webpack configuration file e.g. webpack.config.js, you'll have to set a setting for the plugin in the form of: 

```scala 
webpackConfig in webpack := Some(path_to_config_file)
```

Improvements needed:
* expose all webpack options
* custom output filenames
* figure out how to package/install webpack js file without requiring projects to have a package.json file
* per input file webpack settings?

Thanks to [TaskEasy](https://www.taskeasy.com/) for allowing me to work on this plugin.

Copyright 2015 Spencer Stejskal

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
