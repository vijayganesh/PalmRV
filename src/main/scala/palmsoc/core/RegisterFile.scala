package palmsoc.core
import chisel3._



class RegisterFile extends Module {
  val io = IO(new Bundle {
    // Read ports
    val rs1_addr = Input(UInt(5.W))
    val rs2_addr = Input(UInt(5.W))
    val rs1_data = Output(UInt(32.W))
    val rs2_data = Output(UInt(32.W))
    
    // Write port
    val rd_addr = Input(UInt(5.W))
    val rd_data = Input(UInt(32.W))
    val rd_wen = Input(Bool())
  })
  
  // 32 registers, x0 is always 0
  val regs = Reg(Vec(32, UInt(32.W)))
  
  // Read ports
  io.rs1_data := Mux(io.rs1_addr === 0.U, 0.U, regs(io.rs1_addr))
  io.rs2_data := Mux(io.rs2_addr === 0.U, 0.U, regs(io.rs2_addr))
  
  // Write port
  when(io.rd_wen && io.rd_addr =/= 0.U) {
    regs(io.rd_addr) := io.rd_data
  }
}
