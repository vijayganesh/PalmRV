package palmsoc.core
import chisel3._ 
import chisel3.util._

/**
 * Writeback Stage
 * 
 * Writes results back to register file.
 */
class WritebackStage extends Module {
  val io = IO(new Bundle {
    // Inputs from memory stage
    val wb_data = Input(UInt(32.W))
    val rd_addr = Input(UInt(5.W))
    val reg_write = Input(Bool())
    val valid_in = Input(Bool())
    
    // Register file write port
    val rf_waddr = Output(UInt(5.W))
    val rf_wdata = Output(UInt(32.W))
    val rf_wen = Output(Bool())
  })
  
  // Writeback to register file
  io.rf_waddr := io.rd_addr
  io.rf_wdata := io.wb_data
  io.rf_wen := io.reg_write && io.valid_in && (io.rd_addr =/= 0.U)
}
