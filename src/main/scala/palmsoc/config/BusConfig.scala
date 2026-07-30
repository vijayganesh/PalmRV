package palmsoc.config

import chisel3._
import chisel3.util._

/**
 * AXI4-Lite Bus Configuration for PALMV SoC
 * 
 * AXI4-Lite is a simplified subset of AXI4 designed for simple, low-throughput
 * memory-mapped communication. It's ideal for control registers and configuration
 * interfaces in peripherals, accelerators, and system components.
 * 
 * Key AXI4-Lite Simplifications:
 * - No burst support (all transfers are single beat)
 * - No locked or exclusive access
 * - No QoS, Region, or User signals
 * - Fixed 32-bit or 64-bit data width
 * - No write strobes interleaving
 * 
 * Use Cases in PALMV:
 * - Peripheral control registers (UART, SPI, GPIO, etc.)
 * - Accelerator configuration and status
 * - DMA controller setup
 * - Interrupt controller configuration
 * - Debug module interface
 */

/**
 * AXI4-Lite Response Codes
 */
object AXI4LiteResp {
  val OKAY   = 0.U(2.W)  // Normal access success
  val EXOKAY = 1.U(2.W)  // Exclusive access okay (not used in AXI4-Lite typically)
  val SLVERR = 2.U(2.W)  // Slave error
  val DECERR = 3.U(2.W)  // Decode error
}

/**
 * AXI4-Lite Write Address Channel
 * 
 * The write address channel carries the address and control information
 * for write transactions.
 */
class AXI4LiteWriteAddressChannel(addrWidth: Int) extends Bundle {
  val awaddr  = Output(UInt(addrWidth.W))  // Write address
  val awprot  = Output(UInt(3.W))          // Protection type (privilege, secure, instruction)
  val awvalid = Output(Bool())             // Write address valid
  val awready = Input(Bool())              // Write address ready
  
  /**
   * Protection bits encoding:
   * [0]: 0 = Unprivileged, 1 = Privileged
   * [1]: 0 = Secure, 1 = Non-secure
   * [2]: 0 = Data, 1 = Instruction
   */
  def setPrivileged(): Unit = awprot := Cat(awprot(2, 1), 1.U(1.W))
  def setUnprivileged(): Unit = awprot := Cat(awprot(2, 1), 0.U(1.W))
  def setSecure(): Unit = awprot := Cat(awprot(2), 0.U(1.W), awprot(0))
  def setNonSecure(): Unit = awprot := Cat(awprot(2), 1.U(1.W), awprot(0))
  def setData(): Unit = awprot := Cat(0.U(1.W), awprot(1, 0))
  def setInstruction(): Unit = awprot := Cat(1.U(1.W), awprot(1, 0))
}

/**
 * AXI4-Lite Write Data Channel
 * 
 * The write data channel carries the write data and byte lane strobes.
 */
class AXI4LiteWriteDataChannel(dataWidth: Int) extends Bundle {
  val wdata  = Output(UInt(dataWidth.W))           // Write data
  val wstrb  = Output(UInt((dataWidth/8).W))       // Write strobes (byte lane valid)
  val wvalid = Output(Bool())                      // Write data valid
  val wready = Input(Bool())                       // Write data ready
  
  /**
   * Helper methods for byte strobes
   */
  def setAllBytes(): Unit = wstrb := ((1 << (dataWidth/8)) - 1).U
  def clearAllBytes(): Unit = wstrb := 0.U
  def setByte(byteIndex: Int): Unit = wstrb := wstrb | (1.U << byteIndex)
  def clearByte(byteIndex: Int): Unit = wstrb := wstrb & ~(1.U << byteIndex)
}

/**
 * AXI4-Lite Write Response Channel
 * 
 * The write response channel provides the status of a write transaction.
 */
class AXI4LiteWriteResponseChannel extends Bundle {
  val bresp  = Input(UInt(2.W))   // Write response
  val bvalid = Input(Bool())      // Write response valid
  val bready = Output(Bool())     // Write response ready
  
  /**
   * Response checking helpers
   */
  def isOkay: Bool = bresp === AXI4LiteResp.OKAY
  def isError: Bool = (bresp === AXI4LiteResp.SLVERR) || (bresp === AXI4LiteResp.DECERR)
  def isSlaveError: Bool = bresp === AXI4LiteResp.SLVERR
  def isDecodeError: Bool = bresp === AXI4LiteResp.DECERR
}

