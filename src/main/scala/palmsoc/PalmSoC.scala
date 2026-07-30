package palmsoc

import chisel3._
import chisel3.util._
import palmsoc.core.RV32Core
import palmsoc.memory.{BootROM_AXI, SRAM_AXI}
import palmsoc.peripheral.GPIO_AXI
import palmsoc.config.AXI4LiteConfig
import chisel3.experimental.Analog

/**
 * PalmSoC with RV32Core, Boot ROM, SRAM, and GPIO
 * 
 * Memory Map:
 * 0x0000_0000 - 0x0000_0FFF : Boot ROM (4KB)
 * 0x1000_0000 - 0x1000_00FF : GPIO (256 bytes)
 * 0x8000_0000 - 0x8000_3FFF : SRAM (16KB)
 * 
 * Features:
 * - RV32I Core with 5-stage pipeline
 * - Boot ROM at reset vector (0x0000_0000)
 * - GPIO controller with 18 pins (0x1000_0000)
 * - Main memory (SRAM) at 0x8000_0000
 * - Simple address decoding
 */
class PalmSoC extends Module {
  val io = IO(new Bundle {
    // Debug outputs
    val pc = Output(UInt(32.W))
    val instruction = Output(UInt(32.W))
    
    // GPIO pins - vector of bidirectional analog pins
    val gpio = Vec(18, Analog(1.W))
    val gpio_interrupt = Output(Bool())
  })
  
  // AXI4-Lite configuration
  val axiConfig = AXI4LiteConfig(addrWidth = 32, dataWidth = 32)
  
  // Instantiate components
  val core = Module(new RV32Core)
  val bootrom = Module(new BootROM_AXI(
    config = axiConfig,
    depth = 1024,  // 4KB (1K words)
    initContent = None  // Will use default boot code
  ))
  val sram = Module(new SRAM_AXI(
    config = axiConfig,
    depth = 4096  // 16KB (4K words)
  ))
  val gpio_controller = Module(new GPIO_AXI(
    config = axiConfig,
    numPins = 18
  ))
  
  // GPIO analog pin mapping
  // For each GPIO pin, drive output when OE is high, read input otherwise
  val gpio_in_bits = Wire(UInt(18.W))
  gpio_in_bits := 0.U
  
  for (i <- 0 until 18) {
    when(gpio_controller.gpio_oe(i)) {
      // Output mode - drive the analog pin
      io.gpio(i) := gpio_controller.gpio_out(i).asBool
    }.otherwise {
      // Input mode - read from analog pin
      val input_bit = io.gpio(i)
      gpio_in_bits := gpio_in_bits | (input_bit.asUInt << i)
    }
  }
  
  // Connect GPIO input
  gpio_controller.gpio_in := gpio_in_bits
  
  // Connect interrupt
  io.gpio_interrupt := gpio_controller.interrupt
  core.io.external_interrupt := false.B
  
  // Memory interface state machine - separate states for each AXI phase
  val sIdle :: sImemRead :: sImemWait :: sDmemWriteAddr :: sDmemWriteData :: sDmemWriteResp :: sDmemReadAddr :: sDmemReadData :: Nil = Enum(8)
  val state = RegInit(sIdle)
  
  // Instruction memory transaction state
  val imem_addr_reg = RegInit(0.U(32.W))
  
  // Data memory transaction state  
  val dmem_addr_reg = RegInit(0.U(32.W))
  val dmem_wdata_reg = RegInit(0.U(32.W))
  val dmem_strb_reg = RegInit(0.U(4.W))
  val dmem_write_reg = RegInit(false.B)
  val dmem_read_reg = RegInit(false.B)
  
  // Address decoding
  def isBootROM(addr: UInt): Bool = addr < 0x00001000L.U  // 0x0000_0000 - 0x0000_0FFF
  def isGPIO(addr: UInt): Bool = (addr >= 0x10000000L.U) && (addr < 0x10000100L.U)  // 0x1000_0000 - 0x1000_00FF
  def isSRAM(addr: UInt): Bool = (addr >= 0x80000000L.U) && (addr < 0x80004000L.U)  // 0x8000_0000 - 0x8000_3FFF
  
  // Convert addresses to local offset
  def toSRAMAddr(addr: UInt): UInt = addr - 0x80000000L.U
  def toGPIOAddr(addr: UInt): UInt = addr - 0x10000000L.U
  
  // Default AXI signals
  bootrom.io.axi.awvalid := false.B
  bootrom.io.axi.awaddr := 0.U
  bootrom.io.axi.awprot := 0.U
  bootrom.io.axi.wvalid := false.B
  bootrom.io.axi.wdata := 0.U
  bootrom.io.axi.wstrb := 0.U
  bootrom.io.axi.bready := false.B
  bootrom.io.axi.arvalid := false.B
  bootrom.io.axi.araddr := 0.U
  bootrom.io.axi.arprot := 0.U
  bootrom.io.axi.rready := false.B
  
