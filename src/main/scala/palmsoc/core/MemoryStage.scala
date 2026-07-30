package palmsoc.core

import  chisel3._ 
import chisel3.util._


/**
 * Memory Access Stage (part of Execute for load/store)
 */

class MemoryStage extends Module {
  val io = IO(new Bundle {
    // Inputs from execute
    val alu_result_in = Input(UInt(32.W))
    val mem_write_data = Input(UInt(32.W))
    val rd_addr_in = Input(UInt(5.W))
    val mem_read = Input(Bool())
    val mem_write = Input(Bool())
    val mem_size = Input(UInt(2.W))
    val mem_unsigned = Input(Bool())
    val reg_write_in = Input(Bool())
    val wb_sel_in = Input(UInt(2.W))
    val pc_plus_4_in = Input(UInt(32.W))
    val valid_in = Input(Bool())
    
    // CSR ports
    val csr_cmd_in = Input(UInt(3.W))
    val csr_addr_in = Input(UInt(12.W))
    val mret_in = Input(Bool())
    val csr_rdata = Input(UInt(32.W))
    
    // Data memory interface
    val dmem_addr = Output(UInt(32.W))
    val dmem_wdata = Output(UInt(32.W))
    val dmem_rdata = Input(UInt(32.W))
    val dmem_write = Output(Bool())
    val dmem_read = Output(Bool())
    val dmem_size = Output(UInt(2.W))
    val dmem_valid = Input(Bool())
    
    // Outputs to writeback
    val wb_data = Output(UInt(32.W))
    val rd_addr_out = Output(UInt(5.W))
    val reg_write_out = Output(Bool())
    val valid_out = Output(Bool())
    
    // Combinational forwarding data
    val forward_data_out = Output(UInt(32.W))
    
    // CSR outputs
    val mret_out = Output(Bool())
    
    // Control
    val stall = Input(Bool())
    val flush = Input(Bool())
  })
  
  // Memory address and write data
  io.dmem_addr := io.alu_result_in
  io.dmem_write := io.mem_write
  io.dmem_read := io.mem_read
  io.dmem_size := io.mem_size
  
  // Prepare write data based on size
  val write_data = WireDefault(io.mem_write_data)
  switch(io.mem_size) {
    is(0.U) { write_data := Fill(4, io.mem_write_data(7, 0)) }   // byte
    is(1.U) { write_data := Fill(2, io.mem_write_data(15, 0)) }  // halfword
    is(2.U) { write_data := io.mem_write_data }                   // word
  }
  io.dmem_wdata := write_data
  
  // Load data alignment and sign extension
  val byte_offset = io.alu_result_in(1, 0)
  val mem_rdata_aligned = WireDefault(io.dmem_rdata)
  
  switch(io.mem_size) {
    is(0.U) {  // byte
      val byte_data = MuxLookup(byte_offset, 0.U(8.W))(Seq(
        0.U -> io.dmem_rdata(7, 0),
        1.U -> io.dmem_rdata(15, 8),
        2.U -> io.dmem_rdata(23, 16),
        3.U -> io.dmem_rdata(31, 24)
      ))
      mem_rdata_aligned := Mux(io.mem_unsigned,
        byte_data,
        Cat(Fill(24, byte_data(7)), byte_data)
      )
    }
    is(1.U) {  // halfword
      val half_data = Mux(byte_offset(1), io.dmem_rdata(31, 16), io.dmem_rdata(15, 0))
      mem_rdata_aligned := Mux(io.mem_unsigned,
        half_data,
        Cat(Fill(16, half_data(15)), half_data)
      )
    }
    is(2.U) {  // word
      mem_rdata_aligned := io.dmem_rdata
    }
  }
  
  // Writeback data selection
  val wb_data = MuxLookup(io.wb_sel_in, io.alu_result_in)(Seq(
    0.U -> io.alu_result_in,
    1.U -> mem_rdata_aligned,
    2.U -> io.pc_plus_4_in,
    3.U -> io.csr_rdata
  ))
  
  io.forward_data_out := wb_data
  
  // Pipeline registers
  val wb_data_reg = RegInit(0.U(32.W))
  val rd_addr_reg = RegInit(0.U(5.W))
  val reg_write_reg = RegInit(false.B)
  val valid_reg = RegInit(false.B)
  
  when(io.flush) {
    valid_reg := false.B
    reg_write_reg := false.B
  }.elsewhen(!io.stall) {
    wb_data_reg := wb_data
    rd_addr_reg := io.rd_addr_in
    reg_write_reg := io.reg_write_in
    valid_reg := io.valid_in && (!io.mem_read || io.dmem_valid)
  }
  
  // Outputs
  io.wb_data := wb_data_reg
  io.rd_addr_out := rd_addr_reg
  io.reg_write_out := reg_write_reg
  io.valid_out := valid_reg
  io.mret_out := io.mret_in && io.valid_in
}
