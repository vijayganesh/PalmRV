package palmsoc

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim
import palmsoc.config.SoCConfig
import palmsoc.memory.BootROM

class BenchmarkTest extends AnyFunSpec with ChiselSim {
  describe("PalmSoC Benchmarks") {
    it("should run hello world bare-metal program") {
      // Create config with M extension enabled
      val coreConfig = SoCConfig(enableMExtension = true)
      val socConfig = ConfigurablePalmSoCConfig(coreConfig = coreConfig)
      
      // Load hello.hex
      val hexPath = "benchmarks/hello/hello.hex"
      val initContent = BootROM.loadHexFile(hexPath)
      
      simulate(new ConfigurablePalmSoC(socConfig, Some(initContent))) { dut =>
        
        // Wait for reset
        dut.clock.step(10)
        
        // Read UART TX to see printed characters
        // In simulation, we can monitor the UART peripheral's data
        var cycles = 0
        
        // Simulate for a reasonable amount of time to let hello world finish
        while (cycles < 10000) {
          dut.clock.step(100)
          cycles += 100
        }
        
        println(s"Simulated $cycles cycles.")
      }
    }
  }
}
