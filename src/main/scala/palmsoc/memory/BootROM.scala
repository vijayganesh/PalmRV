package palmsoc.memory

import chisel3._
import chisel3.util._
import palmsoc.bus.{WishboneSlave, AXI4LiteSlave, AXI4LiteResp}
import palmsoc.config.AXI4LiteConfig

/**
 * Boot ROM with Wishbone Interface
 * 
 * Read-only memory containing boot code and initialization routines.
 * Typically mapped at address 0x0000_0000 (reset vector location).
 * 
 * Features:
 * - Read-only access (writes are ignored/error)
 * - Single-cycle read latency
 * - Configurable depth
 * - Optional initialization from file or sequence
 * 
 * @param addrWidth Address bus width in bits
 * @param dataWidth Data bus width in bits (must be 32)
 * @param depth Number of words in ROM
 * @param initContent Optional initial content for ROM
 */
class BootROM(
  addrWidth: Int, 
  dataWidth: Int, 
  depth: Int,
  initContent: Option[Seq[UInt]] = None
) extends WishboneSlave(addrWidth, dataWidth) {
  
  require(dataWidth == 32, "Boot ROM currently supports 32-bit data width only")
  
  // ROM array - use VecInit for read-only memory with initialization
  val rom = if (initContent.isDefined) {
    VecInit(initContent.get.padTo(depth, 0.U(dataWidth.W)))
  } else {
    // Default boot code: simple infinite loop at reset vector
    // This prevents undefined behavior if ROM is not initialized
    val defaultCode = Seq(
      0x0000006fL.U,  // j . (infinite loop: JAL x0, 0)
      0x00000013L.U,  // nop (ADDI x0, x0, 0)
      0x00000013L.U,  // nop
      0x00000013L.U   // nop
    )
    VecInit(defaultCode.padTo(depth, 0.U(dataWidth.W)))
  }
  
  // Wishbone transaction state
  val busy = RegInit(false.B)
  val opAddr = Reg(UInt(addrWidth.W))
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
      }.elsewhen(io.wb.we) {
        // Write attempt to ROM - signal error
        io.wb.err := true.B
      }.otherwise {
        // Valid read - capture transaction
        busy := true.B
        opAddr := io.wb.adr
      }
    }
  }.otherwise {
    // Busy - process read operation
    readData := rom(opAddr)
    
    // Acknowledge and return to idle
    io.wb.ack := true.B
    busy := false.B
  }
  
  // Reset on end of cycle
  when(!io.wb.cyc) {
    busy := false.B
  }
}

/**
 * Boot ROM with AXI4-Lite Interface
 * 
 * Read-only memory with AXI4-Lite slave interface.
 * Writes return SLVERR response.
 * 
 * @param config AXI4LiteConfig specifying address and data widths
 * @param depth Number of words in ROM
 * @param initContent Optional initial content for ROM
 */
