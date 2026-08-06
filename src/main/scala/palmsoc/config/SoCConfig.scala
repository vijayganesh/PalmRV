package palmsoc.config

import chisel3._

/**
 * PALMV SoC Address Space Configuration
 * 
 * Defines the complete memory map and address space allocation
 * for the PALMV RISC-V SoC based on the address_space.md specification.
 * 
 * Address Space Overview:
 * - Boot ROM:     0x0000_0000 - 0x0000_FFFF (64 KB)
 * - Flash:        0x1000_0000 - 0x1FFF_FFFF (256 MB)
 * - SRAM:         0x2000_0000 - 0x2FFF_FFFF (256 MB)
 * - Peripherals:  0x3000_0000 - 0x3FFF_FFFF (256 MB)
 * - Accelerators: 0x4000_0000 - 0x4FFF_FFFF (256 MB)
 * - DMA:          0x5000_0000 - 0x5000_FFFF (64 KB)
 * - CLINT/CLIC:   0x6000_0000 - 0x6000_FFFF (64 KB)
 * - Debug:        0x7000_0000 - 0x7000_FFFF (64 KB)
 * - Reserved:     0x8000_0000 - 0xFFFF_FFFF (2 GB)
 */
object MemoryRegion extends Enumeration {
  val BootROM, Flash, SRAM, Peripheral, Accelerator, DMA, Interrupt, Debug, Reserved = Value
}

/**
 * Base addresses for major memory regions
 */
object MemoryMap {
  // Major Region Base Addresses
  val BOOTROM_BASE    = 0x00000000L
  val FLASH_BASE      = 0x10000000L
  val SRAM_BASE       = 0x20000000L
  val PERIPHERAL_BASE = 0x30000000L
  val ACCELERATOR_BASE= 0x40000000L
  val DMA_BASE        = 0x50000000L
  val INTERRUPT_BASE  = 0x60000000L
  val DEBUG_BASE      = 0x70000000L
  val RESERVED_BASE   = 0x80000000L
  
  // Region Sizes
  val BOOTROM_SIZE    = 0x00020000L  // 128 KB
  val FLASH_SIZE      = 0x10000000L  // 256 MB
  val SRAM_SIZE       = 0x10000000L  // 256 MB
  val PERIPHERAL_SIZE = 0x10000000L  // 256 MB
  val ACCELERATOR_SIZE= 0x10000000L  // 256 MB
  val DMA_SIZE        = 0x00010000L  // 64 KB
  val INTERRUPT_SIZE  = 0x00010000L  // 64 KB
  val DEBUG_SIZE      = 0x00010000L  // 64 KB
  val RESERVED_SIZE   = 0x80000000L  // 2 GB
  
  // Region End Addresses (inclusive)
  val BOOTROM_END     = BOOTROM_BASE + BOOTROM_SIZE - 1
  val FLASH_END       = FLASH_BASE + FLASH_SIZE - 1
  val SRAM_END        = SRAM_BASE + SRAM_SIZE - 1
  val PERIPHERAL_END  = PERIPHERAL_BASE + PERIPHERAL_SIZE - 1
  val ACCELERATOR_END = ACCELERATOR_BASE + ACCELERATOR_SIZE - 1
  val DMA_END         = DMA_BASE + DMA_SIZE - 1
  val INTERRUPT_END   = INTERRUPT_BASE + INTERRUPT_SIZE - 1
  val DEBUG_END       = DEBUG_BASE + DEBUG_SIZE - 1
  val RESERVED_END    = 0xFFFFFFFFL
  
  /**
   * Check if an address falls within a specific region
   */
  def inRegion(addr: UInt, base: Long, size: Long): Bool = {
    val addrVal = addr.asUInt
    (addrVal >= base.U) && (addrVal < (base + size).U)
  }
  
  def inBootROM(addr: UInt): Bool = inRegion(addr, BOOTROM_BASE, BOOTROM_SIZE)
  def inFlash(addr: UInt): Bool = inRegion(addr, FLASH_BASE, FLASH_SIZE)
  def inSRAM(addr: UInt): Bool = inRegion(addr, SRAM_BASE, SRAM_SIZE)
  def inPeripheral(addr: UInt): Bool = inRegion(addr, PERIPHERAL_BASE, PERIPHERAL_SIZE)
  def inAccelerator(addr: UInt): Bool = inRegion(addr, ACCELERATOR_BASE, ACCELERATOR_SIZE)
  def inDMA(addr: UInt): Bool = inRegion(addr, DMA_BASE, DMA_SIZE)
  def inInterrupt(addr: UInt): Bool = inRegion(addr, INTERRUPT_BASE, INTERRUPT_SIZE)
  def inDebug(addr: UInt): Bool = inRegion(addr, DEBUG_BASE, DEBUG_SIZE)
  def inReserved(addr: UInt): Bool = inRegion(addr, RESERVED_BASE, RESERVED_SIZE)
}

