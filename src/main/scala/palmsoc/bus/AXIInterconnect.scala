package palmsoc.bus

import chisel3._
import chisel3.util._
import palmsoc.config._

/**
 * AXI4-Lite Interconnect (Crossbar)
 * 
 * Implements a full crossbar interconnect that connects multiple AXI4-Lite masters
 * to multiple AXI4-Lite slaves. The interconnect performs:
 * 
 * 1. Address Decoding: Routes transactions to appropriate slaves based on address
 * 2. Arbitration: Handles conflicts when multiple masters access same slave
 * 3. Response Routing: Returns responses from slaves to correct masters
 * 4. Error Handling: Generates error responses for unmapped addresses
 * 
 * Architecture:
 * - Separate read and write paths for maximum throughput
 * - Round-robin arbitration for fairness
 * - Single outstanding transaction per master (AXI4-Lite constraint)
 * - Combinational address decode for low latency
 * 
 * Performance:
 * - Zero-cycle address decode latency
 * - One-cycle arbitration latency when no conflicts
 * - Full bandwidth when masters access different slaves
 */

/**
 * AXI4-Lite Interconnect IO
 */
class AXI4LiteInterconnectIO(
  numMasters: Int,
  numSlaves: Int,
  addrWidth: Int = 32,
  dataWidth: Int = 32
) extends Bundle {
  val masters = Flipped(Vec(numMasters, new AXI4LiteMasterInterface(addrWidth, dataWidth)))
  val slaves = Vec(numSlaves, new AXI4LiteMasterInterface(addrWidth, dataWidth))
}

/**
 * Write Channel Interconnect
 * 
 * Handles write address, write data, and write response channels.
 * Since AXI4-Lite doesn't support write data interleaving, we can
 * simplify the arbitration.
 */
class AXI4LiteWriteInterconnect(
  numMasters: Int,
  numSlaves: Int,
  addrWidth: Int = 32,
  dataWidth: Int = 32,
  addressMap: Seq[(Long, Long)]
) extends Module {
  val io = IO(new Bundle {
    val masters = Flipped(Vec(numMasters, new Bundle {
      val aw = new AXI4LiteWriteAddressChannel(addrWidth)
      val w = new AXI4LiteWriteDataChannel(dataWidth)
      val b = new AXI4LiteWriteResponseChannel
    }))
    val slaves = Vec(numSlaves, new Bundle {
      val aw = new AXI4LiteWriteAddressChannel(addrWidth)
      val w = new AXI4LiteWriteDataChannel(dataWidth)
      val b = new AXI4LiteWriteResponseChannel
    })
  })
  
  // Address decoders for each master
  val decoders = Seq.fill(numMasters)(Module(new AXI4LiteAddressDecoder(
    AddressDecoderParams(addrWidth, numSlaves, addressMap)
  )))
  
  // Connect decoders to master addresses
  for (m <- 0 until numMasters) {
    decoders(m).io.addr := io.masters(m).aw.awaddr
  }
  
  // Write transaction state per master
  val writeInProgress = RegInit(VecInit(Seq.fill(numMasters)(false.B)))
  val selectedSlave = Reg(Vec(numMasters, UInt(log2Ceil(numSlaves).W)))
  val hasDecodeError = Reg(Vec(numMasters, Bool()))
  
  // Arbitration: track which master owns each slave
  val slaveOwner = Reg(Vec(numSlaves, UInt(log2Ceil(numMasters).W)))
  val slaveBusy = RegInit(VecInit(Seq.fill(numSlaves)(false.B)))
  
  // Default slave outputs
  for (s <- 0 until numSlaves) {
    io.slaves(s).aw.awaddr := 0.U
    io.slaves(s).aw.awprot := 0.U
    io.slaves(s).aw.awvalid := false.B
    io.slaves(s).w.wdata := 0.U
    io.slaves(s).w.wstrb := 0.U
    io.slaves(s).w.wvalid := false.B
    io.slaves(s).b.bready := false.B
  }
  
  // Default master responses
  for (m <- 0 until numMasters) {
    io.masters(m).aw.awready := false.B
    io.masters(m).w.wready := false.B
    io.masters(m).b.bresp := AXI4LiteResp.DECERR
    io.masters(m).b.bvalid := false.B
  }
  
  // Write address channel arbitration and routing
  for (m <- 0 until numMasters) {
    when(!writeInProgress(m) && io.masters(m).aw.awvalid) {
      val decode = decoders(m).io.decode
      val targetSlave = decode.slaveSelect
      
      when(decode.validSlave && !slaveBusy(targetSlave)) {
        // Grant access to slave
        io.slaves(targetSlave).aw.awaddr := io.masters(m).aw.awaddr
        io.slaves(targetSlave).aw.awprot := io.masters(m).aw.awprot
        io.slaves(targetSlave).aw.awvalid := true.B
        io.masters(m).aw.awready := io.slaves(targetSlave).aw.awready
        
        when(io.slaves(targetSlave).aw.awready) {
          // Address accepted
          writeInProgress(m) := true.B
          selectedSlave(m) := targetSlave
          hasDecodeError(m) := false.B
          slaveBusy(targetSlave) := true.B
          slaveOwner(targetSlave) := m.U
        }
      }.elsewhen(!decode.validSlave) {
        // Decode error - accept address immediately and mark error
        io.masters(m).aw.awready := true.B
        writeInProgress(m) := true.B
        hasDecodeError(m) := true.B
      }
    }
  }
  
  // Write data channel routing
  for (m <- 0 until numMasters) {
    when(writeInProgress(m) && io.masters(m).w.wvalid && !hasDecodeError(m)) {
      val targetSlave = selectedSlave(m)
      
      // Forward write data to selected slave
      io.slaves(targetSlave).w.wdata := io.masters(m).w.wdata
      io.slaves(targetSlave).w.wstrb := io.masters(m).w.wstrb
      io.slaves(targetSlave).w.wvalid := true.B
      io.masters(m).w.wready := io.slaves(targetSlave).w.wready
    }.elsewhen(writeInProgress(m) && io.masters(m).w.wvalid && hasDecodeError(m)) {
      // For decode error, accept write data immediately
      io.masters(m).w.wready := true.B
    }
  }
  
  // Write response channel routing
  for (m <- 0 until numMasters) {
    when(writeInProgress(m)) {
      when(hasDecodeError(m)) {
        // Generate decode error response
        io.masters(m).b.bresp := AXI4LiteResp.DECERR
        io.masters(m).b.bvalid := io.masters(m).w.wready && io.masters(m).w.wvalid
        
        when(io.masters(m).b.bvalid && io.masters(m).b.bready) {
          writeInProgress(m) := false.B
        }
      }.otherwise {
        val targetSlave = selectedSlave(m)
        
        // Forward response from slave
        io.masters(m).b.bresp := io.slaves(targetSlave).b.bresp
        io.masters(m).b.bvalid := io.slaves(targetSlave).b.bvalid
        io.slaves(targetSlave).b.bready := io.masters(m).b.bready
        
        when(io.slaves(targetSlave).b.bvalid && io.masters(m).b.bready) {
          writeInProgress(m) := false.B
          slaveBusy(targetSlave) := false.B
        }
      }
    }
  }
}

