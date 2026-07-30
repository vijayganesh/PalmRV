package palmsoc.interrupt

import chisel3._
import chisel3.util._
import palmsoc.bus.{AXI4LiteSlave, AXI4LiteResp}
import palmsoc.config.AXI4LiteConfig

/**
 * Custom Interrupt Controller (Simple PLIC replacement)
 * 
 * Aggregates peripheral interrupt lines and generates a single external_interrupt 
 * signal for the CPU core.
 * 
 * Register Map:
 * 0x00: INT_EN Register (R/W)
 *       Bits [numSources-1:0]: Enable mask for each interrupt source
 * 0x04: INT_PEND Register (R)
 *       Bits [numSources-1:0]: Status of pending interrupts
 * 0x08: INT_ACK Register (W)
 *       Bits [numSources-1:0]: Write 1 to clear the corresponding pending bit
 */
class InterruptController(
  config: AXI4LiteConfig = AXI4LiteConfig(32, 32),
  numSources: Int = 4
) extends AXI4LiteSlave(config) {
  
  require(numSources > 0 && numSources <= 32, "Interrupt sources must be between 1 and 32")
  
  // Peripheral interrupt inputs
  val io_interrupts = IO(Input(UInt(numSources.W)))
  
  // Core-facing external interrupt output
  val io_ext_int = IO(Output(Bool()))
  
  // Internal registers
  val int_en_reg = RegInit(0.U(numSources.W))
  val int_pend_reg = RegInit(0.U(numSources.W))
  
  // Previous input value for rising-edge detection
  val prev_inputs = RegNext(io_interrupts)
  val rising_edges = io_interrupts & ~prev_inputs
  
  // Clear mask triggered via AXI register write
  val clear_mask = WireDefault(0.U(numSources.W))
  
  // Pending logic: set on raw level or rising edge, cleared by clear_mask
  int_pend_reg := (int_pend_reg | io_interrupts | rising_edges) & ~clear_mask
  
  // Output external interrupt if any enabled interrupt is pending
  io_ext_int := (int_pend_reg & int_en_reg).orR
  
  // AXI4-Lite FSM State Machine
  val sIdle :: sWriteData :: sWriteResp :: sReadData :: Nil = Enum(4)
  val state = RegInit(sIdle)
  
  val writeAddr = RegInit(0.U(config.addrWidth.W))
  val readAddr = RegInit(0.U(config.addrWidth.W))
  val readData = RegInit(0.U(config.dataWidth.W))
  
  // Default AXI output signals
  io.axi.awready := false.B
  io.axi.wready  := false.B
  io.axi.bresp   := AXI4LiteResp.OKAY
  io.axi.bvalid  := false.B
  io.axi.arready := false.B
  io.axi.rdata   := readData
  io.axi.rresp   := AXI4LiteResp.OKAY
  io.axi.rvalid  := false.B
  
  switch(state) {
    is(sIdle) {
      io.axi.awready := true.B
      io.axi.arready := true.B
      when(io.axi.awvalid && io.axi.awready) {
        writeAddr := io.axi.awaddr
        state := sWriteData
      }.elsewhen(io.axi.arvalid && io.axi.arready) {
        readAddr := io.axi.araddr
        state := sReadData
      }
    }
    
    is(sWriteData) {
      io.axi.wready := true.B
      when(io.axi.wvalid && io.axi.wready) {
        val reg_offset = writeAddr(7, 0)
        
        switch(reg_offset) {
          is(0x00.U) {
            int_en_reg := io.axi.wdata(numSources - 1, 0)
          }
          is(0x08.U) {
            clear_mask := io.axi.wdata(numSources - 1, 0)
          }
        }
        state := sWriteResp
      }
    }
    
    is(sWriteResp) {
      io.axi.bvalid := true.B
      when(io.axi.bvalid && io.axi.bready) {
        state := sIdle
      }
    }
    
    is(sReadData) {
      val reg_offset = readAddr(7, 0)
      val read_val = WireDefault(0.U(config.dataWidth.W))
      
      switch(reg_offset) {
        is(0x00.U) {
          read_val := int_en_reg
        }
        is(0x04.U) {
          read_val := int_pend_reg
        }
      }
      
      readData := read_val
      io.axi.rdata := read_val
      io.axi.rvalid := true.B
      when(io.axi.rready) {
        state := sIdle
      }
    }
  }
}

/**
 * Companion object for InterruptController
 */
object InterruptController {
  def apply(): InterruptController = {
    new InterruptController(AXI4LiteConfig(32, 32), 4)
  }
  
  def apply(config: AXI4LiteConfig, numSources: Int): InterruptController = {
    new InterruptController(config, numSources)
  }
}
