package palmsoc.core

import chisel3._
import chisel3.util._
import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim
import palmsoc.config.SoCConfig

class ForwardingTest extends AnyFunSpec with ChiselSim {
  describe("RV32Core Hardware Forwarding") {
    it("should resolve back-to-back RAW hazards without NOPs") {
      val config = SoCConfig(enableMExtension = true, enableBExtension = true)
      simulate(new RV32Core(config)) { dut =>
        // Initialize inputs
        dut.io.imem_valid.poke(true.B)
        dut.io.dmem_rdata.poke(0.U)
        dut.io.dmem_valid.poke(true.B)
        
        // Cycle 0: Fetch ADDI x1, x0, 5
        // Machine Code: 00500093
        dut.io.imem_data.poke("h00500093".U)
        dut.clock.step(1)
        
        // Cycle 1: Fetch ADDI x2, x1, 7
        // Machine Code: 00708113
        // This creates a RAW hazard on x1. The Execute stage must forward from MEM.
        dut.io.imem_data.poke("h00708113".U)
        dut.clock.step(1)
        
        // Cycle 2: Fetch ADD x3, x1, x2
        // Machine Code: 002081b3
        // This creates a RAW hazard on x1 (from WB) and x2 (from MEM).
        dut.io.imem_data.poke("h002081b3".U)
        dut.clock.step(1)
        
        // Cycle 3: Fetch SW x3, 0(x0)
        // Machine Code: 00302023 
        // (imm[11:5]=0000000, rs2=3, rs1=0, funct3=2, imm[4:0]=00000, opcode=0x23)
        // This creates a RAW hazard on x3 (from MEM).
        dut.io.imem_data.poke("h00302023".U)
        dut.clock.step(1)
        
        // Cycle 4-5: Fetch NOPs to flush pipeline
        dut.io.imem_data.poke("h00000013".U)
        dut.clock.step(1)
        dut.clock.step(1)
        
        // At Cycle 6, the SW instruction should be in the Memory stage.
        // It should attempt to write x3 (5 + (5+7) = 17) to memory address 0.
        dut.io.dmem_write.expect(true.B)
        dut.io.dmem_addr.expect(0.U)
        dut.io.dmem_wdata.expect(17.U)
        
        dut.clock.step(1)
      }
    }
  }
}
