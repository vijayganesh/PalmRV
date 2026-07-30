package palmsoc.bus

import chisel3._ 

class WishboneIO(addrWidth: Int, dataWidth: Int) extends Bundle {
  val cyc   = Output(Bool())
  val stb   = Output(Bool())
  val we    = Output(Bool())
  val adr   = Output(UInt(addrWidth.W))
  val dat_w = Output(UInt(dataWidth.W))
  val sel   = Output(UInt((dataWidth/8).W))
  val dat_r = Input(UInt(dataWidth.W))
  val ack   = Input(Bool())
  val err   = Input(Bool())
}

abstract class WishboneMaster(addrWidth: Int, dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    val wb = new WishboneIO(addrWidth, dataWidth)
  })
  
  // Default signal initialization
  io.wb.cyc   := false.B
  io.wb.stb   := false.B
  io.wb.we    := false.B
  io.wb.adr   := 0.U
  io.wb.dat_w := 0.U
  io.wb.sel   := 0.U
}

abstract class WishboneSlave(addrWidth: Int, dataWidth: Int) extends Module {
  val io = IO(new Bundle {
    val wb = Flipped(new WishboneIO(addrWidth, dataWidth))
  })
  
  // Default signal initialization
  io.wb.dat_r := 0.U
  io.wb.ack   := false.B
  io.wb.err   := false.B
}

