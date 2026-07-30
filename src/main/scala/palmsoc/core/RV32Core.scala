package palmsoc.core

import chisel3._
import chisel3.util._







/**
 * Complete RISC-V RV32I Core
 * 
 * 5-stage pipeline: Fetch -> Decode -> Execute -> Memory -> Writeback
 */
class RV32Core extends Module {
  val io = IO(new Bundle {
    // Instruction memory interface
    val imem_addr = Output(UInt(32.W))
    val imem_data = Input(UInt(32.W))
    val imem_valid = Input(Bool())
    
    // Data memory interface
    val dmem_addr = Output(UInt(32.W))
    val dmem_wdata = Output(UInt(32.W))
    val dmem_rdata = Input(UInt(32.W))
    val dmem_write = Output(Bool())
    val dmem_read = Output(Bool())
    val dmem_size = Output(UInt(2.W))
    val dmem_valid = Input(Bool())
    
    // Interrupt interface
    val external_interrupt = Input(Bool())
  })
  
  // Pipeline stages
  val fetch = Module(new FetchStage)
  val decode = Module(new DecodeStage)
  val execute = Module(new ExecuteStage)
  val memory = Module(new MemoryStage)
  val writeback = Module(new WritebackStage)
  val regfile = Module(new RegisterFile)
  
  // Instantiate CSRFile
  val csr = Module(new CSRFile)
  
  // Control signals
  val stall = Wire(Bool())
  val flush = Wire(Bool())
  
  // Branch/Jump/Trap control
  val taking_trap = csr.io.taking_trap
  val mret_active = memory.io.mret_out
  val take_branch = execute.io.branch_taken || execute.io.jump_taken
  flush := take_branch || taking_trap || mret_active
  
  // Stall on load-use hazard or instruction memory wait
  stall := (memory.io.mem_read && !memory.io.valid_out) || !io.imem_valid
  
  // Fetch stage connections
  fetch.io.pc_next := Mux(taking_trap, csr.io.trap_vector,
                        Mux(mret_active, csr.io.epc,
                                         execute.io.target_pc))
  fetch.io.pc_sel := take_branch || taking_trap || mret_active
  fetch.io.stall := stall
  fetch.io.flush := flush
  fetch.io.imem_data := io.imem_data
  fetch.io.imem_valid := io.imem_valid
  io.imem_addr := fetch.io.imem_addr
  
  // Decode stage connections
  decode.io.instruction := fetch.io.instruction
  decode.io.pc_in := fetch.io.pc_to_decode
  decode.io.valid_in := fetch.io.valid
  decode.io.rs1_data := regfile.io.rs1_data
  decode.io.rs2_data := regfile.io.rs2_data
  decode.io.stall := stall
  decode.io.flush := flush
  
  // Register file read
  regfile.io.rs1_addr := decode.io.rs1_addr
  regfile.io.rs2_addr := decode.io.rs2_addr
  
  // Execute stage connections
  execute.io.pc_in := decode.io.pc_out
  execute.io.rs1_data := decode.io.rs1_data_out
  execute.io.rs2_data := decode.io.rs2_data_out
  execute.io.imm := decode.io.imm
  execute.io.rd_addr_in := decode.io.rd_addr
  execute.io.alu_op := decode.io.alu_op
  execute.io.alu_src1_sel := decode.io.alu_src1_sel
  execute.io.alu_src2_sel := decode.io.alu_src2_sel
  execute.io.branch := decode.io.branch
  execute.io.jump := decode.io.jump
  execute.io.mem_read_in := decode.io.mem_read
  execute.io.mem_write_in := decode.io.mem_write
  execute.io.mem_size_in := decode.io.mem_size
  execute.io.mem_unsigned_in := decode.io.mem_unsigned
  execute.io.reg_write_in := decode.io.reg_write
  execute.io.wb_sel_in := decode.io.wb_sel
  execute.io.valid_in := decode.io.valid_out
  execute.io.stall := stall
  execute.io.flush := flush
  execute.io.csr_cmd_in := decode.io.csr_cmd
  execute.io.csr_addr_in := decode.io.csr_addr
  execute.io.mret_in := decode.io.mret
  
  // Memory stage connections
  memory.io.alu_result_in := execute.io.alu_result
  memory.io.mem_write_data := execute.io.mem_write_data
  memory.io.rd_addr_in := execute.io.rd_addr_out
  memory.io.mem_read := execute.io.mem_read_out
  memory.io.mem_write := execute.io.mem_write_out
  memory.io.mem_size := execute.io.mem_size_out
  memory.io.mem_unsigned := execute.io.mem_unsigned_out
  memory.io.reg_write_in := execute.io.reg_write_out
  memory.io.wb_sel_in := execute.io.wb_sel_out
  memory.io.pc_plus_4_in := execute.io.pc_plus_4
  memory.io.valid_in := execute.io.valid_out
  memory.io.dmem_rdata := io.dmem_rdata
  memory.io.dmem_valid := io.dmem_valid
  memory.io.stall := stall
  memory.io.flush := flush
  memory.io.csr_cmd_in := execute.io.csr_cmd_out
  memory.io.csr_addr_in := execute.io.csr_addr_out
  memory.io.mret_in := execute.io.mret_out
  
  // CSRFile Connections
  csr.io.csr_addr := memory.io.csr_addr_in
  csr.io.csr_cmd := Mux(memory.io.valid_in, memory.io.csr_cmd_in, 0.U)
  csr.io.csr_wdata := memory.io.alu_result_in
  memory.io.csr_rdata := csr.io.csr_rdata
  
  csr.io.exception := false.B
  csr.io.exception_cause := 0.U
  csr.io.exception_pc := memory.io.pc_plus_4_in - 4.U
  csr.io.exception_tval := 0.U
  
  csr.io.interrupt := false.B
  csr.io.interrupt_cause := 0.U
  
  csr.io.external_interrupt := io.external_interrupt
  csr.io.timer_interrupt := false.B
  csr.io.software_interrupt := false.B
  
  csr.io.mret := memory.io.mret_out
  csr.io.instret := writeback.io.rf_wen
  
  // Data memory interface
  io.dmem_addr := memory.io.dmem_addr
  io.dmem_wdata := memory.io.dmem_wdata
  io.dmem_write := memory.io.dmem_write
  io.dmem_read := memory.io.dmem_read
  io.dmem_size := memory.io.dmem_size
  
  // Writeback stage connections
  writeback.io.wb_data := memory.io.wb_data
  writeback.io.rd_addr := memory.io.rd_addr_out
  writeback.io.reg_write := memory.io.reg_write_out
  writeback.io.valid_in := memory.io.valid_out
  
  // Register file write
  regfile.io.rd_addr := writeback.io.rf_waddr
  regfile.io.rd_data := writeback.io.rf_wdata
  regfile.io.rd_wen := writeback.io.rf_wen
}

// Legacy class name for compatibility
class RISCV32Core extends RV32Core