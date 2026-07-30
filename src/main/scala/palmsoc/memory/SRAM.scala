package palmsoc.memory

import chisel3._
import chisel3.util._
import palmsoc.bus.WishboneSlave

class SRAM(addrWidth: Int, dataWidth: Int, depth: Int) extends WishboneSlave(addrWidth, dataWidth) {
  // Memory array - use Mem for combinational read
  val mem = Mem(depth, UInt(dataWidth.W))
  
  // Wishbone transaction state - single bit is enough
  val busy = RegInit(false.B)
  
  // Register for operation and address
  val opAddr = Reg(UInt(addrWidth.W))
  val opWrite = Reg(Bool())
  val opData = Reg(UInt(dataWidth.W))
  val opSel = Reg(UInt((dataWidth/8).W))
  
  // Register for read data
  val readData = Reg(UInt(dataWidth.W))
  
  // Default outputs
  io.wb.dat_r := readData
  io.wb.ack   := false.B
  io.wb.err   := false.B
  
  // Address within bounds check
  val addrInBounds = io.wb.adr < depth.U
  
  when(!busy) {
    // Idle - wait for new transaction
    when(io.wb.cyc && io.wb.stb) {
      when(!addrInBounds) {
        // Address out of bounds - signal error immediately
        io.wb.err := true.B
      }.otherwise {
        // Capture transaction
        busy := true.B
        opAddr := io.wb.adr
        opWrite := io.wb.we
        opData := io.wb.dat_w
        opSel := io.wb.sel
      }
    }
  }.otherwise {
    // Busy - process operation
    when(opWrite) {
      // Write operation
      val writeMask = VecInit(Seq.tabulate(dataWidth/8) { i =>
        Mux(opSel(i), Fill(8, 1.U(1.W)), 0.U(8.W))
      }).asUInt
      
      val oldData = mem.read(opAddr)
      val maskedWrite = (opData & writeMask) | (oldData & ~writeMask)
      mem.write(opAddr, maskedWrite)
    }.otherwise {
      // Read operation
      readData := mem.read(opAddr)
    }
    
    // Acknowledge and return to idle
    io.wb.ack := true.B
    busy := false.B
  }
  
  // Reset on end of cycle
  when(!io.wb.cyc) {
    busy := false.B
  }
}
