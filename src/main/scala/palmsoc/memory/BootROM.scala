package palmsoc.memory

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFileInline
import palmsoc.bus.{WishboneSlave, AXI4LiteSlave, AXI4LiteResp}
import palmsoc.config.AXI4LiteConfig

class BootROM(
  addrWidth: Int, 
  dataWidth: Int, 
  depth: Int,
  hexFile: Option[String] = None
) extends WishboneSlave(addrWidth, dataWidth) {
  
  require(dataWidth == 32, "Boot ROM currently supports 32-bit data width only")
  
  val rom = SyncReadMem(depth, UInt(dataWidth.W))
  if (hexFile.isDefined) {
    loadMemoryFromFileInline(rom, hexFile.get)
  }
  
  val busy = RegInit(false.B)
  val opAddr = RegInit(0.U(addrWidth.W))
  
  // Need 1 extra state for SyncReadMem latency
  val sIdle :: sWait :: sAck :: Nil = Enum(3)
  val state = RegInit(sIdle)
  
  io.wb.ack := (state === sAck)
  io.wb.err := false.B
  
  // Address within bounds check
  val addrInBounds = io.wb.adr < depth.U
  
  val readData = WireDefault(0.U(dataWidth.W))
  val safeIndex = opAddr >> log2Ceil(dataWidth/8)
  
  io.wb.dat_r := rom.read(safeIndex, state === sIdle && io.wb.cyc && io.wb.stb && !io.wb.we)
  
  switch(state) {
    is(sIdle) {
      when(io.wb.cyc && io.wb.stb) {
        opAddr := io.wb.adr
        when(!addrInBounds || io.wb.we) {
          io.wb.err := true.B
          state := sAck
        }.otherwise {
          state := sWait
        }
      }
    }
    is(sWait) {
      state := sAck
    }
    is(sAck) {
      state := sIdle
    }
  }
  
  when(!io.wb.cyc) {
    state := sIdle
  }
}

class BootROM_AXI(
  config: AXI4LiteConfig,
  depth: Int,
  hexFile: Option[String] = None
) extends AXI4LiteSlave(config) {
  
  require(config.dataWidth == 32, "Boot ROM currently supports 32-bit data width only")
  
  val rom = SyncReadMem(depth, UInt(config.dataWidth.W))
  if (hexFile.isDefined) {
    loadMemoryFromFileInline(rom, hexFile.get)
  }
  
  val sIdle :: sWaitRead :: sReadData :: sWriteData :: sWriteResp :: Nil = Enum(5)
  val state = RegInit(sIdle)
  
  val writeAddr = RegInit(0.U(config.addrWidth.W))
  val readAddr = RegInit(0.U(config.addrWidth.W))
  
  io.axi.awready := false.B
  io.axi.wready  := false.B
  io.axi.bresp   := AXI4LiteResp.SLVERR
  io.axi.bvalid  := false.B
  io.axi.arready := false.B
  io.axi.rresp   := AXI4LiteResp.OKAY
  io.axi.rvalid  := false.B
  
  val readAddrIndex = readAddr >> log2Ceil(config.bytesPerWord)
  val readInBounds = readAddrIndex < depth.U
  val readAddrAligned = (readAddr & ((config.bytesPerWord - 1).U)) === 0.U
  
  val arAddrIndex = io.axi.araddr >> log2Ceil(config.bytesPerWord)
  val safeIndex = arAddrIndex(log2Ceil(depth) - 1, 0)
  
  val romData = rom.read(safeIndex, io.axi.arvalid && state === sIdle)
  val readDataReg = RegInit(0.U(config.dataWidth.W))
  io.axi.rdata := readDataReg
  
  switch(state) {
    is(sIdle) {
      when(io.axi.awvalid) {
        io.axi.awready := true.B
        writeAddr := io.axi.awaddr
        state := sWriteData
      }.elsewhen(io.axi.arvalid) {
        io.axi.arready := true.B
        readAddr := io.axi.araddr
        state := sWaitRead
      }
    }
    
    is(sWaitRead) {
      readDataReg := romData
      state := sReadData
    }
    
    is(sWriteData) {
      when(io.axi.wvalid) {
        io.axi.wready := true.B
        io.axi.bresp := AXI4LiteResp.SLVERR
        state := sWriteResp
      }
    }
    
    is(sWriteResp) {
      io.axi.bvalid := true.B
      io.axi.bresp := AXI4LiteResp.SLVERR
      when(io.axi.bready) {
        state := sIdle
      }
    }
    
    is(sReadData) {
      when(!readInBounds || !readAddrAligned) {
        io.axi.rdata := 0.U
        io.axi.rresp := AXI4LiteResp.SLVERR
      }.otherwise {
        io.axi.rdata := readDataReg
        io.axi.rresp := AXI4LiteResp.OKAY
      }
      
      io.axi.rvalid := true.B
      
      when(io.axi.rready) {
        state := sIdle
      }
    }
  }
}

object BootROM {
  def apply(depth: Int = 16384): BootROM = new BootROM(32, 32, depth, None)
  def apply(depth: Int, hexFile: String): BootROM = new BootROM(32, 32, depth, Some(hexFile))
  def loadHexFile(filename: String): Seq[UInt] = Seq() // Stub
}

object BootROM_AXI {
  def apply(depth: Int = 16384): BootROM_AXI = new BootROM_AXI(AXI4LiteConfig(32, 32), depth, None)
  def apply(config: AXI4LiteConfig, depth: Int, hexFile: String): BootROM_AXI = new BootROM_AXI(config, depth, Some(hexFile))
  def apply(config: AXI4LiteConfig, depth: Int): BootROM_AXI = new BootROM_AXI(config, depth, None)
}