/**
 * Peripheral device addresses within Peripheral Region
 * Base: 0x3000_0000 - 0x3FFF_FFFF
 */
object PeripheralMap {
  val PERIPHERAL_BASE = MemoryMap.PERIPHERAL_BASE
  
  // UART Controllers
  val UART0_BASE      = PERIPHERAL_BASE + 0x00000000L  // 0x3000_0000
  val UART1_BASE      = PERIPHERAL_BASE + 0x00001000L  // 0x3000_1000
  val UART_SIZE       = 0x1000L  // 4 KB per UART
  
  // SPI Controllers
  val SPI0_BASE       = PERIPHERAL_BASE + 0x00010000L  // 0x3001_0000
  val SPI1_BASE       = PERIPHERAL_BASE + 0x00011000L  // 0x3001_1000
  val SPI_SIZE        = 0x1000L  // 4 KB per SPI
  
  // I2C Controllers
  val I2C0_BASE       = PERIPHERAL_BASE + 0x00020000L  // 0x3002_0000
  val I2C1_BASE       = PERIPHERAL_BASE + 0x00021000L  // 0x3002_1000
  val I2C_SIZE        = 0x1000L  // 4 KB per I2C
  
  // GPIO Controller
  val GPIO_BASE       = PERIPHERAL_BASE + 0x00030000L  // 0x3003_0000
  val GPIO_SIZE       = 0x1000L  // 4 KB
  
  // PWM Controller
  val PWM_BASE        = PERIPHERAL_BASE + 0x00040000L  // 0x3004_0000
  val PWM_SIZE        = 0x1000L  // 4 KB
  
  // ADC Controller
  val ADC_BASE        = PERIPHERAL_BASE + 0x00050000L  // 0x3005_0000
  val ADC_SIZE        = 0x1000L  // 4 KB
  
  // Timer Controller
  val TIMER_BASE      = PERIPHERAL_BASE + 0x00060000L  // 0x3006_0000
  val TIMER_SIZE      = 0x1000L  // 4 KB
  
  /**
   * Helper functions to check peripheral address ranges
   */
  def inUART0(addr: UInt): Bool = MemoryMap.inRegion(addr, UART0_BASE, UART_SIZE)
  def inUART1(addr: UInt): Bool = MemoryMap.inRegion(addr, UART1_BASE, UART_SIZE)
  def inSPI0(addr: UInt): Bool = MemoryMap.inRegion(addr, SPI0_BASE, SPI_SIZE)
  def inSPI1(addr: UInt): Bool = MemoryMap.inRegion(addr, SPI1_BASE, SPI_SIZE)
  def inI2C0(addr: UInt): Bool = MemoryMap.inRegion(addr, I2C0_BASE, I2C_SIZE)
  def inI2C1(addr: UInt): Bool = MemoryMap.inRegion(addr, I2C1_BASE, I2C_SIZE)
  def inGPIO(addr: UInt): Bool = MemoryMap.inRegion(addr, GPIO_BASE, GPIO_SIZE)
  def inPWM(addr: UInt): Bool = MemoryMap.inRegion(addr, PWM_BASE, PWM_SIZE)
  def inADC(addr: UInt): Bool = MemoryMap.inRegion(addr, ADC_BASE, ADC_SIZE)
  def inTimer(addr: UInt): Bool = MemoryMap.inRegion(addr, TIMER_BASE, TIMER_SIZE)
}

/**
 * Hardware Accelerator addresses within Accelerator Region
 * Base: 0x4000_0000 - 0x4FFF_FFFF
 */
object AcceleratorMap {
  val ACCELERATOR_BASE = MemoryMap.ACCELERATOR_BASE
  
  // Neural Network Accelerators
  val LSTM_BASE       = ACCELERATOR_BASE + 0x00000000L  // 0x4000_0000
  val LSTM_SIZE       = 0x10000L  // 64 KB
  
  val CNN_BASE        = ACCELERATOR_BASE + 0x00010000L  // 0x4001_0000
  val CNN_SIZE        = 0x10000L  // 64 KB
  
  // Cryptographic Accelerators
  val AES_BASE        = ACCELERATOR_BASE + 0x00020000L  // 0x4002_0000
  val AES_SIZE        = 0x4000L   // 16 KB
  
  val SHA_BASE        = ACCELERATOR_BASE + 0x00024000L  // 0x4002_4000
  val SHA_SIZE        = 0x4000L   // 16 KB
  