class BootROM_AXI(
  config: AXI4LiteConfig,
  depth: Int,
  initContent: Option[Seq[UInt]] = None
) extends AXI4LiteSlave(config) {
  
  require(config.dataWidth == 32, "Boot ROM currently supports 32-bit data width only")
  
  // ROM array with initialization
  val rom = if (initContent.isDefined) {
    VecInit(initContent.get.padTo(depth, 0.U(config.dataWidth.W)))
  } else {
    // Default boot code: simple infinite loop at reset vector
    val defaultCode = Seq(
      0x0000006fL.U,  // j . (infinite loop: JAL x0, 0)
      0x00000013L.U,  // nop (ADDI x0, x0, 0)
      0x00000013L.U,  // nop
      0x00000013L.U   // nop
    )
    VecInit(defaultCode.padTo(depth, 0.U(config.dataWidth.W)))
  }
  
  // AXI4-Lite state machine
  val sIdle :: sWriteData :: sWriteResp :: sReadData :: Nil = Enum(4)
  val state = RegInit(sIdle)
  
  // Registers for captured addresses (initialize to 0)
  val writeAddr = RegInit(0.U(config.addrWidth.W))
  val readAddr = RegInit(0.U(config.addrWidth.W))
  val readData = RegInit(0.U(config.dataWidth.W))
  
  // Default outputs - all channels idle
  io.axi.awready := false.B
  io.axi.wready  := false.B
  io.axi.bresp   := AXI4LiteResp.SLVERR  // ROM is read-only
  io.axi.bvalid  := false.B
  io.axi.arready := false.B
  io.axi.rdata   := readData
  io.axi.rresp   := AXI4LiteResp.OKAY
  io.axi.rvalid  := false.B
  
  // Word-aligned address check (use registered values for current operation)
  val writeAddrAligned = (writeAddr & ((config.bytesPerWord - 1).U)) === 0.U
  val readAddrAligned = (readAddr & ((config.bytesPerWord - 1).U)) === 0.U
  
  // Address within bounds check (word-addressed)
  val writeAddrIndex = writeAddr >> log2Ceil(config.bytesPerWord)
  val readAddrIndex = readAddr >> log2Ceil(config.bytesPerWord)
  val writeInBounds = writeAddrIndex < depth.U
  val readInBounds = readAddrIndex < depth.U
  
  // Incoming address checks (for early validation in Idle state)
  val arAddrAligned = (io.axi.araddr & ((config.bytesPerWord - 1).U)) === 0.U
  val arAddrIndex = io.axi.araddr >> log2Ceil(config.bytesPerWord)
  val arInBounds = arAddrIndex < depth.U
// Debug: print current state
when(state =/= RegNext(state)) {
    //printf(p"[BootROM_AXI] State transition: ${RegNext(state)} -> ${state}\n")
}
  // Memory access signals are no longer needed
  
  // State machine
  switch(state) {
    is(sIdle) {
      // Ready to accept new transactions
      // Priority: write address > read address (though writes will error)
      when(io.axi.awvalid) {
        // Accept write address (will return error)
        io.axi.awready := true.B
        writeAddr := io.axi.awaddr
        state := sWriteData
      }.elsewhen(io.axi.arvalid) {
        // Accept read address
        io.axi.arready := true.B
        readAddr := io.axi.araddr
        
        // Capture ROM data synchronously to break combinatorial AXI path
        val arAddrIndex = io.axi.araddr >> log2Ceil(config.bytesPerWord)
        val safeIndex = arAddrIndex(log2Ceil(depth) - 1, 0)
        readData := rom(safeIndex)
        
        state := sReadData
      }
    }
    
    is(sWriteData) {
      // Wait for write data (but ROM is read-only)
      when(io.axi.wvalid) {
        io.axi.wready := true.B
        // ROM is read-only - always return error
        io.axi.bresp := AXI4LiteResp.SLVERR
        state := sWriteResp
      }
    }
    
    is(sWriteResp) {
      // Send write error response
      io.axi.bvalid := true.B
      io.axi.bresp := AXI4LiteResp.SLVERR
      
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
        // Send the synchronously captured data
        io.axi.rdata := readData
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
 * Boot ROM Companion Object
 * 
 * Provides factory methods and utilities for Boot ROM creation
 */
object BootROM {
  /**
   * Create Wishbone Boot ROM with default configuration
   * 
   * @param depth Number of words (default: 16K words = 64KB)
   * @return BootROM module
   */
  def apply(depth: Int = 16384): BootROM = {
    new BootROM(addrWidth = 32, dataWidth = 32, depth, None)
  }
  
  /**
   * Create Wishbone Boot ROM with initialization
   * 
   * @param depth Number of words
   * @param initContent Initial ROM content
   * @return BootROM module
   */
  def apply(depth: Int, initContent: Seq[UInt]): BootROM = {
    new BootROM(addrWidth = 32, dataWidth = 32, depth, Some(initContent))
  }
  
  /**
   * Load ROM content from hex file (utility for future use)
   * Format: One 32-bit hex value per line (e.g., "deadbeef")
   */
  def loadHexFile(filename: String): Seq[UInt] = {
    val source = scala.io.Source.fromFile(filename)
    try {
      source.getLines().map { line =>
        val trimmed = line.trim
        if (trimmed.isEmpty || trimmed.startsWith("#") || trimmed.startsWith("//")) {
          0.U(32.W)
        } else {
          ("h" + trimmed).U(32.W)
        }
      }.toSeq
    } finally {
      source.close()
    }
  }
  
  /**
   * Generate simple boot code sequence
   */
  def defaultBootCode(): Seq[UInt] = Seq(
    0x0000006fL.U,  // 0x00: j . (infinite loop at reset vector)
    0x00000013L.U,  // 0x04: nop
    0x00000013L.U,  // 0x08: nop
    0x00000013L.U,  // 0x0C: nop
    // Could add more boot code here
  )
}

/**
 * Boot ROM AXI Companion Object
 */
object BootROM_AXI {
  /**
   * Create AXI Boot ROM with default 32-bit configuration
   * 
   * @param depth Number of words (default: 16K words = 64KB)
   * @return BootROM_AXI module
   */
  def apply(depth: Int = 16384): BootROM_AXI = {
    new BootROM_AXI(AXI4LiteConfig(addrWidth = 32, dataWidth = 32), depth, None)
  }
  
  /**
   * Create AXI Boot ROM with initialization
   * 
   * @param config AXI4LiteConfig
   * @param depth Number of words
   * @param initContent Initial ROM content
   * @return BootROM_AXI module
   */
  def apply(config: AXI4LiteConfig, depth: Int, initContent: Seq[UInt]): BootROM_AXI = {
    new BootROM_AXI(config, depth, Some(initContent))
  }
  
  /**
   * Create AXI Boot ROM with custom config
   * 
   * @param config AXI4LiteConfig
   * @param depth Number of words
   * @return BootROM_AXI module
   */
  def apply(config: AXI4LiteConfig, depth: Int): BootROM_AXI = {
    new BootROM_AXI(config, depth, None)
  }
}