/**
 * AXI4-Lite Read Address Channel
 * 
 * The read address channel carries the address and control information
 * for read transactions.
 */
class AXI4LiteReadAddressChannel(addrWidth: Int) extends Bundle {
  val araddr  = Output(UInt(addrWidth.W))  // Read address
  val arprot  = Output(UInt(3.W))          // Protection type
  val arvalid = Output(Bool())             // Read address valid
  val arready = Input(Bool())              // Read address ready
  
  /**
   * Protection bits encoding (same as write address channel)
   */
  def setPrivileged(): Unit = arprot := Cat(arprot(2, 1), 1.U(1.W))
  def setUnprivileged(): Unit = arprot := Cat(arprot(2, 1), 0.U(1.W))
  def setSecure(): Unit = arprot := Cat(arprot(2), 0.U(1.W), arprot(0))
  def setNonSecure(): Unit = arprot := Cat(arprot(2), 1.U(1.W), arprot(0))
  def setData(): Unit = arprot := Cat(0.U(1.W), arprot(1, 0))
  def setInstruction(): Unit = arprot := Cat(1.U(1.W), arprot(1, 0))
}

/**
 * AXI4-Lite Read Data Channel
 * 
 * The read data channel carries the read data and response status.
 */
class AXI4LiteReadDataChannel(dataWidth: Int) extends Bundle {
  val rdata  = Input(UInt(dataWidth.W))   // Read data
  val rresp  = Input(UInt(2.W))           // Read response
  val rvalid = Input(Bool())              // Read data valid
  val rready = Output(Bool())             // Read data ready
  
  /**
   * Response checking helpers
   */
  def isOkay: Bool = rresp === AXI4LiteResp.OKAY
  def isError: Bool = (rresp === AXI4LiteResp.SLVERR) || (rresp === AXI4LiteResp.DECERR)
  def isSlaveError: Bool = rresp === AXI4LiteResp.SLVERR
  def isDecodeError: Bool = rresp === AXI4LiteResp.DECERR
}

/**
 * AXI4-Lite Master Interface
 * 
 * Complete AXI4-Lite master interface combining all five channels.
 * Used by bus masters (CPU, DMA) to initiate transactions.
 */
class AXI4LiteMasterInterface(addrWidth: Int = 32, dataWidth: Int = 32) extends Bundle {
  require(dataWidth == 32 || dataWidth == 64, "AXI4-Lite supports 32-bit or 64-bit data width only")
  require(addrWidth > 0 && addrWidth <= 64, "Invalid address width")
  
  // Write channels
  val aw = new AXI4LiteWriteAddressChannel(addrWidth)
  val w  = new AXI4LiteWriteDataChannel(dataWidth)
  val b  = new AXI4LiteWriteResponseChannel
  
  // Read channels
  val ar = new AXI4LiteReadAddressChannel(addrWidth)
  val r  = new AXI4LiteReadDataChannel(dataWidth)
  
  /**
   * Tie off unused signals with safe defaults
   */
  def tieOff(): Unit = {
    aw.awaddr  := 0.U
    aw.awprot  := 0.U
    aw.awvalid := false.B
    w.wdata    := 0.U
    w.wstrb    := 0.U
    w.wvalid   := false.B
    b.bready   := false.B
    ar.araddr  := 0.U
    ar.arprot  := 0.U
    ar.arvalid := false.B
    r.rready   := false.B
  }
}

/**
 * AXI4-Lite Slave Interface
 * 
 * Complete AXI4-Lite slave interface (flipped master).
 * Used by peripherals, memories, and other slaves to respond to transactions.
 */
class AXI4LiteSlaveInterface(addrWidth: Int = 32, dataWidth: Int = 32) extends Bundle {
  require(dataWidth == 32 || dataWidth == 64, "AXI4-Lite supports 32-bit or 64-bit data width only")
  require(addrWidth > 0 && addrWidth <= 64, "Invalid address width")
  
  // Write channels (flipped from master perspective)
  val aw = Flipped(new AXI4LiteWriteAddressChannel(addrWidth))
  val w  = Flipped(new AXI4LiteWriteDataChannel(dataWidth))
  val b  = Flipped(new AXI4LiteWriteResponseChannel)
  
