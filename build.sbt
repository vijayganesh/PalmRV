
ThisBuild / scalaVersion     := "2.13.17"
ThisBuild / version          := "0.1.1"
ThisBuild / organization     := "org.palmV"

//lazy val prathamaProjectPath = file("Prathama_RISCV/RV32Core")
// javacOptions ++= Seq("-source", "25", "-target", "25")
// javaOptions ++= Seq("-Djava.awt.headless=true")

// // Ensure Java compatibility
// initialize := {
//   val _ = initialize.value // Run the previous initialization
//   val required = "25"
//   val current  = sys.props("java.specification.version")
//   assert(current == required, s"Requires Java $required but currently using $current")
// }

Test / fork := true
Test / parallelExecution := false

fork := true

// Global settings
Global / cancelable := true
lazy val root = (project in file("."))
  .settings(
    name := "floatsNumber",
    // idePackagePrefix := Some("org.vricsa.LSTM"),

   
       libraryDependencies ++= Seq(
      "org.chipsalliance" %% "chisel" % "7.2.0",
      //"org.scalanlp" %% "breeze" % "2.1.0",
      //"org.scalanlp" %% "breeze-viz" % "2.1.0",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
      //"edu.berkeley.cs" %% "chiseltest" % "6.0.0" % Test,

        // Additional required for testing
        // https://mvnrepository.com/artifact/org.json4s/json4s-jackson
        // above m4 it requres scala 2.13.12 and above which is incompat with chiseltest
        //"org.json4s" %% "json4s-jackson" % "4.1.0-M4",
      //  "org.json4s" %% "json4s-native" % "4.1.0-M4",
       // "org.yaml" % "snakeyaml" % "2.0",

        "org.apache.commons" % "commons-compress" % "1.26.0",

       // "org.apache.xmlgraphics" % "xmlgraphics-commons" % "2.7",
       // "org.iq80.snappy" % "snappy" % "0.5",
       // "com.itextpdf" % "itextpdf" % "5.5.13.3",
        // Verification chiselverify
        // "io.github.chiselverify" % "chiselverify" % "0.4.0",





      ),
   // excludeDependencies += "com.lowagie" % "itext",

    /*scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
      "-P:chiselplugin:genBundleElements",
    ),*/
    addCompilerPlugin("org.chipsalliance" %% "chisel-plugin" % "7.2.0" cross CrossVersion.full),
  )
//Compile / unmanagedSourceDirectories += prathamaProjectPath / "src" / "main" / "scala"
// Compile / unmanagedSourceDirectories += prathamaProjectPath / "src" / "test" / "scala"

/*
def setUnlimitedStackSize:Unit = {
  val env = System.getenv()
  env.put("VERILATOR_ULIMIT", "ulimit -s unlimited")
}
setUnlimitedStackSize
*/