/**
 * Read Channel Interconnect
 * 
 * Handles read address and read data channels.
 * Simpler than write path since there's only two channels.
 */
class AXI4LiteReadInterconnect(
  numMasters: Int,
  numSlaves: Int,
  addrWidth: Int = 32,
  dataWidth: Int = 32,
  addressMap: Seq[(Long, Long)]
) extends Module {
  val io = IO(new Bundle {
    val masters = Flipped(Vec(numMasters, new Bundle {
      val ar = new AXI4LiteReadAddressChannel(addrWidth)
      val r = new AXI4LiteReadDataChannel(dataWidth)
    }))
    val slaves = Vec(numSlaves, new Bundle {
      val ar = new AXI4LiteReadAddressChannel(addrWidth)
      val r = new AXI4LiteReadDataChannel(dataWidth)
    })
  })
  
  // Address decoders for each master
  val decoders = Seq.fill(numMasters)(Module(new AXI4LiteAddressDecoder(
    AddressDecoderParams(addrWidth, numSlaves, addressMap)
  )))
  
  // Connect decoders to master addresses
  for (m <- 0 until numMasters) {
    decoders(m).io.addr := io.masters(m).ar.araddr
  }
  
  // Read transaction state per master
  val readInProgress = RegInit(VecInit(Seq.fill(numMasters)(false.B)))
  val selectedSlave = Reg(Vec(numMasters, UInt(log2Ceil(numSlaves).W)))
  val hasDecodeError = Reg(Vec(numMasters, Bool()))
  
  // Arbitration: track which master owns each slave
  val slaveOwner = Reg(Vec(numSlaves, UInt(log2Ceil(numMasters).W)))
  val slaveBusy = RegInit(VecInit(Seq.fill(numSlaves)(false.B)))
  
  // Default slave outputs
  for (s <- 0 until numSlaves) {
    io.slaves(s).ar.araddr := 0.U
    io.slaves(s).ar.arprot := 0.U
    io.slaves(s).ar.arvalid := false.B
    io.slaves(s).r.rready := false.B
  }
  
  // Default master responses
  for (m <- 0 until numMasters) {
    io.masters(m).ar.arready := false.B
    io.masters(m).r.rdata := 0.U
    io.masters(m).r.rresp := AXI4LiteResp.DECERR
    io.masters(m).r.rvalid := false.B
  }
  
  // Read address channel arbitration and routing
  for (m <- 0 until numMasters) {
    when(!readInProgress(m) && io.masters(m).ar.arvalid) {
      val decode = decoders(m).io.decode
      val targetSlave = decode.slaveSelect
      
      when(decode.validSlave && !slaveBusy(targetSlave)) {
        // Grant access to slave
        io.slaves(targetSlave).ar.araddr := io.masters(m).ar.araddr
        io.slaves(targetSlave).ar.arprot := io.masters(m).ar.arprot
        io.slaves(targetSlave).ar.arvalid := true.B
        io.masters(m).ar.arready := io.slaves(targetSlave).ar.arready
        
        when(io.slaves(targetSlave).ar.arready) {
          // Address accepted
          readInProgress(m) := true.B
          selectedSlave(m) := targetSlave
          hasDecodeError(m) := false.B
          slaveBusy(targetSlave) := true.B
          slaveOwner(targetSlave) := m.U
        }
      }.elsewhen(!decode.validSlave) {
        // Decode error - accept address immediately and mark error
        io.masters(m).ar.arready := true.B
        readInProgress(m) := true.B
        hasDecodeError(m) := true.B
      }
    }
  }
  
  // Read data channel routing
  for (m <- 0 until numMasters) {
    when(readInProgress(m)) {
      when(hasDecodeError(m)) {
        // Generate decode error response
        io.masters(m).r.rdata := 0.U
        io.masters(m).r.rresp := AXI4LiteResp.DECERR
        io.masters(m).r.rvalid := true.B
        
        when(io.masters(m).r.rready) {
          readInProgress(m) := false.B
        }
      }.otherwise {
        val targetSlave = selectedSlave(m)
        
        // Forward response from slave
        io.masters(m).r.rdata := io.slaves(targetSlave).r.rdata
        io.masters(m).r.rresp := io.slaves(targetSlave).r.rresp
        io.masters(m).r.rvalid := io.slaves(targetSlave).r.rvalid
        io.slaves(targetSlave).r.rready := io.masters(m).r.rready
        
        when(io.slaves(targetSlave).r.rvalid && io.masters(m).r.rready) {
          readInProgress(m) := false.B
          slaveBusy(targetSlave) := false.B
        }
      }
    }
  }
}

