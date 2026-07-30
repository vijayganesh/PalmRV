package palmsoc.bus

import chisel3._
import palmsoc.config.AXI4LiteConfig

/**
 * AXI4-Lite Bus Interface Bundle
 * 
 * Simplified AXI4 protocol for memory-mapped peripheral access.
 * Based on AXI4LiteConfig for parameterized address and data widths.
 */
class AXI4LiteIO(config: AXI4LiteConfig) extends Bundle {
  // Write Address Channel
  val awaddr  = Output(UInt(config.addrWidth.W))
  val awprot  = Output(UInt(3.W))
  val awvalid = Output(Bool())
  val awready = Input(Bool())
  
  // Write Data Channel
  val wdata  = Output(UInt(config.dataWidth.W))
  val wstrb  = Output(UInt(config.strbWidth.W))
  val wvalid = Output(Bool())
  val wready = Input(Bool())
  
  // Write Response Channel
  val bresp  = Input(UInt(2.W))
  val bvalid = Input(Bool())
  val bready = Output(Bool())
  
  // Read Address Channel
  val araddr  = Output(UInt(config.addrWidth.W))
  val arprot  = Output(UInt(3.W))
  val arvalid = Output(Bool())
  val arready = Input(Bool())
  
  // Read Data Channel
  val rdata  = Input(UInt(config.dataWidth.W))
  val rresp  = Input(UInt(2.W))
  val rvalid = Input(Bool())
  val rready = Output(Bool())
}

/**
 * AXI4-Lite Response Codes
 */
object AXI4LiteResp {
  val OKAY   = 0.U(2.W)  // Normal access success
  val EXOKAY = 1.U(2.W)  // Exclusive access okay (not typically used in AXI4-Lite)
  val SLVERR = 2.U(2.W)  // Slave error
  val DECERR = 3.U(2.W)  // Decode error
}

/**
 * AXI4-Lite Protection Types
 */
object AXI4LiteProt {
  // Bit [0]: Privileged (1) or Unprivileged (0)
  // Bit [1]: Non-secure (1) or Secure (0)
  // Bit [2]: Instruction (1) or Data (0)
  
  val PRIVILEGED_DATA    = 0x1.U(3.W)  // Privileged, Secure, Data
  val UNPRIVILEGED_DATA  = 0x0.U(3.W)  // Unprivileged, Secure, Data
  val PRIVILEGED_INSTR   = 0x5.U(3.W)  // Privileged, Secure, Instruction
  val NONSECURE_DATA     = 0x2.U(3.W)  // Unprivileged, Non-secure, Data
}

/**
 * Abstract AXI4-Lite Master Module
 * 
 * Base class for AXI4-Lite bus master implementations (e.g., CPU, DMA).
 * Provides default signal initialization and common structure.
 * 
 * @param config AXI4LiteConfig specifying address and data widths
 */
abstract class AXI4LiteMaster(config: AXI4LiteConfig) extends Module {
  val io = IO(new Bundle {
    val axi = new AXI4LiteIO(config)
  })
  
  // Default signal initialization - all channels idle
  // Write Address Channel
  io.axi.awaddr  := 0.U
  io.axi.awprot  := AXI4LiteProt.PRIVILEGED_DATA
  io.axi.awvalid := false.B
  
  // Write Data Channel
  io.axi.wdata  := 0.U
  io.axi.wstrb  := 0.U
  io.axi.wvalid := false.B
  
  // Write Response Channel
  io.axi.bready := false.B
  
  // Read Address Channel
  io.axi.araddr  := 0.U
  io.axi.arprot  := AXI4LiteProt.PRIVILEGED_DATA
  io.axi.arvalid := false.B
  
  // Read Data Channel
  io.axi.rready := false.B
}

/**
 * Abstract AXI4-Lite Slave Module
 * 
 * Base class for AXI4-Lite bus slave implementations (e.g., peripherals, memory).
 * Provides default signal initialization with flipped interface and common structure.
 * 
 * @param config AXI4LiteConfig specifying address and data widths
 */
abstract class AXI4LiteSlave(config: AXI4LiteConfig) extends Module {
  val io = IO(new Bundle {
    val axi = Flipped(new AXI4LiteIO(config))
  })
  
  // Default signal initialization - slave not responding
  // Write Address Channel
  io.axi.awready := false.B
  
  // Write Data Channel
  io.axi.wready := false.B
  
  // Write Response Channel
  io.axi.bresp  := AXI4LiteResp.DECERR
  io.axi.bvalid := false.B
  
  // Read Address Channel
  io.axi.arready := false.B
  
  // Read Data Channel
  io.axi.rdata  := 0.U
  io.axi.rresp  := AXI4LiteResp.DECERR
  io.axi.rvalid := false.B
}
