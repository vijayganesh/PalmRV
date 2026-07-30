package generate

import circt.stage.ChiselStage
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import palmsoc.PalmSoC

object GeneratePalmSoC {

  private val SynthesisFirtoolOpts: Array[String] = Array(
    "--default-layer-specialization=disable",
    "--disable-layers=Verification",
    "--disable-mem-randomization",
    "--disable-reg-randomization"
  )

  private final case class Config(
      targetDir: String = "test/generate",
      outputFile: String = "PalmSoC.sv"
  )

  private def parseArgs(args: Array[String]): Config = {
    args.foldLeft(Config()) { (cfg, arg) =>
      val parts = arg.split("=", 2)
      if (parts.length != 2) {
        throw new IllegalArgumentException(
          s"Invalid argument '$arg'. Expected key=value format."
        )
      }

      parts(0) match {
        case "targetDir" => cfg.copy(targetDir = parts(1))
        case "outputFile" => cfg.copy(outputFile = parts(1))
        case other =>
          throw new IllegalArgumentException(
            s"Unknown option '$other'. Supported: targetDir, outputFile"
          )
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val cfg = parseArgs(args)

    val targetPath = Paths.get(cfg.targetDir)
    Files.createDirectories(targetPath)

    println(s"Elaborating PalmSoC (without accelerator, only GPIO)...")
    val sv = ChiselStage.emitSystemVerilog(
      gen = new PalmSoC,
      firtoolOpts = SynthesisFirtoolOpts
    )

    val outPath = targetPath.resolve(cfg.outputFile)
    Files.write(outPath, sv.getBytes(StandardCharsets.UTF_8))

    println(s"Generated SystemVerilog in: ${cfg.targetDir}")
    println(s"  - ${outPath.toAbsolutePath}")
  }
}