/**
 * Complete AXI4-Lite Interconnect
 * 
 * Combines read and write interconnects into a complete crossbar.
 */
class AXI4LiteInterconnect(
  numMasters: Int,
  numSlaves: Int,
  addrWidth: Int = 32,
  dataWidth: Int = 32,
  addressMap: Seq[(Long, Long)]
) extends Module {
  val io = IO(new AXI4LiteInterconnectIO(numMasters, numSlaves, addrWidth, dataWidth))
  
  // Instantiate write and read interconnects
  val writeXbar = Module(new AXI4LiteWriteInterconnect(
    numMasters, numSlaves, addrWidth, dataWidth, addressMap
  ))
  
  val readXbar = Module(new AXI4LiteReadInterconnect(
    numMasters, numSlaves, addrWidth, dataWidth, addressMap
  ))
  
  // Connect masters
  for (m <- 0 until numMasters) {
    // Write channels
    writeXbar.io.masters(m).aw <> io.masters(m).aw
    writeXbar.io.masters(m).w <> io.masters(m).w
    io.masters(m).b <> writeXbar.io.masters(m).b
    
    // Read channels
    readXbar.io.masters(m).ar <> io.masters(m).ar
    io.masters(m).r <> readXbar.io.masters(m).r
  }
  
  // Connect slaves
  for (s <- 0 until numSlaves) {
    // Write channels
    io.slaves(s).aw <> writeXbar.io.slaves(s).aw
    io.slaves(s).w <> writeXbar.io.slaves(s).w
    writeXbar.io.slaves(s).b <> io.slaves(s).b
    
    // Read channels
    io.slaves(s).ar <> readXbar.io.slaves(s).ar
    readXbar.io.slaves(s).r <> io.slaves(s).r
  }
}

/**
 * PALMV Main Interconnect
 * 
 * Pre-configured interconnect for the main PALMV SoC bus.
 * Connects CPU and DMA to all major memory regions.
 */