  sram.io.axi.awvalid := false.B
  sram.io.axi.awaddr := 0.U
  sram.io.axi.awprot := 0.U
  sram.io.axi.wvalid := false.B
  sram.io.axi.wdata := 0.U
  sram.io.axi.wstrb := 0.U
  sram.io.axi.bready := false.B
  sram.io.axi.arvalid := false.B
  sram.io.axi.araddr := 0.U
  sram.io.axi.arprot := 0.U
  sram.io.axi.rready := false.B
  
  gpio_controller.io.axi.awvalid := false.B
  gpio_controller.io.axi.awaddr := 0.U
  gpio_controller.io.axi.awprot := 0.U
  gpio_controller.io.axi.wvalid := false.B
  gpio_controller.io.axi.wdata := 0.U
  gpio_controller.io.axi.wstrb := 0.U
  gpio_controller.io.axi.bready := false.B
  gpio_controller.io.axi.arvalid := false.B
  gpio_controller.io.axi.araddr := 0.U
  gpio_controller.io.axi.arprot := 0.U
  gpio_controller.io.axi.rready := false.B
  
  // Default core signals
  core.io.imem_data := 0.U
  core.io.imem_valid := false.B
  core.io.dmem_rdata := 0.U
  core.io.dmem_valid := false.B
  
