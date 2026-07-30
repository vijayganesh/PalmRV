package palmsoc.core

import chisel3._
import chisel3.util._
import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim
import palmsoc.config.SoCConfig

class HazardTest extends AnyFunSpec with ChiselSim {
  describe("RV32Core Hazard Resolution") {
    
    it("should correctly handle control hazards by flushing instructions after a jump") {
      val config = SoCConfig(enableMExtension = true, enableBExtension = true)
      simulate(new RV32Core(config)) { dut =>
        dut.io.imem_valid.poke(true.B)
        dut.io.dmem_rdata.poke(0.U)
        dut.io.dmem_valid.poke(true.B)
        
        // PC 0: ADDI x1, x0, 1 (x1 = 1)
        // Machine Code: 00100093
        dut.io.imem_data.poke("h00100093".U)
        dut.clock.step(1)
        
        // PC 4: JAL x0, 12 (Jump to PC=16)
        // Machine Code: 00c0006f (imm=12)
        dut.io.imem_data.poke("h00c0006f".U)
        dut.clock.step(1)
        
        // PC 8: ADDI x1, x0, 99 (Should be flushed, x1 should NOT become 99)
        // Machine Code: 06300093
        dut.io.imem_data.poke("h06300093".U)
        dut.clock.step(1)
        
        // At this point, the JAL is in Execute. It will evaluate and assert flush!
        // PC 12: ADDI x1, x0, 100 (Should also be flushed)
        // Machine Code: 06400093
        dut.io.imem_data.poke("h06400093".U)
        dut.clock.step(1)
        
        // PC 16: ADDI x2, x1, 5
        // Since x1 is 1 (the 99 and 100 were flushed), x2 should be 1 + 5 = 6
        // Machine Code: 00508113
        dut.io.imem_data.poke("h00508113".U)
        dut.clock.step(1)
        
        // PC 20: SW x2, 0(x0)
        // Machine Code: 00202023
        dut.io.imem_data.poke("h00202023".U)
        dut.clock.step(1)
        
        // NOPs to push SW to memory stage
        dut.io.imem_data.poke("h00000013".U)
        dut.clock.step(2)
        
        // The SW should write 6 to address 0
        dut.io.dmem_write.expect(true.B)
        dut.io.dmem_wdata.expect(6.U)
        
        dut.clock.step(1)
      }
    }
    
    it("should stall pipeline gracefully when instruction memory is not valid") {
      val config = SoCConfig(enableMExtension = true, enableBExtension = true)
      simulate(new RV32Core(config)) { dut =>
        dut.io.dmem_rdata.poke(0.U)
        dut.io.dmem_valid.poke(true.B)
        
        // PC 0: ADDI x3, x0, 42
        dut.io.imem_valid.poke(true.B)
        dut.io.imem_data.poke("h02a00193".U)
        dut.clock.step(1)
        
        // Simulate a 2-cycle structural/memory wait state
        dut.io.imem_valid.poke(false.B)
        dut.clock.step(2)
        
        // Memory recovers
        dut.io.imem_valid.poke(true.B)
        // PC 4: SW x3, 0(x0)
        dut.io.imem_data.poke("h00302023".U)
        dut.clock.step(1)
        
        // NOPs
        dut.io.imem_data.poke("h00000013".U)
        dut.clock.step(2)
        
        // Expect the value 42 to be written, meaning the stall preserved the pipeline
        dut.io.dmem_write.expect(true.B)
        dut.io.dmem_wdata.expect(42.U)
      }
    }
  }
}
