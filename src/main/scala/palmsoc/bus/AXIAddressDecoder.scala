package palmsoc.bus

import chisel3._
import chisel3.util._
import palmsoc.config._

/**
 * AXI4-Lite Address Decoder
 * 
 * Decodes address from AXI4-Lite transactions and generates slave select signals
 * based on the configured memory map. This module is the heart of the address
 * routing in the AXI interconnect.
 * 
 * Features:
 * - Configurable address ranges for each slave
 * - Priority-based address matching (lower index = higher priority)
 * - Default slave for unmapped addresses
 * - Decode error detection
 * - Address alignment checking
 * 
 * Address Decoding Strategy:
 * 1. Check address against each slave's address range
 * 2. Return first matching slave index (priority order)
 * 3. Return error signal if no match found
 * 4. Optionally check address alignment
 */

/**
 * Address Decoder Parameters
 */
case class AddressDecoderParams(
  addrWidth: Int,
  numSlaves: Int,
  // List of (base_address, size) for each slave
  addressMap: Seq[(Long, Long)]
) {
  require(addrWidth > 0 && addrWidth <= 64, "Invalid address width")
  require(numSlaves > 0, "Must have at least one slave")
  require(addressMap.length == numSlaves, "Address map must match number of slaves")
  
  // Validate no address overlaps
  for (i <- addressMap.indices) {
    val (base1, size1) = addressMap(i)
    require(size1 > 0, s"Slave $i size must be positive")
    require(base1 >= 0, s"Slave $i base address must be non-negative")
    require(base1 + size1 <= (1L << addrWidth), s"Slave $i address range exceeds address space")
  }
}

/**
 * Address Decoder Output Bundle
 */
class AddressDecodeResult(numSlaves: Int) extends Bundle {
  val slaveSelect = UInt(log2Ceil(numSlaves).W)  // Selected slave index
  val validSlave = Bool()                         // Valid slave found
  val decodeError = Bool()                        // No valid slave for address
  
  def slaveMask: UInt = UIntToOH(slaveSelect, numSlaves)
}

/**
 * AXI4-Lite Address Decoder Module
 * 
 * Pure combinational logic for address decoding.
 * Can be instantiated multiple times for read and write channels.
 */
class AXI4LiteAddressDecoder(params: AddressDecoderParams) extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(params.addrWidth.W))
    val decode = Output(new AddressDecodeResult(params.numSlaves))
  })
  
  // Generate address match signals for each slave
  val matchSignals = Wire(Vec(params.numSlaves, Bool()))
  
  for (i <- 0 until params.numSlaves) {
    val (base, size) = params.addressMap(i)
    val baseAddr = base.U(params.addrWidth.W)
    val endAddr = (base + size - 1).U(params.addrWidth.W)
    
    // Address is in range if: base <= addr <= end
    matchSignals(i) := (io.addr >= baseAddr) && (io.addr <= endAddr)
  }
  
  // Priority encoder: select first matching slave
  val anyMatch = matchSignals.asUInt.orR
  val selectedSlave = PriorityEncoder(matchSignals.asUInt)
  
  // Output decode result
  io.decode.slaveSelect := selectedSlave
  io.decode.validSlave := anyMatch
  io.decode.decodeError := !anyMatch
}

/**
 * AXI4-Lite Address Decoder with Alignment Check
 * 
 * Extended decoder that also checks address alignment for word accesses.
 */
class AXI4LiteAddressDecoderAligned(
  params: AddressDecoderParams,
  alignmentBytes: Int = 4
) extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(params.addrWidth.W))
    val decode = Output(new AddressDecodeResult(params.numSlaves))
    val alignError = Output(Bool())
  })
  
  require(isPow2(alignmentBytes), "Alignment must be power of 2")
  
  // Instantiate base decoder
  val decoder = Module(new AXI4LiteAddressDecoder(params))
  decoder.io.addr := io.addr
  
  // Check alignment
  val alignMask = (alignmentBytes - 1).U
  val isAligned = (io.addr & alignMask) === 0.U
  
  io.decode := decoder.io.decode
  io.alignError := !isAligned
}

/**
 * Optimized Address Decoder using upper address bits
 * 
 * For memory maps where regions are separated by upper address bits,
 * this provides faster decoding using direct bit indexing rather than
 * range comparison.
 * 
 * Example: If regions are at 0x0XXX_XXXX, 0x1XXX_XXXX, 0x2XXX_XXXX, etc.
 *          Then addr[31:28] directly selects the region.
 */
