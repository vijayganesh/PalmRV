package palmsoc.memory

import chisel3._
import chisel3.util._
import palmsoc.bus.{AXI4LiteSlave, AXI4LiteResp}
import palmsoc.config.AXI4LiteConfig

/**
 * SRAM with AXI4-Lite Interface
 * 
 * Synchronous BRAM memory with AXI4-Lite slave interface.
 * Uses SyncReadMem to map to FPGA Block RAMs.
 */
class SRAM_AXI(config: AXI4LiteConfig, depth: Int) extends AXI4LiteSlave(config) {
  // Memory array - use SyncReadMem for BRAM mapping, with byte enables
  val mem = SyncReadMem(depth, Vec(config.strbWidth, UInt(8.W)))
  
  // AXI4-Lite Write state machine
  val sWriteIdle :: sWriteData :: sWriteResp :: Nil = Enum(3)
  val writeState = RegInit(sWriteIdle)
  
  // AXI4-Lite Read state machine
  val sReadIdle :: sReadData :: Nil = Enum(2)
  val readState = RegInit(sReadIdle)
  
  // Registers for captured address and responses
  val writeAddr = Reg(UInt(config.addrWidth.W))
  val readAddr = Reg(UInt(config.addrWidth.W))
  val bresp_reg = RegInit(AXI4LiteResp.OKAY)
  
  // Default outputs
  io.axi.awready := false.B
  io.axi.wready  := false.B
  io.axi.bresp   := bresp_reg
  io.axi.bvalid  := false.B
  
  io.axi.arready := false.B
  io.axi.rresp   := AXI4LiteResp.OKAY
  io.axi.rvalid  := false.B
  
  // Memory access signals
  val do_read = WireDefault(false.B)
  val read_addr_wire = WireDefault(readAddr)
  
  // BRAM Read Logic
  val read_addr_index = read_addr_wire >> log2Ceil(config.bytesPerWord)
  val mem_out = mem.read(read_addr_index, do_read)
  
  // Latch the output for when AXI stall occurs
  val readDataLatched = Reg(UInt(config.dataWidth.W))
  val is_mem_out_valid = RegNext(do_read, false.B)
  when(is_mem_out_valid) {
    readDataLatched := mem_out.asUInt
  }
  io.axi.rdata := Mux(is_mem_out_valid, mem_out.asUInt, readDataLatched)
  
  // Write State Machine
  switch(writeState) {
    is(sWriteIdle) {
      when(io.axi.awvalid) {
        io.axi.awready := true.B
        writeAddr := io.axi.awaddr
        writeState := sWriteData
      }
    }
    
    is(sWriteData) {
      when(io.axi.wvalid) {
        io.axi.wready := true.B
        
        val writeAddrAligned = (writeAddr & ((config.bytesPerWord - 1).U)) === 0.U
        val writeAddrIndex = writeAddr >> log2Ceil(config.bytesPerWord)
        val writeInBounds = writeAddrIndex < depth.U
        
        when(!writeInBounds || !writeAddrAligned) {
          bresp_reg := AXI4LiteResp.SLVERR
        }.otherwise {
          // Perform synchronous masked write directly to BRAM
          val writeDataVec = VecInit(Seq.tabulate(config.strbWidth) { i =>
            io.axi.wdata(i * 8 + 7, i * 8)
          })
          val writeMaskVec = VecInit(Seq.tabulate(config.strbWidth) { i =>
            io.axi.wstrb(i)
          })
          mem.write(writeAddrIndex, writeDataVec, writeMaskVec)
          bresp_reg := AXI4LiteResp.OKAY
        }
        
        writeState := sWriteResp
      }
    }
    
    is(sWriteResp) {
      io.axi.bvalid := true.B
      when(io.axi.bready) {
        writeState := sWriteIdle
      }
    }
  }

  // Read State Machine
  switch(readState) {
    is(sReadIdle) {
      when(io.axi.arvalid) {
        io.axi.arready := true.B
        readAddr := io.axi.araddr
        read_addr_wire := io.axi.araddr
        do_read := true.B
        readState := sReadData
      }
    }
    
    is(sReadData) {
      val readAddrAligned = (readAddr & ((config.bytesPerWord - 1).U)) === 0.U
      val readInBounds = (readAddr >> log2Ceil(config.bytesPerWord)) < depth.U
      
      when(!readInBounds || !readAddrAligned) {
        io.axi.rdata := 0.U
        io.axi.rresp := AXI4LiteResp.SLVERR
      }
      
      io.axi.rvalid := true.B
      
      when(io.axi.rready) {
        readState := sReadIdle
      }
    }
  }
}

object SRAM_AXI {
  def apply(depth: Int): SRAM_AXI = {
    new SRAM_AXI(AXI4LiteConfig(addrWidth = 32, dataWidth = 32), depth)
  }
  
  def apply(config: AXI4LiteConfig, depth: Int): SRAM_AXI = {
    new SRAM_AXI(config, depth)
  }
}