  // Read channels (flipped from master perspective)
  val ar = Flipped(new AXI4LiteReadAddressChannel(addrWidth))
  val r  = Flipped(new AXI4LiteReadDataChannel(dataWidth))
  
  /**
   * Tie off unused signals with safe defaults (slave not responding)
   */
  def tieOff(): Unit = {
    aw.awready := false.B
    w.wready   := false.B
    b.bresp    := AXI4LiteResp.DECERR
    b.bvalid   := false.B
    ar.arready := false.B
    r.rdata    := 0.U
    r.rresp    := AXI4LiteResp.DECERR
    r.rvalid   := false.B
  }
  
  /**
   * Generate decode error response for unmapped addresses
   */
  def genDecodeError(): Unit = {
    b.bresp := AXI4LiteResp.DECERR
    r.rresp := AXI4LiteResp.DECERR
  }
  
  /**
   * Generate slave error response for invalid accesses
   */
  def genSlaveError(): Unit = {
    b.bresp := AXI4LiteResp.SLVERR
    r.rresp := AXI4LiteResp.SLVERR
  }
  
  /**
   * Generate okay response for successful accesses
   */
  def genOkayResponse(): Unit = {
    b.bresp := AXI4LiteResp.OKAY
    r.rresp := AXI4LiteResp.OKAY
  }
}

/**
 * AXI4-Lite Configuration Parameters
 * 
 * Consolidates configuration for the AXI4-Lite bus in PALMV SoC
 */
case class AXI4LiteConfig(
  addrWidth: Int = 32,
  dataWidth: Int = 32
) {
  require(dataWidth == 32 || dataWidth == 64, "AXI4-Lite supports 32-bit or 64-bit data width only")
  require(addrWidth > 0 && addrWidth <= 64, "Invalid address width")
  
  // Derived parameters
  val strbWidth = dataWidth / 8
  val bytesPerWord = dataWidth / 8
  
  /**
   * Address alignment check
   */
  def isAligned(addr: UInt): Bool = {
    addr(log2Ceil(bytesPerWord) - 1, 0) === 0.U
  }
  
  /**
   * Validate address is within 32-bit space
   */
  def isValid32BitAddr(addr: UInt): Bool = {
    if (addrWidth > 32) addr(addrWidth - 1, 32) === 0.U
    else true.B
  }
}

/**
 * Default AXI4-Lite Configuration for PALMV
 */
object DefaultAXI4LiteConfig {
  def apply(): AXI4LiteConfig = AXI4LiteConfig(
    addrWidth = 32,
    dataWidth = 32
  )
}

/**
 * AXI4-Lite Transaction Helper
 * 
 * Provides utilities for common AXI4-Lite transaction patterns
 */
object AXI4LiteTransaction {
  /**
   * Generate write strobes for different data sizes
   */
  def byteStrobe(offset: UInt): UInt = {
    1.U << offset
  }
  
  def halfWordStrobe(offset: UInt): UInt = {
    Mux(offset(0), 0xC.U(4.W), 0x3.U(4.W))
  }
  
  def wordStrobe(): UInt = {
    0xF.U(4.W)
  }
  
  /**
   * Align data based on byte offset
   */
  def alignWriteData(data: UInt, offset: UInt, size: UInt): UInt = {
    val shamt = offset << 3  // Multiply by 8 for bit shift
    data << shamt
  }
  
  def alignReadData(data: UInt, offset: UInt, size: UInt): UInt = {
    val shamt = offset << 3  // Multiply by 8 for bit shift
    data >> shamt
  }
  
  /**
   * Common protection settings
   */
  def protPrivilegedData: UInt = 0x1.U(3.W)      // Privileged, Secure, Data
  def protUnprivilegedData: UInt = 0x0.U(3.W)    // Unprivileged, Secure, Data
  def protPrivilegedInstr: UInt = 0x5.U(3.W)     // Privileged, Secure, Instruction
  def protNonSecureData: UInt = 0x2.U(3.W)       // Unprivileged, Non-secure, Data
}

/**
 * AXI4-Lite Crossbar Configuration
 * 
 * Defines the interconnect topology for PALMV SoC
 */