  // State machine for memory arbitration
  switch(state) {
    is(sIdle) {
      // Capture instruction fetch request
      imem_addr_reg := core.io.imem_addr
      state := sImemRead
    }
    
    is(sImemRead) {
      val addr = imem_addr_reg
      
      // Start read transaction to appropriate memory
      when(isBootROM(addr)) {
        bootrom.io.axi.arvalid := true.B
        bootrom.io.axi.araddr := addr
        
        when(bootrom.io.axi.arready) {
          state := sImemWait
        }
      }.elsewhen(isSRAM(addr)) {
        val sram_addr = toSRAMAddr(addr)
        sram.io.axi.arvalid := true.B
        sram.io.axi.araddr := sram_addr
        
        when(sram.io.axi.arready) {
          state := sImemWait
        }
      }.otherwise {
        // Invalid address - return NOP
        core.io.imem_data := 0x00000013L.U
        core.io.imem_valid := true.B
        
        // Check for data memory request
        when(core.io.dmem_write) {
          dmem_addr_reg := core.io.dmem_addr
          dmem_wdata_reg := core.io.dmem_wdata
          dmem_write_reg := true.B
          dmem_read_reg := false.B
          
          // Calculate write strobe
          val strb = WireDefault(0xF.U(4.W))
          switch(core.io.dmem_size) {
            is(0.U) { strb := (1.U << core.io.dmem_addr(1, 0)) }
            is(1.U) { strb := (3.U << (core.io.dmem_addr(1) << 1)) }
            is(2.U) { strb := 0xF.U }
          }
          dmem_strb_reg := strb
          state := sDmemWriteAddr
        }.elsewhen(core.io.dmem_read) {
          dmem_addr_reg := core.io.dmem_addr
          dmem_write_reg := false.B
          dmem_read_reg := true.B
          state := sDmemReadAddr
        }.otherwise {
          state := sIdle
        }
      }
    }
    
    is(sImemWait) {
      val addr = imem_addr_reg
      
      // Wait for read data
      when(isBootROM(addr)) {
        bootrom.io.axi.rready := true.B
        
        when(bootrom.io.axi.rvalid) {
          core.io.imem_data := bootrom.io.axi.rdata
          core.io.imem_valid := true.B
          
          // Check for data memory request
          when(core.io.dmem_write) {
            dmem_addr_reg := core.io.dmem_addr
            dmem_wdata_reg := core.io.dmem_wdata
            dmem_write_reg := true.B
            dmem_read_reg := false.B
            
            val strb = WireDefault(0xF.U(4.W))
            switch(core.io.dmem_size) {
              is(0.U) { strb := (1.U << core.io.dmem_addr(1, 0)) }
              is(1.U) { strb := (3.U << (core.io.dmem_addr(1) << 1)) }
              is(2.U) { strb := 0xF.U }
            }
            dmem_strb_reg := strb
            state := sDmemWriteAddr
          }.elsewhen(core.io.dmem_read) {
            dmem_addr_reg := core.io.dmem_addr
            dmem_write_reg := false.B
            dmem_read_reg := true.B
            state := sDmemReadAddr
          }.otherwise {
            state := sIdle
          }
        }
      }.elsewhen(isSRAM(addr)) {
        val sram_addr = toSRAMAddr(addr)
        sram.io.axi.rready := true.B
        
        when(sram.io.axi.rvalid) {
          core.io.imem_data := sram.io.axi.rdata
          core.io.imem_valid := true.B
          
          // Check for data memory request
          when(core.io.dmem_write) {
            dmem_addr_reg := core.io.dmem_addr
            dmem_wdata_reg := core.io.dmem_wdata
            dmem_write_reg := true.B
            dmem_read_reg := false.B
            
            val strb = WireDefault(0xF.U(4.W))
            switch(core.io.dmem_size) {
              is(0.U) { strb := (1.U << core.io.dmem_addr(1, 0)) }
              is(1.U) { strb := (3.U << (core.io.dmem_addr(1) << 1)) }
              is(2.U) { strb := 0xF.U }
            }
            dmem_strb_reg := strb
            state := sDmemWriteAddr
          }.elsewhen(core.io.dmem_read) {
            dmem_addr_reg := core.io.dmem_addr
            dmem_write_reg := false.B
            dmem_read_reg := true.B
            state := sDmemReadAddr
          }.otherwise {
            state := sIdle
          }
        }
      }
    }
    
    is(sDmemWriteAddr) {
      // SRAM and GPIO support writes
      when(isSRAM(dmem_addr_reg)) {
        val sram_addr = toSRAMAddr(dmem_addr_reg)
        sram.io.axi.awvalid := true.B
        sram.io.axi.awaddr := sram_addr
        
        when(sram.io.axi.awready) {
          state := sDmemWriteData
        }
      }.elsewhen(isGPIO(dmem_addr_reg)) {
        val gpio_addr = toGPIOAddr(dmem_addr_reg)
        gpio_controller.io.axi.awvalid := true.B
        gpio_controller.io.axi.awaddr := gpio_addr
        
        when(gpio_controller.io.axi.awready) {
          state := sDmemWriteData
        }
      }.otherwise {
        // Invalid write - signal completion
        core.io.dmem_valid := true.B
        state := sIdle
      }
    }
    
    is(sDmemWriteData) {
      when(isSRAM(dmem_addr_reg)) {
        sram.io.axi.wvalid := true.B
        sram.io.axi.wdata := dmem_wdata_reg
        sram.io.axi.wstrb := dmem_strb_reg
        
        when(sram.io.axi.wready) {
          state := sDmemWriteResp
        }
      }.elsewhen(isGPIO(dmem_addr_reg)) {
        gpio_controller.io.axi.wvalid := true.B
        gpio_controller.io.axi.wdata := dmem_wdata_reg
        gpio_controller.io.axi.wstrb := dmem_strb_reg
        
        when(gpio_controller.io.axi.wready) {
          state := sDmemWriteResp
        }
      }
    }
    
    is(sDmemWriteResp) {
      when(isSRAM(dmem_addr_reg)) {
        sram.io.axi.bready := true.B
        
        when(sram.io.axi.bvalid) {
          core.io.dmem_valid := true.B
          state := sIdle
        }
      }.elsewhen(isGPIO(dmem_addr_reg)) {
        gpio_controller.io.axi.bready := true.B
        
        when(gpio_controller.io.axi.bvalid) {
          core.io.dmem_valid := true.B
          state := sIdle
        }
      }
    }
    
    is(sDmemReadAddr) {
      // SRAM and GPIO support reads
      when(isSRAM(dmem_addr_reg)) {
        val sram_addr = toSRAMAddr(dmem_addr_reg)
        sram.io.axi.arvalid := true.B
        sram.io.axi.araddr := sram_addr
        
        when(sram.io.axi.arready) {
          state := sDmemReadData
        }
      }.elsewhen(isGPIO(dmem_addr_reg)) {
        val gpio_addr = toGPIOAddr(dmem_addr_reg)
        gpio_controller.io.axi.arvalid := true.B
        gpio_controller.io.axi.araddr := gpio_addr
        
        when(gpio_controller.io.axi.arready) {
          state := sDmemReadData
        }
      }.otherwise {
        // Invalid read - return 0
        core.io.dmem_rdata := 0.U
        core.io.dmem_valid := true.B
        state := sIdle
      }
    }
    
    is(sDmemReadData) {
      when(isSRAM(dmem_addr_reg)) {
        sram.io.axi.rready := true.B
        
        when(sram.io.axi.rvalid) {
          core.io.dmem_rdata := sram.io.axi.rdata
          core.io.dmem_valid := true.B
          state := sIdle
        }
      }.elsewhen(isGPIO(dmem_addr_reg)) {
        gpio_controller.io.axi.rready := true.B
        
        when(gpio_controller.io.axi.rvalid) {
          core.io.dmem_rdata := gpio_controller.io.axi.rdata
          core.io.dmem_valid := true.B
          state := sIdle
        }
      }
    }
  }
  
  // Debug outputs
  io.pc := core.io.imem_addr
  io.instruction := core.io.imem_data
}

// Legacy class name for compatibility
class PalmSoCTop extends PalmSoC
