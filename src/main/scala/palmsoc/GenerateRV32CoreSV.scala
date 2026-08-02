package palmsoc

import circt.stage.ChiselStage
import palmsoc.core.RV32Core
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

object GenerateRV32CoreSV {
  private val SynthesisFirtoolOpts: Array[String] = Array(
    "--default-layer-specialization=disable",
    "--disable-layers=Verification",
    "--disable-mem-randomization",
    "--disable-reg-randomization"
  )

  def main(args: Array[String]): Unit = {
    val targetPath = Paths.get("GeneratedSV")
    Files.createDirectories(targetPath)

    val coreConfig = palmsoc.config.SoCConfig(enableMExtension = true, enableBExtension = true)

    println(s"Compiling RV32Core to SystemVerilog...")

    val sv = ChiselStage.emitSystemVerilog(
      gen = new RV32Core(coreConfig),
      firtoolOpts = SynthesisFirtoolOpts
    )

    val outPath = targetPath.resolve("RV32Core.sv")
    Files.write(outPath, sv.getBytes(StandardCharsets.UTF_8))

    println(s"Generated SystemVerilog in: GeneratedSV/RV32Core.sv")
  }
}
