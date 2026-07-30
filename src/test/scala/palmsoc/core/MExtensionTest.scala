package palmsoc.core

import chisel3._
import chisel3.util._
import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim
import palmsoc.config.SoCConfig

class MExtensionTest extends AnyFunSpec with ChiselSim {
  describe("RV32Core M Extension (Enabled)") {
    it("should compute MUL operations correctly and stall appropriately") {
      val config = SoCConfig(enableMExtension = true, enableBExtension = true)
      simulate(new RV32Core(config)) { dut =>
        dut.io.imem_valid.poke(true.B)
        dut.io.dmem_rdata.poke(0.U)
        dut.io.dmem_valid.poke(true.B)
        
        // Provide instructions based on imem_addr to gracefully handle pipeline stalls
        var cycles = 0
        while (cycles < 20) {
          val pc = dut.io.imem_addr.peek().litValue
          if (pc == 0) dut.io.imem_data.poke("h00a00093".U) // ADDI x1, x0, 10
          else if (pc == 4) dut.io.imem_data.poke("h00300113".U) // ADDI x2, x0, 3
          else if (pc == 8) dut.io.imem_data.poke("h022081b3".U) // MUL x3, x1, x2
          else if (pc == 12) dut.io.imem_data.poke("h00302023".U) // SW x3, 0(x0)
          else dut.io.imem_data.poke("h00000013".U) // NOP
          
          dut.clock.step(1)
          cycles += 1
          
          // Check if dmem_write is asserted to break early and verify
          if (dut.io.dmem_write.peek().litValue == 1) {
            dut.io.dmem_wdata.expect(30.U)
            cycles = 100 // break
          }
        }
        
        assert(cycles == 100, "Memory write was never asserted")
      }
    }
  }
}