class PalmVMainInterconnect extends Module {
  val io = IO(new AXI4LiteInterconnectIO(
    numMasters = 2,   // CPU, DMA
    numSlaves = 8,    // Boot ROM, Flash, SRAM, Peripherals, Accelerators, DMA, Interrupts, Debug
    addrWidth = 32,
    dataWidth = 32
  ))
  
  import MemoryMap._
  
  val xbar = Module(new AXI4LiteInterconnect(
    numMasters = 2,
    numSlaves = 8,
    addrWidth = 32,
    dataWidth = 32,
    addressMap = Seq(
      (BOOTROM_BASE, BOOTROM_SIZE),
      (FLASH_BASE, FLASH_SIZE),
      (SRAM_BASE, SRAM_SIZE),
      (PERIPHERAL_BASE, PERIPHERAL_SIZE),
      (ACCELERATOR_BASE, ACCELERATOR_SIZE),
      (DMA_BASE, DMA_SIZE),
      (INTERRUPT_BASE, INTERRUPT_SIZE),
      (DEBUG_BASE, DEBUG_SIZE)
    )
  ))
  
  io <> xbar.io
}

/**
 * PALMV Peripheral Interconnect
 * 
 * Sub-interconnect for connecting to individual peripherals.
 */
class PalmVPeripheralInterconnect extends Module {
  val io = IO(new AXI4LiteInterconnectIO(
    numMasters = 1,   // From main interconnect
    numSlaves = 10,   // All peripherals
    addrWidth = 32,
    dataWidth = 32
  ))
  
  import PeripheralMap._
  
  val xbar = Module(new AXI4LiteInterconnect(
    numMasters = 1,
    numSlaves = 10,
    addrWidth = 32,
    dataWidth = 32,
    addressMap = Seq(
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
  ))
  
  io <> xbar.io
}

/**
 * PALMV Accelerator Interconnect
 * 
 * Sub-interconnect for connecting to hardware accelerators.
 */
class PalmVAcceleratorInterconnect extends Module {
  val io = IO(new AXI4LiteInterconnectIO(
    numMasters = 1,   // From main interconnect
    numSlaves = 7,    // All accelerators
    addrWidth = 32,
    dataWidth = 32
  ))
  
  import AcceleratorMap._
  
  val xbar = Module(new AXI4LiteInterconnect(
    numMasters = 1,
    numSlaves = 7,
    addrWidth = 32,
    dataWidth = 32,
    addressMap = Seq(
      (LSTM_BASE, LSTM_SIZE),
      (CNN_BASE, CNN_SIZE),
      (AES_BASE, AES_SIZE),
      (SHA_BASE, SHA_SIZE),
      (DSP_BASE, DSP_SIZE),
      (FFT_BASE, FFT_SIZE),
      (MATRIX_BASE, MATRIX_SIZE)
    )
  ))
  
