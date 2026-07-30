package palmsoc.core

import chisel3._
import chisel3.util._

/**
 * Forwarding Unit
 * 
 * Resolves Read-After-Write (RAW) data hazards by forwarding 
 * ALU results from the Memory or Writeback stages back to the Execute stage.
 */
class ForwardingUnit extends Module {
  val io = IO(new Bundle {
    // Inputs from Execute stage
    val ex_rs1 = Input(UInt(5.W))
    val ex_rs2 = Input(UInt(5.W))
    
    // Inputs from Memory stage
    val mem_rd = Input(UInt(5.W))
    val mem_reg_write = Input(Bool())
    
    // Inputs from Writeback stage
    val wb_rd = Input(UInt(5.W))
    val wb_reg_write = Input(Bool())
    
    // Outputs to Execute stage
    // 0 = no forward, 1 = forward from MEM, 2 = forward from WB
    val forward_a = Output(UInt(2.W))
    val forward_b = Output(UInt(2.W))
  })
  
  // Default values
  io.forward_a := 0.U
  io.forward_b := 0.U
  
  // Forward A logic (for rs1)
  when(io.mem_reg_write && io.mem_rd =/= 0.U && io.mem_rd === io.ex_rs1) {
    // EX hazard (data from MEM stage)
    io.forward_a := 1.U
  }.elsewhen(io.wb_reg_write && io.wb_rd =/= 0.U && io.wb_rd === io.ex_rs1) {
    // MEM hazard (data from WB stage)
    io.forward_a := 2.U
  }
  
  // Forward B logic (for rs2)
  when(io.mem_reg_write && io.mem_rd =/= 0.U && io.mem_rd === io.ex_rs2) {
    // EX hazard (data from MEM stage)
    io.forward_b := 1.U
  }.elsewhen(io.wb_reg_write && io.wb_rd =/= 0.U && io.wb_rd === io.ex_rs2) {
    // MEM hazard (data from WB stage)
    io.forward_b := 2.U
  }
}
