package palmsoc.memory

import chisel3._
import chisel3.util._
import palmsoc.bus.{AXI4LiteSlave, AXI4LiteResp}
import palmsoc.config.AXI4LiteConfig

/**
 * SRAM with AXI4-Lite Interface
 * 
 * Single-port SRAM memory with AXI4-Lite slave interface for memory-mapped access.
 * Supports byte-addressable writes with write strobes and single-cycle read/write operations.
 * 
 * @param config AXI4LiteConfig specifying address and data widths
 * @param depth Number of memory words (each word is config.dataWidth bits)
 */
class SRAM_AXI(config: AXI4LiteConfig, depth: Int) extends AXI4LiteSlave(config) {
  // Memory array - use Mem for combinational read
  val mem = Mem(depth, UInt(config.dataWidth.W))
  
  // AXI4-Lite state machine
  val sIdle :: sWriteData :: sWriteResp :: sReadData :: Nil = Enum(4)
  val state = RegInit(sIdle)
  
  // Registers for captured address and write data
  val writeAddr = Reg(UInt(config.addrWidth.W))
  val readAddr = Reg(UInt(config.addrWidth.W))
  val readData = Reg(UInt(config.dataWidth.W))
  
  // Default outputs - all channels idle
  io.axi.awready := false.B
  io.axi.wready  := false.B
  io.axi.bresp   := AXI4LiteResp.OKAY
  io.axi.bvalid  := false.B
  io.axi.arready := false.B
  io.axi.rdata   := readData
  io.axi.rresp   := AXI4LiteResp.OKAY
  io.axi.rvalid  := false.B
  
  // Word-aligned address check
  val writeAddrAligned = (writeAddr & ((config.bytesPerWord - 1).U)) === 0.U
  val readAddrAligned = (readAddr & ((config.bytesPerWord - 1).U)) === 0.U
  
  // Address within bounds check (word-addressed)
  val writeAddrIndex = writeAddr >> log2Ceil(config.bytesPerWord)
  val readAddrIndex = readAddr >> log2Ceil(config.bytesPerWord)
  val writeInBounds = writeAddrIndex < depth.U
  val readInBounds = readAddrIndex < depth.U
  
  // State machine
  switch(state) {
    is(sIdle) {
      // Ready to accept new transactions
      // Priority: write address > read address
      when(io.axi.awvalid) {
        // Accept write address
        io.axi.awready := true.B
        writeAddr := io.axi.awaddr
        state := sWriteData
      }.elsewhen(io.axi.arvalid) {
        // Accept read address
        io.axi.arready := true.B
        readAddr := io.axi.araddr
        state := sReadData
      }
    }
    
    is(sWriteData) {
      // Wait for write data
      when(io.axi.wvalid) {
        io.axi.wready := true.B
        
        when(!writeInBounds || !writeAddrAligned) {
          // Address error - out of bounds or misaligned
          io.axi.bresp := AXI4LiteResp.SLVERR
        }.otherwise {
          // Perform masked write
          val writeMask = VecInit(Seq.tabulate(config.strbWidth) { i =>
            Mux(io.axi.wstrb(i), Fill(8, 1.U(1.W)), 0.U(8.W))
          }).asUInt
          
          val oldData = mem.read(writeAddrIndex)
          val maskedWrite = (io.axi.wdata & writeMask) | (oldData & ~writeMask)
          mem.write(writeAddrIndex, maskedWrite)
          
          io.axi.bresp := AXI4LiteResp.OKAY
        }
        
        state := sWriteResp
      }
    }
    
    is(sWriteResp) {
      // Send write response
      io.axi.bvalid := true.B
      
      when(io.axi.bready) {
        // Response accepted, return to idle
        state := sIdle
      }
    }
    
    is(sReadData) {
      // Perform read and send data
      when(!readInBounds || !readAddrAligned) {
        // Address error - out of bounds or misaligned
        io.axi.rdata := 0.U
        io.axi.rresp := AXI4LiteResp.SLVERR
      }.otherwise {
        // Read from memory
        readData := mem.read(readAddrIndex)
        io.axi.rdata := mem.read(readAddrIndex)
        io.axi.rresp := AXI4LiteResp.OKAY
      }
      
      io.axi.rvalid := true.B
      
      when(io.axi.rready) {
        // Data accepted, return to idle
        state := sIdle
      }
    }
  }
}

/**
 * SRAM_AXI Companion Object
 * 
 * Provides factory methods for common SRAM configurations
 */
object SRAM_AXI {
  /**
   * Create SRAM with default 32-bit AXI4-Lite configuration
   * 
   * @param depth Number of words
   * @return SRAM_AXI module
   */
  def apply(depth: Int): SRAM_AXI = {
    new SRAM_AXI(AXI4LiteConfig(addrWidth = 32, dataWidth = 32), depth)
  }
  
  /**
   * Create SRAM with custom configuration
   * 
   * @param config AXI4LiteConfig
   * @param depth Number of words
   * @return SRAM_AXI module
   */
  def apply(config: AXI4LiteConfig, depth: Int): SRAM_AXI = {
    new SRAM_AXI(config, depth)
  }
}