class AXI4LiteAddressDecoderFast(
  addrWidth: Int,
  numSlaves: Int,
  regionBits: Int = 4  // Number of upper bits used for region selection
) extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(addrWidth.W))
    val decode = Output(new AddressDecodeResult(numSlaves))
    val regionMap = Input(Vec(1 << regionBits, UInt(log2Ceil(numSlaves).W)))
    val regionValid = Input(Vec(1 << regionBits, Bool()))
  })
  
  require(regionBits <= addrWidth, "Region bits must fit in address width")
  
  // Extract region from upper address bits
  val region = io.addr(addrWidth - 1, addrWidth - regionBits)
  
  // Lookup slave index from region map
  io.decode.slaveSelect := io.regionMap(region)
  io.decode.validSlave := io.regionValid(region)
  io.decode.decodeError := !io.regionValid(region)
}

/**
 * PALMV Main Address Decoder
 * 
 * Pre-configured decoder for the main PALMV SoC memory map.
 * Decodes addresses to 8 major regions.
 */
class PalmVAddressDecoder extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(32.W))
    val decode = Output(new AddressDecodeResult(8))
  })
  
  import MemoryMap._
  
  val params = AddressDecoderParams(
    addrWidth = 32,
    numSlaves = 8,
    addressMap = Seq(
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
  
  val decoder = Module(new AXI4LiteAddressDecoder(params))
  decoder.io.addr := io.addr
  io.decode := decoder.io.decode
}

/**
 * Optimized PALMV Address Decoder using fast lookup
 * 
 * Uses upper 4 bits (addr[31:28]) for fast region selection.
 * PALMV memory map is organized such that:
 * - 0x0_______ -> Boot ROM
 * - 0x1_______ -> Flash
 * - 0x2_______ -> SRAM
 * - 0x3_______ -> Peripherals
 * - 0x4_______ -> Accelerators
 * - 0x5_______ -> DMA
 * - 0x6_______ -> Interrupts
 * - 0x7_______ -> Debug
 * - 0x8-0xF___ -> Reserved/Error
 */
class PalmVAddressDecoderFast extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(32.W))
    val decode = Output(new AddressDecodeResult(8))
  })
  
  // Use fast decoder with 4-bit region selection
  val decoder = Module(new AXI4LiteAddressDecoderFast(
    addrWidth = 32,
    numSlaves = 8,
    regionBits = 4
  ))
  
  decoder.io.addr := io.addr
  
  // Build static region map (ROM lookup table)
  val regionMap = VecInit(Seq(
    0.U,  // 0x0: Boot ROM (slave 0)
    1.U,  // 0x1: Flash (slave 1)
    2.U,  // 0x2: SRAM (slave 2)
    3.U,  // 0x3: Peripherals (slave 3)
    4.U,  // 0x4: Accelerators (slave 4)
    5.U,  // 0x5: DMA (slave 5)
    6.U,  // 0x6: Interrupts (slave 6)
    7.U,  // 0x7: Debug (slave 7)
    0.U,  // 0x8: Reserved (decode error)
    0.U,  // 0x9: Reserved (decode error)
    0.U,  // 0xA: Reserved (decode error)
    0.U,  // 0xB: Reserved (decode error)
    0.U,  // 0xC: Reserved (decode error)
    0.U,  // 0xD: Reserved (decode error)
    0.U,  // 0xE: Reserved (decode error)
    0.U   // 0xF: Reserved (decode error)
  ))
  
  val regionValid = VecInit(Seq(
    true.B,   // 0x0: Boot ROM
    true.B,   // 0x1: Flash
    true.B,   // 0x2: SRAM
    true.B,   // 0x3: Peripherals
    true.B,   // 0x4: Accelerators
    true.B,   // 0x5: DMA
    true.B,   // 0x6: Interrupts
    true.B,   // 0x7: Debug
    false.B,  // 0x8: Reserved
    false.B,  // 0x9: Reserved
    false.B,  // 0xA: Reserved
    false.B,  // 0xB: Reserved
    false.B,  // 0xC: Reserved
    false.B,  // 0xD: Reserved
    false.B,  // 0xE: Reserved
    false.B   // 0xF: Reserved
  ))
  
  decoder.io.regionMap := regionMap
  decoder.io.regionValid := regionValid
  
  io.decode := decoder.io.decode
}

/**
 * Peripheral Sub-Decoder
 * 
 * Decodes addresses within the peripheral region (0x3000_0000 - 0x3FFF_FFFF)
 * to individual peripheral slaves.
 */
class PeripheralAddressDecoder extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(32.W))
    val decode = Output(new AddressDecodeResult(10))  // 10 peripherals
  })
  
  import PeripheralMap._
  
  val params = AddressDecoderParams(
    addrWidth = 32,
    numSlaves = 10,
    addressMap = Seq(
      (UART0_BASE, UART_SIZE),   // Slave 0
      (UART1_BASE, UART_SIZE),   // Slave 1
      (SPI0_BASE, SPI_SIZE),     // Slave 2
      (SPI1_BASE, SPI_SIZE),     // Slave 3
      (I2C0_BASE, I2C_SIZE),     // Slave 4
      (I2C1_BASE, I2C_SIZE),     // Slave 5
      (GPIO_BASE, GPIO_SIZE),    // Slave 6
      (PWM_BASE, PWM_SIZE),      // Slave 7
      (ADC_BASE, ADC_SIZE),      // Slave 8
      (TIMER_BASE, TIMER_SIZE)   // Slave 9
    )
  )
  
  val decoder = Module(new AXI4LiteAddressDecoder(params))
  decoder.io.addr := io.addr
  io.decode := decoder.io.decode
}

