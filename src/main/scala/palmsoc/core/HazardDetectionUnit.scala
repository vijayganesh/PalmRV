package palmsoc.core

import chisel3._
import chisel3.util._

/**
 * Hazard Detection Unit
 * 
 * Detects Load-Use hazards and inserts a pipeline stall when necessary.
 */
class HazardDetectionUnit extends Module {
  val io = IO(new Bundle {
    // Inputs from Decode stage
    val id_rs1 = Input(UInt(5.W))
    val id_rs2 = Input(UInt(5.W))
    val id_rs1_used = Input(Bool())
    val id_rs2_used = Input(Bool())
    
    // Inputs from Execute stage
    val ex_mem_read = Input(Bool())
    val ex_rd = Input(UInt(5.W))
    
    // Output control signals
    val load_use_stall = Output(Bool())
  })
  
  // Default no stall
  io.load_use_stall := false.B
  
  // Load-Use Hazard Detection:
  // If the instruction in EX is a load (ex_mem_read), and its destination register
  // matches either source register of the instruction in ID, we must stall 1 cycle.
  when(io.ex_mem_read && io.ex_rd =/= 0.U) {
    val rs1_match = io.id_rs1_used && (io.id_rs1 === io.ex_rd)
    val rs2_match = io.id_rs2_used && (io.id_rs2 === io.ex_rd)
    
    when(rs1_match || rs2_match) {
      io.load_use_stall := true.B
    }
  }
}
