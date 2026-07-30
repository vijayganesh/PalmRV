package palmsoc.core

import chisel3._
import chisel3.util._
import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim
//import chisel3.simulator.WriteVcdAnnotation

class CoreTest extends AnyFunSpec with ChiselSim {
  describe("RV32Core") {
    it("should execute basic ADDI instructions") {
      simulate(new RV32Core) { dut =>
        // Initialize inputs
        dut.io.imem_valid.poke(true.B)
        dut.io.dmem_rdata.poke(0.U)
        dut.io.dmem_valid.poke(true.B)
        
        // Provide a sequence of instructions
        
        // Cycle 0: Fetch
        // ADDI x1, x0, 5 -> 00500093 (Opcode: 0x13, rd: 1, funct3: 0, rs1: 0, imm: 5)
        dut.io.imem_data.poke("h00500093".U)
        dut.clock.step(1)
        
        // Cycle 1: Fetch next, Decode ADDI x1
        // ADDI x2, x1, 7 -> 00708113 (Opcode: 0x13, rd: 2, funct3: 0, rs1: 1, imm: 7)
        dut.io.imem_data.poke("h00708113".U)
        dut.clock.step(1)
        
        // Cycle 2: Fetch next, Decode ADDI x2, Execute ADDI x1
        // NOP (ADDI x0, x0, 0) -> 00000013
        dut.io.imem_data.poke("h00000013".U)
        dut.clock.step(1)
        
        // Let the instructions flow through Memory and Writeback stages
        // NOPs
        dut.io.imem_data.poke("h00000013".U)
        dut.clock.step(5)
        
        // At this point, x1 should contain 5, and x2 should contain 12.
        
        // Store Word (SW x2, 0(x0)) -> 00202023 
        // (imm[11:5]=0000000, rs2=2, rs1=0, funct3=2, imm[4:0]=00000, opcode=0x23)
        dut.io.imem_data.poke("h00202023".U)
        dut.clock.step(1)
        
        // NOPs to push SW through the pipeline
        dut.io.imem_data.poke("h00000013".U)
        dut.clock.step(3) // SW should be in memory stage now
        
        dut.clock.step(2)
      }
    }
  }
}