  // DSP Accelerators
  val DSP_BASE        = ACCELERATOR_BASE + 0x00030000L  // 0x4003_0000
  val DSP_SIZE        = 0x10000L  // 64 KB
  
  val FFT_BASE        = ACCELERATOR_BASE + 0x00040000L  // 0x4004_0000
  val FFT_SIZE        = 0x10000L  // 64 KB
  
  val MATRIX_BASE     = ACCELERATOR_BASE + 0x00050000L  // 0x4005_0000
  val MATRIX_SIZE     = 0x10000L  // 64 KB
  
  /**
   * Helper functions to check accelerator address ranges
   */
  def inLSTM(addr: UInt): Bool = MemoryMap.inRegion(addr, LSTM_BASE, LSTM_SIZE)
  def inCNN(addr: UInt): Bool = MemoryMap.inRegion(addr, CNN_BASE, CNN_SIZE)
  def inAES(addr: UInt): Bool = MemoryMap.inRegion(addr, AES_BASE, AES_SIZE)
  def inSHA(addr: UInt): Bool = MemoryMap.inRegion(addr, SHA_BASE, SHA_SIZE)
  def inDSP(addr: UInt): Bool = MemoryMap.inRegion(addr, DSP_BASE, DSP_SIZE)
  def inFFT(addr: UInt): Bool = MemoryMap.inRegion(addr, FFT_BASE, FFT_SIZE)
  def inMatrix(addr: UInt): Bool = MemoryMap.inRegion(addr, MATRIX_BASE, MATRIX_SIZE)
}

/**
 * DMA Controller addresses within DMA Region
 * Base: 0x5000_0000 - 0x5000_FFFF (64 KB)
 */
object DMAMap {
  val DMA_BASE        = MemoryMap.DMA_BASE
  val DMA_SIZE        = MemoryMap.DMA_SIZE
  
  // DMA Channel Offsets (4 KB per channel, 4 channels)
  val CHANNEL_SIZE    = 0x1000L  // 4 KB per channel
  val NUM_CHANNELS    = 4
  
  def channelBase(ch: Int): Long = {
    require(ch >= 0 && ch < NUM_CHANNELS, s"Invalid DMA channel: $ch")
    DMA_BASE + (ch * CHANNEL_SIZE)
  }
  
  val CHANNEL0_BASE   = channelBase(0)  // 0x5000_0000
  val CHANNEL1_BASE   = channelBase(1)  // 0x5000_1000
  val CHANNEL2_BASE   = channelBase(2)  // 0x5000_2000
  val CHANNEL3_BASE   = channelBase(3)  // 0x5000_3000
  
  // Global DMA Control Registers
  val GLOBAL_CTRL_BASE= DMA_BASE + 0xF000L  // 0x5000_F000
  val GLOBAL_CTRL_SIZE= 0x1000L  // 4 KB
  
  /**
   * Helper functions
   */
  def inDMAChannel(addr: UInt, ch: Int): Bool = {
    MemoryMap.inRegion(addr, channelBase(ch), CHANNEL_SIZE)
  }
  
  def inGlobalCtrl(addr: UInt): Bool = {
    MemoryMap.inRegion(addr, GLOBAL_CTRL_BASE, GLOBAL_CTRL_SIZE)
  }
}

/**
 * Interrupt Controller addresses within Interrupt Region
 * Base: 0x6000_0000 - 0x6000_FFFF (64 KB)
 */
object InterruptMap {
  val INTERRUPT_BASE  = MemoryMap.INTERRUPT_BASE
  val INTERRUPT_SIZE  = MemoryMap.INTERRUPT_SIZE
  
  // CLINT (Core-Local Interruptor)
  val CLINT_BASE      = INTERRUPT_BASE + 0x0000L  // 0x6000_0000
  val CLINT_SIZE      = 0x8000L  // 32 KB
  
  // CLINT Register Offsets
  val MSIP_BASE       = CLINT_BASE + 0x0000L      // Machine Software Interrupt Pending
  val MTIMECMP_BASE   = CLINT_BASE + 0x4000L      // Machine Timer Compare
  val MTIME_BASE      = CLINT_BASE + 0xBFF8L      // Machine Time
  
  // CLIC (Core-Local Interrupt Controller)
  val CLIC_BASE       = INTERRUPT_BASE + 0x8000L  // 0x6000_8000
  val CLIC_SIZE       = 0x8000L  // 32 KB
  
  // CLIC Configuration
  val MAX_INTERRUPTS  = 256  // Up to 256 external interrupts
  
