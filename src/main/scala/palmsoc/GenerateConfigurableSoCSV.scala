package palmsoc

import circt.stage.ChiselStage
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

/**
 * Main Runner to generate SystemVerilog for various ConfigurablePalmSoC options
 */
object GenerateConfigurableSoCSV {

  private val SynthesisFirtoolOpts: Array[String] = Array(
    "--default-layer-specialization=disable",
    "--disable-layers=Verification",
    "--disable-mem-randomization",
    "--disable-reg-randomization"
  )

  private final case class Config(
    gpio: Boolean = true,
    uart: Boolean = true,
    i2c: Boolean = true,
    targetDir: String = "GeneratedSV",
    outputFile: String = "ConfigurablePalmSoC.sv"
  )

  private def parseArgs(args: Array[String]): Config = {
    args.foldLeft(Config()) { (cfg, arg) =>
      val parts = arg.split("=", 2)
      if (parts.length != 2) {
        throw new IllegalArgumentException(
          s"Invalid argument '$arg'. Expected key=value format."
        )
      }

      parts(0).trim.toLowerCase match {
        case "gpio"          => cfg.copy(gpio = parts(1).toBoolean)
        case "uart"          => cfg.copy(uart = parts(1).toBoolean)
        case "i2c"           => cfg.copy(i2c = parts(1).toBoolean)
        case "targetdir"     => cfg.copy(targetDir = parts(1))
        case "outputfile"    => cfg.copy(outputFile = parts(1))
        case other =>
          throw new IllegalArgumentException(
            s"Unknown option '$other'. Supported: gpio, uart, i2c, targetDir, outputFile"
          )
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val cfg = parseArgs(args)

    val targetPath = Paths.get(cfg.targetDir)
    Files.createDirectories(targetPath)

    val socConfig = ConfigurablePalmSoCConfig(
      hasGPIO = cfg.gpio,
      hasUART = cfg.uart,
      hasI2C = cfg.i2c
    )

    println(s"Compiling ConfigurablePalmSoC with options:")
    println(s"  - GPIO:           ${cfg.gpio}")
    println(s"  - UART:           ${cfg.uart}")
    println(s"  - I2C:            ${cfg.i2c}")

    val sv = ChiselStage.emitSystemVerilog(
      gen = new ConfigurablePalmSoC(socConfig),
      firtoolOpts = SynthesisFirtoolOpts
    )

    val outPath = targetPath.resolve(cfg.outputFile)
    Files.write(outPath, sv.getBytes(StandardCharsets.UTF_8))

    println(s"Generated SystemVerilog in: ${cfg.targetDir}")
    println(s"  - ${outPath.toAbsolutePath}")
  }
}
