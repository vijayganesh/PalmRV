package palmsoc

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim
import palmsoc.config.SoCConfig
import palmsoc.memory.BootROM
import firrtl.options.TargetDirAnnotation

class BenchmarkTest extends AnyFunSpec with ChiselSim {
  describe("PalmSoC Benchmarks") {
    it("should run hello world bare-metal program") {
      val coreConfig = SoCConfig(enableMExtension = true)
      val socConfig = ConfigurablePalmSoCConfig(coreConfig = coreConfig)
      
      val hexPath = "benchmarks/hello/hello.hex"
      val initContent = BootROM.loadHexFile(hexPath)
      
      // Try to use Verilator if available, otherwise it falls back
      simulate(new ConfigurablePalmSoC(socConfig, Some(initContent))) { dut =>
        var cycles = 0
        while (cycles < 15000) {
          dut.clock.step(100)
          cycles += 100
        }
        println(s"Simulated $cycles cycles.")
      }
    }

    it("should run dhrystone benchmark program") {
      val coreConfig = SoCConfig(enableMExtension = true)
      val socConfig = ConfigurablePalmSoCConfig(coreConfig = coreConfig)
      
      val hexPath = "benchmarks/dhrystone/dhrystone.hex"
      val initContent = BootROM.loadHexFile(hexPath)
      
      simulate(new ConfigurablePalmSoC(socConfig, Some(initContent))) { dut =>
        var cycles = 0
        while (cycles < 500000) {
          dut.clock.step(1000)
          cycles += 1000
        }
        println(s"Simulated $cycles cycles.")
      }
    }

    it("should run coremark benchmark program") {
      val coreConfig = SoCConfig(enableMExtension = true)
      val socConfig = ConfigurablePalmSoCConfig(coreConfig = coreConfig)
      
      val hexPath = "benchmarks/coremark/coremark.hex"
      val initContent = BootROM.loadHexFile(hexPath)
      
      simulate(new ConfigurablePalmSoC(socConfig, Some(initContent))) { dut =>
        var cycles = 0
        while (cycles < 1500000) {
          dut.clock.step(1000)
          cycles += 1000
        }
        println(s"Simulated $cycles cycles.")
      }
    }
  }
}