  /**
   * Helper functions
   */
  def inCLINT(addr: UInt): Bool = MemoryMap.inRegion(addr, CLINT_BASE, CLINT_SIZE)
  def inCLIC(addr: UInt): Bool = MemoryMap.inRegion(addr, CLIC_BASE, CLIC_SIZE)
  def inMSIP(addr: UInt): Bool = MemoryMap.inRegion(addr, MSIP_BASE, 0x4L)
  def inMTIMECMP(addr: UInt): Bool = MemoryMap.inRegion(addr, MTIMECMP_BASE, 0x8L)
  def inMTIME(addr: UInt): Bool = MemoryMap.inRegion(addr, MTIME_BASE, 0x8L)
}

/**
 * Debug Module addresses within Debug Region
 * Base: 0x7000_0000 - 0x7000_FFFF (64 KB)
 */
object DebugMap {
  val DEBUG_BASE      = MemoryMap.DEBUG_BASE
  val DEBUG_SIZE      = MemoryMap.DEBUG_SIZE
  
  // Debug Module Interface Registers (RISC-V Debug Spec 0.13)
  val DM_DATA0        = DEBUG_BASE + 0x0004L  // Abstract Data 0
  val DM_DATA1        = DEBUG_BASE + 0x0008L  // Abstract Data 1
  val DM_DMCONTROL    = DEBUG_BASE + 0x0010L  // Debug Module Control
  val DM_DMSTATUS     = DEBUG_BASE + 0x0011L  // Debug Module Status
  val DM_HARTINFO     = DEBUG_BASE + 0x0012L  // Hart Info
  val DM_ABSTRACTCS   = DEBUG_BASE + 0x0016L  // Abstract Control/Status
  val DM_COMMAND      = DEBUG_BASE + 0x0017L  // Abstract Command
  val DM_PROGBUF0     = DEBUG_BASE + 0x0020L  // Program Buffer 0
  val DM_PROGBUF1     = DEBUG_BASE + 0x0021L  // Program Buffer 1
  val DM_SBCS         = DEBUG_BASE + 0x0038L  // System Bus Access Control/Status
  val DM_SBADDRESS0   = DEBUG_BASE + 0x0039L  // System Bus Address 31:0
  val DM_SBDATA0      = DEBUG_BASE + 0x003CL  // System Bus Data 31:0
  
  /**
   * Helper function
   */
  def inDebugModule(addr: UInt): Bool = MemoryMap.inRegion(addr, DEBUG_BASE, DEBUG_SIZE)
}

/**
 * SoC Configuration Parameters
 * 
 * Consolidates all configuration parameters for the PALMV SoC
 */
case class SoCConfig(
  // Core Configuration
  xlen: Int = 32,
  
  // ISA Extensions
  enableMExtension: Boolean = false,   // Multiply/Divide
  enableBExtension: Boolean = false,   // Bit Manipulation
  
  // Memory Sizes (actual implemented sizes, not address space)
  bootROMSize: Int = 64 * 1024,        // 64 KB
  flashSize: Int = 16 * 1024 * 1024,   // 16 MB (of 256 MB address space)
  sramSize: Int = 1 * 1024 * 1024,     // 1 MB (of 256 MB address space)
  
  // Physical Memory Protection
  numPMPEntries: Int = 8,
  
  // DMA Configuration
  numDMAChannels: Int = 4,
  dmaMaxBurstLen: Int = 16,
  
  // Interrupt Configuration
  numExternalInterrupts: Int = 256,
  
  // Cache Configuration (optional)
  iCacheSize: Int = 8 * 1024,          // 8 KB instruction cache
  dCacheSize: Int = 8 * 1024,          // 8 KB data cache
  cacheLineSize: Int = 32,             // 32-byte cache lines
  
  // Performance Monitoring
  numPerfCounters: Int = 29,           // HPM counters (mhpmcounter3-31)
  
  // Debug Configuration
  debugSupport: Boolean = true,
  numDebugTriggers: Int = 4
) {
  // Derived parameters
  val addrWidth = xlen
  val dataWidth = xlen
  
  // Validate configuration
  require(xlen == 32, "Only RV32 is supported")
  require(numPMPEntries <= 64, "Maximum 64 PMP entries")
  require(numDMAChannels <= 8, "Maximum 8 DMA channels")
  require(numExternalInterrupts <= 4096, "Maximum 4096 interrupts")
  require(isPow2(iCacheSize) && iCacheSize >= 1024, "Invalid iCache size")
  require(isPow2(dCacheSize) && dCacheSize >= 1024, "Invalid dCache size")
  require(isPow2(cacheLineSize) && cacheLineSize >= 16, "Invalid cache line size")
  
  private def isPow2(n: Int): Boolean = (n & (n - 1)) == 0
}

/**
 * Default SoC Configuration
 */
object DefaultSoCConfig {
  def apply(): SoCConfig = SoCConfig()
}