case class AXI4LiteCrossbarConfig(
  numMasters: Int,
  numSlaves: Int,
  addrWidth: Int = 32,
  dataWidth: Int = 32,
  // Slave address ranges (base, size pairs)
  slaveAddressMap: Seq[(Long, Long)] = Seq()
) {
  require(numMasters > 0 && numMasters <= 16, "Support 1-16 masters")
  require(numSlaves > 0 && numSlaves <= 32, "Support 1-32 slaves")
  require(slaveAddressMap.isEmpty || slaveAddressMap.length == numSlaves, 
          "Address map must match number of slaves")
  
  // Validate address ranges don't overlap
  if (slaveAddressMap.nonEmpty) {
    for (i <- slaveAddressMap.indices) {
      val (base1, size1) = slaveAddressMap(i)
      require(size1 > 0, s"Slave $i size must be positive")
      for (j <- i + 1 until slaveAddressMap.length) {
        val (base2, size2) = slaveAddressMap(j)
        val overlap = (base1 < base2 + size2) && (base2 < base1 + size1)
        require(!overlap, s"Address ranges overlap between slave $i and slave $j")
      }
    }
  }
}

/**
 * PALMV SoC Default Crossbar Configuration
 * 
 * Based on the address space defined in address_space.md and SoCConfig.scala
 */
object PalmVCrossbarConfig {
  /**
   * Main system crossbar configuration
   * Masters: CPU, DMA
   * Slaves: Boot ROM, Flash, SRAM, Peripherals, Accelerators, DMA, Interrupts, Debug
   */
  def apply(): AXI4LiteCrossbarConfig = {
    import palmsoc.config.MemoryMap._
    
    AXI4LiteCrossbarConfig(
      numMasters = 2,  // CPU, DMA
      numSlaves = 8,   // Major regions (excluding reserved)
      addrWidth = 32,
      dataWidth = 32,
      slaveAddressMap = Seq(
        (BOOTROM_BASE, BOOTROM_SIZE),         // Slave 0: Boot ROM
        (FLASH_BASE, FLASH_SIZE),             // Slave 1: Flash
        (SRAM_BASE, SRAM_SIZE),               // Slave 2: SRAM
        (PERIPHERAL_BASE, PERIPHERAL_SIZE),   // Slave 3: Peripherals
        (ACCELERATOR_BASE, ACCELERATOR_SIZE), // Slave 4: Accelerators
        (DMA_BASE, DMA_SIZE),                 // Slave 5: DMA
        (INTERRUPT_BASE, INTERRUPT_SIZE),     // Slave 6: Interrupts
        (DEBUG_BASE, DEBUG_SIZE)              // Slave 7: Debug
      )
    )
  }
  
  /**
   * Peripheral sub-crossbar configuration
   * All peripherals share the peripheral address space
   */
  def peripheralCrossbar(): AXI4LiteCrossbarConfig = {
    import palmsoc.config.PeripheralMap._
    
    AXI4LiteCrossbarConfig(
      numMasters = 1,  // From main crossbar
      numSlaves = 10,  // UART0/1, SPI0/1, I2C0/1, GPIO, PWM, ADC, Timer
      addrWidth = 32,
      dataWidth = 32,
      slaveAddressMap = Seq(
        (UART0_BASE, UART_SIZE),
        (UART1_BASE, UART_SIZE),
        (SPI0_BASE, SPI_SIZE),
        (SPI1_BASE, SPI_SIZE),
        (I2C0_BASE, I2C_SIZE),
        (I2C1_BASE, I2C_SIZE),
        (GPIO_BASE, GPIO_SIZE),
        (PWM_BASE, PWM_SIZE),
        (ADC_BASE, ADC_SIZE),
        (TIMER_BASE, TIMER_SIZE)
      )
    )
  }
  
  /**
   * Accelerator sub-crossbar configuration
   */
  def acceleratorCrossbar(): AXI4LiteCrossbarConfig = {
    import palmsoc.config.AcceleratorMap._
    
    AXI4LiteCrossbarConfig(
      numMasters = 1,  // From main crossbar
      numSlaves = 7,   // LSTM, CNN, AES, SHA, DSP, FFT, Matrix
      addrWidth = 32,
      dataWidth = 32,
      slaveAddressMap = Seq(
        (LSTM_BASE, LSTM_SIZE),
        (CNN_BASE, CNN_SIZE),
        (AES_BASE, AES_SIZE),
        (SHA_BASE, SHA_SIZE),
        (DSP_BASE, DSP_SIZE),
        (FFT_BASE, FFT_SIZE),
        (MATRIX_BASE, MATRIX_SIZE)
      )
    )
  }
}
