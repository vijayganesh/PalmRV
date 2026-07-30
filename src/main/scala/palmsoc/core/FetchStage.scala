package palmsoc.core

import chisel3._

import chisel3.util._



/**
 * Instruction Fetch Stage
 * 
 * Fetches instructions from memory and updates PC.
 * Handles branch prediction and PC updates.
 */
class FetchStage extends Module {
  val io = IO(new Bundle {
    // PC control
    val pc_out = Output(UInt(32.W))
    val pc_next = Input(UInt(32.W))
    val pc_sel = Input(Bool())  // 0: PC+4, 1: branch/jump target
    val stall = Input(Bool())
    val flush = Input(Bool())
    
    // Instruction memory interface
    val imem_addr = Output(UInt(32.W))
    val imem_data = Input(UInt(32.W))
    val imem_valid = Input(Bool())
    
    // Output to decode stage
    val instruction = Output(UInt(32.W))
    val pc_to_decode = Output(UInt(32.W))
    val valid = Output(Bool())
  })
  
  // Program Counter
  val pc = RegInit(0.U(32.W))
  val pc_to_decode_reg = RegInit(0.U(32.W))
  val instruction_reg = RegInit(0.U(32.W))
  val valid_reg = RegInit(false.B)
  
  // PC calculation
  val pc_plus_4 = pc + 4.U
  val next_pc = Mux(io.pc_sel, io.pc_next, pc_plus_4)
  
  // Update PC
  when(io.flush) {
    pc := io.pc_next
    valid_reg := false.B
    instruction_reg := 0.U
    pc_to_decode_reg := 0.U
  }.elsewhen(!io.stall) {
    pc := next_pc
    instruction_reg := io.imem_data
    valid_reg := io.imem_valid
    pc_to_decode_reg := pc
  }
  
  // Outputs
  io.pc_out := pc
  io.imem_addr := pc
  io.instruction := instruction_reg
  io.pc_to_decode := pc_to_decode_reg
  io.valid := valid_reg && !io.flush
}