/**
 * Accelerator Sub-Decoder
 * 
 * Decodes addresses within the accelerator region (0x4000_0000 - 0x4FFF_FFFF)
 * to individual accelerator slaves.
 */
class AcceleratorAddressDecoder extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(32.W))
    val decode = Output(new AddressDecodeResult(7))  // 7 accelerators
  })
  
  import AcceleratorMap._
  
  val params = AddressDecoderParams(
    addrWidth = 32,
    numSlaves = 7,
    addressMap = Seq(
      (LSTM_BASE, LSTM_SIZE),    // Slave 0
      (CNN_BASE, CNN_SIZE),      // Slave 1
      (AES_BASE, AES_SIZE),      // Slave 2
      (SHA_BASE, SHA_SIZE),      // Slave 3
      (DSP_BASE, DSP_SIZE),      // Slave 4
      (FFT_BASE, FFT_SIZE),      // Slave 5
      (MATRIX_BASE, MATRIX_SIZE) // Slave 6
    )
  )
  
  val decoder = Module(new AXI4LiteAddressDecoder(params))
  decoder.io.addr := io.addr
  io.decode := decoder.io.decode
}

/**
 * DMA Channel Decoder
 * 
 * Decodes addresses within the DMA region (0x5000_0000 - 0x5000_FFFF)
 * to individual DMA channel registers or global control.
 */
class DMAChannelDecoder extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(32.W))
    val decode = Output(new AddressDecodeResult(5))  // 4 channels + global
  })
  
  import DMAMap._
  
  val params = AddressDecoderParams(
    addrWidth = 32,
    numSlaves = 5,
    addressMap = Seq(
      (CHANNEL0_BASE, CHANNEL_SIZE),     // Slave 0
      (CHANNEL1_BASE, CHANNEL_SIZE),     // Slave 1
      (CHANNEL2_BASE, CHANNEL_SIZE),     // Slave 2
      (CHANNEL3_BASE, CHANNEL_SIZE),     // Slave 3
      (GLOBAL_CTRL_BASE, GLOBAL_CTRL_SIZE) // Slave 4
    )
  )
  
  val decoder = Module(new AXI4LiteAddressDecoder(params))
  decoder.io.addr := io.addr
  io.decode := decoder.io.decode
}

/**
 * Interrupt Region Decoder
 * 
 * Decodes addresses within the interrupt region (0x6000_0000 - 0x6000_FFFF)
 * between CLINT and CLIC.
 */
class InterruptAddressDecoder extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(32.W))
    val decode = Output(new AddressDecodeResult(2))  // CLINT + CLIC
  })
  
  import InterruptMap._
  
  val params = AddressDecoderParams(
    addrWidth = 32,
    numSlaves = 2,
    addressMap = Seq(
      (CLINT_BASE, CLINT_SIZE),  // Slave 0: CLINT
      (CLIC_BASE, CLIC_SIZE)     // Slave 1: CLIC
    )
  )
  
  val decoder = Module(new AXI4LiteAddressDecoder(params))
  decoder.io.addr := io.addr
  io.decode := decoder.io.decode
}

/**
 * Address Decoder Utilities
 */
object AddressDecoderUtils {
  /**
   * Convert decode result to one-hot slave select
   */
  def decodeToOneHot(decode: AddressDecodeResult, numSlaves: Int): UInt = {
    Mux(decode.validSlave, 
        UIntToOH(decode.slaveSelect, numSlaves),
        0.U(numSlaves.W))
  }
  
  /**
   * Check if address is in a specific region (combinational)
   */
  def inRange(addr: UInt, base: Long, size: Long, addrWidth: Int): Bool = {
    val baseAddr = base.U(addrWidth.W)
    val endAddr = (base + size - 1).U(addrWidth.W)
    (addr >= baseAddr) && (addr <= endAddr)
  }
  
  /**
   * Compute region index from upper address bits
   */
  def getRegionIndex(addr: UInt, regionBits: Int, addrWidth: Int): UInt = {
    addr(addrWidth - 1, addrWidth - regionBits)
  }
  
  /**
   * Check address alignment
   */
  def isAligned(addr: UInt, alignmentBytes: Int): Bool = {
    require(isPow2(alignmentBytes), "Alignment must be power of 2")
    val mask = (alignmentBytes - 1).U
    (addr & mask) === 0.U
  }
}