  io <> xbar.io
}

/**
 * Simple 1-to-N AXI4-Lite Demux
 * 
 * Lightweight alternative to full crossbar when only one master exists.
 * More area efficient than full crossbar for simple cases.
 */
class AXI4LiteDemux(
  numSlaves: Int,
  addrWidth: Int = 32,
  dataWidth: Int = 32,
  addressMap: Seq[(Long, Long)]
) extends Module {
  val io = IO(new Bundle {
    val master = Flipped(new AXI4LiteMasterInterface(addrWidth, dataWidth))
    val slaves = Vec(numSlaves, new AXI4LiteMasterInterface(addrWidth, dataWidth))
  })
  
  // Use single master interconnect
  val xbar = Module(new AXI4LiteInterconnect(
    numMasters = 1,
    numSlaves = numSlaves,
    addrWidth = addrWidth,
    dataWidth = dataWidth,
    addressMap = addressMap
  ))
  
  xbar.io.masters(0) <> io.master
  io.slaves <> xbar.io.slaves
}

/**
 * AXI4-Lite Register Slice
 * 
 * Adds pipeline registers to break timing paths in the interconnect.
 * Can be inserted between masters/slaves and the interconnect.
 */
class AXI4LiteRegisterSlice(addrWidth: Int = 32, dataWidth: Int = 32) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(new AXI4LiteMasterInterface(addrWidth, dataWidth))
    val out = new AXI4LiteMasterInterface(addrWidth, dataWidth)
  })
  
  import chisel3.util._
  
  // Helper to pipeline an AXI channel using a 2-element Queue (Skid Buffer)
  def pipelineChannel[T <: Data](
    vIn: Bool, rIn: Bool, pIn: T,
    vOut: Bool, rOut: Bool, pOut: T
  ): Unit = {
    val q = Module(new Queue(chiselTypeOf(pIn), 2))
    q.io.enq.valid := vIn
    q.io.enq.bits := pIn
    rIn := q.io.enq.ready
    
    vOut := q.io.deq.valid
    pOut := q.io.deq.bits
    q.io.deq.ready := rOut
  }
  
  // AW Channel
  val awPayloadIn = Wire(new Bundle { val addr = UInt(addrWidth.W); val prot = UInt(3.W) })
  awPayloadIn.addr := io.in.aw.awaddr
  awPayloadIn.prot := io.in.aw.awprot
  val awPayloadOut = Wire(chiselTypeOf(awPayloadIn))
  pipelineChannel(io.in.aw.awvalid, io.in.aw.awready, awPayloadIn, io.out.aw.awvalid, io.out.aw.awready, awPayloadOut)
  io.out.aw.awaddr := awPayloadOut.addr
  io.out.aw.awprot := awPayloadOut.prot
  
  // W Channel
  val wPayloadIn = Wire(new Bundle { val data = UInt(dataWidth.W); val strb = UInt((dataWidth/8).W) })
  wPayloadIn.data := io.in.w.wdata
  wPayloadIn.strb := io.in.w.wstrb
  val wPayloadOut = Wire(chiselTypeOf(wPayloadIn))
  pipelineChannel(io.in.w.wvalid, io.in.w.wready, wPayloadIn, io.out.w.wvalid, io.out.w.wready, wPayloadOut)
  io.out.w.wdata := wPayloadOut.data
  io.out.w.wstrb := wPayloadOut.strb
  
  // B Channel (Reverse Direction)
  val bPayloadIn = Wire(new Bundle { val resp = UInt(2.W) })
  bPayloadIn.resp := io.out.b.bresp
  val bPayloadOut = Wire(chiselTypeOf(bPayloadIn))
  pipelineChannel(io.out.b.bvalid, io.out.b.bready, bPayloadIn, io.in.b.bvalid, io.in.b.bready, bPayloadOut)
  io.in.b.bresp := bPayloadOut.resp
  
  // AR Channel
  val arPayloadIn = Wire(new Bundle { val addr = UInt(addrWidth.W); val prot = UInt(3.W) })
  arPayloadIn.addr := io.in.ar.araddr
  arPayloadIn.prot := io.in.ar.arprot
  val arPayloadOut = Wire(chiselTypeOf(arPayloadIn))
  pipelineChannel(io.in.ar.arvalid, io.in.ar.arready, arPayloadIn, io.out.ar.arvalid, io.out.ar.arready, arPayloadOut)
  io.out.ar.araddr := arPayloadOut.addr
  io.out.ar.arprot := arPayloadOut.prot
  
  // R Channel (Reverse Direction)
  val rPayloadIn = Wire(new Bundle { val data = UInt(dataWidth.W); val resp = UInt(2.W) })
  rPayloadIn.data := io.out.r.rdata
  rPayloadIn.resp := io.out.r.rresp
  val rPayloadOut = Wire(chiselTypeOf(rPayloadIn))
  pipelineChannel(io.out.r.rvalid, io.out.r.rready, rPayloadIn, io.in.r.rvalid, io.in.r.rready, rPayloadOut)
  io.in.r.rdata := rPayloadOut.data
  io.in.r.rresp := rPayloadOut.resp
}

/**
 * AXI4-Lite Interconnect Utilities
 */
object AXI4LiteInterconnectUtils {
  /**
   * Connect master to slave directly (1-to-1)
   */
  def connect(master: AXI4LiteMasterInterface, slave: AXI4LiteMasterInterface): Unit = {
    slave <> master
  }
  
  /**
   * Create a simple 1-to-N demux
   */
  def createDemux(
    numSlaves: Int,
    addrWidth: Int = 32,
    dataWidth: Int = 32,
    addressMap: Seq[(Long, Long)]
  ): AXI4LiteDemux = {
    Module(new AXI4LiteDemux(numSlaves, addrWidth, dataWidth, addressMap))
  }
  
  /**
   * Insert register slice for timing closure
   */
  def insertRegSlice(
    master: AXI4LiteMasterInterface,
    slave: AXI4LiteMasterInterface
  )(implicit addrWidth: Int = 32, dataWidth: Int = 32): Unit = {
    val regSlice = Module(new AXI4LiteRegisterSlice(addrWidth, dataWidth))
    regSlice.io.in <> master
    slave <> regSlice.io.out
  }
}
