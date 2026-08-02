package palmsoc

import chisel3._
import chisel3.util._
import palmsoc.core.RV32Core
import palmsoc.memory.{BootROM_AXI, SRAM_AXI}
import palmsoc.peripheral.{GPIO_AXI, UART_AXI, I2C_AXI, DMA_AXI}
import palmsoc.interrupt.InterruptController
import palmsoc.bus.PalmVMainInterconnect
import palmsoc.bus.PalmVPeripheralInterconnect
import palmsoc.bus.PalmVAcceleratorInterconnect
import palmsoc.config.AXI4LiteConfig
import chisel3.experimental.Analog

/**
 * Configuration class for PalmSoC
 */
case class ConfigurablePalmSoCConfig(
  hasGPIO: Boolean = true,
  hasUART: Boolean = true,
  hasI2C: Boolean = true,
  coreConfig: palmsoc.config.SoCConfig = palmsoc.config.DefaultSoCConfig()
)

/**
 * Configurable PalmSoC top-level module (without accelerator)
 */
class ConfigurablePalmSoC(
  val socConfig: ConfigurablePalmSoCConfig = ConfigurablePalmSoCConfig(),
  val bootromInit: Option[Seq[UInt]] = None
) extends Module {
  val io = IO(new Bundle {
    // Debug and monitoring outputs
    val pc = Output(UInt(32.W))
    val instruction = Output(UInt(32.W))
    val instret = Output(Bool())
    
    // GPIO interface
    val gpio_out = Output(UInt(18.W))
    val gpio_in = Input(UInt(18.W))
    val gpio_oe = Output(UInt(18.W))
    val gpio_interrupt = Output(Bool())
    
    // UART interface
    val uart_tx = Output(Bool())
    val uart_rx = Input(Bool())
    val uart_interrupt = Output(Bool())
    
    // I2C interface
    val i2c_scl_in = Input(Bool())
    val i2c_sda_in = Input(Bool())
    val i2c_scl_oe = Output(Bool())
    val i2c_sda_oe = Output(Bool())
    val i2c_interrupt = Output(Bool())
  })
  
  // Set default values for top-level IOs in case their respective submodules are disabled
  io.uart_tx := true.B
  io.uart_interrupt := false.B
  io.i2c_scl_oe := false.B
  io.i2c_sda_oe := false.B
  io.i2c_interrupt := false.B
  io.gpio_interrupt := false.B
  io.gpio_out := 0.U
  io.gpio_oe := 0.U

  // Interrupt wires to collect from peripheral modules
  val uart_intr = WireDefault(false.B)
  val i2c_intr = WireDefault(false.B)
  val gpio_intr = WireDefault(false.B)
  val lstm_intr = false.B // LSTM Accelerator removed

  // AXI Configuration
  val axiConfig = AXI4LiteConfig(32, 32)

  // Helper to connect flat AXI4LiteIO to nested AXI4LiteMasterInterface
  private def connectAxi(flat: palmsoc.bus.AXI4LiteIO, nested: palmsoc.config.AXI4LiteMasterInterface): Unit = {
    flat.awaddr  := nested.aw.awaddr
    flat.awprot  := nested.aw.awprot
    flat.awvalid := nested.aw.awvalid
    nested.aw.awready := flat.awready
    
    flat.wdata  := nested.w.wdata
    flat.wstrb  := nested.w.wstrb
    flat.wvalid := nested.w.wvalid
    nested.w.wready := flat.wready
    
    nested.b.bresp  := flat.bresp
    nested.b.bvalid := flat.bvalid
    flat.bready     := nested.b.bready
    
    flat.araddr  := nested.ar.araddr
    flat.arprot  := nested.ar.arprot
    flat.arvalid := nested.ar.arvalid
    nested.ar.arready := flat.arready
    
    nested.r.rdata  := flat.rdata
    nested.r.rresp  := flat.rresp
    nested.r.rvalid := flat.rvalid
    flat.rready     := nested.r.rready
  }

  // Helper to connect flat AXI4LiteIO master to nested AXI4LiteMasterInterface crossbar port
  private def connectAxiMaster(flat: palmsoc.bus.AXI4LiteIO, nested: palmsoc.config.AXI4LiteMasterInterface): Unit = {
    nested.aw.awaddr  := flat.awaddr
    nested.aw.awprot  := flat.awprot
    nested.aw.awvalid := flat.awvalid
    flat.awready      := nested.aw.awready
    
    nested.w.wdata    := flat.wdata
    nested.w.wstrb    := flat.wstrb
    nested.w.wvalid   := flat.wvalid
    flat.wready       := nested.w.wready
    
    flat.bresp        := nested.b.bresp
    flat.bvalid       := nested.b.bvalid
    nested.b.bready   := flat.bready
    
    nested.ar.araddr  := flat.araddr
    nested.ar.arprot  := flat.arprot
    nested.ar.arvalid := flat.arvalid
    flat.arready      := nested.ar.arready
    
    flat.rdata        := nested.r.rdata
    flat.rresp        := nested.r.rresp
    flat.rvalid       := nested.r.rvalid
    nested.r.rready   := flat.rready
  }

  // Helper to tie off unused/disabled slave ports safely (returns DECERR response and prevents hangs)
  private def tieOffSlave(nested: palmsoc.config.AXI4LiteMasterInterface): Unit = {
    nested.aw.awready := true.B
    nested.w.wready   := true.B
    nested.ar.arready := true.B
    
    nested.b.bresp  := palmsoc.config.AXI4LiteResp.DECERR
    nested.b.bvalid := true.B
    
    nested.r.rdata  := 0.U
    nested.r.rresp  := palmsoc.config.AXI4LiteResp.DECERR
    nested.r.rvalid := true.B
  }
  
  // 1. Instantiate the CPU Core
  val core = Module(new RV32Core(socConfig.coreConfig))
  io.pc := core.io.imem_addr
  io.instruction := core.io.imem_data
  io.instret := core.io.instret
  
  // 2. Instantiate crossbar interconnects
  val mainXbar = Module(new PalmVMainInterconnect)
  val peripheralXbar = Module(new PalmVPeripheralInterconnect)
  val acceleratorXbar = Module(new PalmVAcceleratorInterconnect)
  
  // 3. Connect Main Crossbar Slaves
  
  // Slave 0: Boot ROM (4 KB / 1K words)
  val bootrom = Module(new BootROM_AXI(axiConfig, 1024, bootromInit))
  connectAxi(bootrom.io.axi, mainXbar.io.slaves(0))
  
  // Slave 1: Flash (Tied off)
  tieOffSlave(mainXbar.io.slaves(1))
  
  // Slave 2: SRAM (16 KB / 4K words)
  val sram = Module(new SRAM_AXI(axiConfig, 4096))
  connectAxi(sram.io.axi, mainXbar.io.slaves(2))
  
  // Slave 3: Peripheral Sub-Crossbar
  peripheralXbar.io.masters(0) <> mainXbar.io.slaves(3)
  
  // Slave 4: Accelerator Sub-Crossbar
  acceleratorXbar.io.masters(0) <> mainXbar.io.slaves(4)
  
  // Slave 5: DMA Controller Configuration Port
  val dma = Module(new DMA_AXI(axiConfig, 4))
  connectAxi(dma.io.axi_slave, mainXbar.io.slaves(5))
  
  // Slave 6: Interrupt Controller
  val intc = Module(new InterruptController(axiConfig, 5))
  connectAxi(intc.io.axi, mainXbar.io.slaves(6))
  intc.io_interrupts := Cat(lstm_intr, dma.io.interrupt, gpio_intr, i2c_intr, uart_intr)
  core.io.external_interrupt := intc.io_ext_int
  
  tieOffSlave(mainXbar.io.slaves(7))
  
  // 4. Connect Peripheral Crossbar Slaves
  
  // Slave 0: UART0
  if (socConfig.hasUART) {
    val uart = Module(new UART_AXI(axiConfig))
    connectAxi(uart.io.axi, peripheralXbar.io.slaves(0))
    uart.rx := io.uart_rx
    io.uart_tx := uart.tx
    io.uart_interrupt := uart.interrupt
    uart_intr := uart.interrupt
  } else {
    tieOffSlave(peripheralXbar.io.slaves(0))
  }
  
  // Slave 1: UART1 (Tied off)
  tieOffSlave(peripheralXbar.io.slaves(1))
  
  // Slaves 2, 3: SPI0, SPI1 (Tied off)
  tieOffSlave(peripheralXbar.io.slaves(2))
  tieOffSlave(peripheralXbar.io.slaves(3))
  
  // Slave 4: I2C0
  if (socConfig.hasI2C) {
    val i2c = Module(new I2C_AXI(axiConfig))
    connectAxi(i2c.io.axi, peripheralXbar.io.slaves(4))
    i2c.scl_in := io.i2c_scl_in
    i2c.sda_in := io.i2c_sda_in
    io.i2c_scl_oe := i2c.scl_oe
    io.i2c_sda_oe := i2c.sda_oe
    io.i2c_interrupt := i2c.interrupt
    i2c_intr := i2c.interrupt
  } else {
    tieOffSlave(peripheralXbar.io.slaves(4))
  }
  
  // Slave 5: I2C1 (Tied off)
  tieOffSlave(peripheralXbar.io.slaves(5))
  
  // Slave 6: GPIO
  if (socConfig.hasGPIO) {
    val gpio_controller = Module(new GPIO_AXI(axiConfig, 18))
    connectAxi(gpio_controller.io.axi, peripheralXbar.io.slaves(6))
    
    io.gpio_out := gpio_controller.gpio_out
    io.gpio_oe := gpio_controller.gpio_oe
    gpio_controller.gpio_in := io.gpio_in
    io.gpio_interrupt := gpio_controller.interrupt
    gpio_intr := gpio_controller.interrupt
  } else {
    tieOffSlave(peripheralXbar.io.slaves(6))
  }
  
  // Slaves 7, 8, 9: PWM, ADC, Timer (Tied off)
  tieOffSlave(peripheralXbar.io.slaves(7))
  tieOffSlave(peripheralXbar.io.slaves(8))
  tieOffSlave(peripheralXbar.io.slaves(9))
  
  // 5. Connect Accelerator Crossbar Slaves (All tied off/disabled)
  for (i <- 0 until 7) {
    tieOffSlave(acceleratorXbar.io.slaves(i))
  }
  
  // 6. Connect CPU Core to Main Crossbar Master 0 using Bridge State Machine
  
  // CPU Master
  val cpuAxi = mainXbar.io.masters(0)
  // DMA Master
  connectAxiMaster(dma.io.axi_master, mainXbar.io.masters(1))
  
  val sBridgeIdle :: sBridgeImemRead :: sBridgeImemWait :: sBridgeImemDeliver :: sBridgeDmemWriteAddr :: sBridgeDmemWriteData :: sBridgeDmemWriteResp :: sBridgeDmemReadAddr :: sBridgeDmemReadData :: sBridgeDmemDeliver :: Nil = Enum(10)
  val bridgeState = RegInit(sBridgeIdle)
  
  // AXI address and write registers
  val imem_addr_reg = RegInit(0.U(32.W))
  val dmem_addr_reg = RegInit(0.U(32.W))
  val dmem_wdata_reg = RegInit(0.U(32.W))
  val dmem_strb_reg = RegInit(0.U(4.W))
  val dmem_just_completed = RegInit(false.B)
  
  // Default values
  cpuAxi.aw.awaddr := 0.U
  cpuAxi.aw.awprot := 0.U
  cpuAxi.aw.awvalid := false.B
  cpuAxi.w.wdata := 0.U
  cpuAxi.w.wstrb := 0.U
  cpuAxi.w.wvalid := false.B
  cpuAxi.b.bready := false.B
  cpuAxi.ar.araddr := 0.U
  cpuAxi.ar.arprot := 0.U
  cpuAxi.ar.arvalid := false.B
  cpuAxi.r.rready := false.B
  
  // Pipelined valid/data registers to break combinatorial control path
  val core_imem_valid_reg = RegInit(false.B)
  val core_imem_data_reg = Reg(UInt(32.W))
  val core_dmem_valid_reg = RegInit(false.B)
  val core_dmem_rdata_reg = Reg(UInt(32.W))
  
  core.io.imem_data := core_imem_data_reg
  core.io.imem_valid := core_imem_valid_reg
  core.io.dmem_rdata := core_dmem_rdata_reg
  core.io.dmem_valid := core_dmem_valid_reg
  
  val stall_counter = RegInit(0.U(32.W))
  when(bridgeState =/= sBridgeIdle && bridgeState === RegNext(bridgeState)) {
    stall_counter := stall_counter + 1.U
    when(stall_counter === 100.U) {
      printf("BRIDGE STALL DETECTED: state=%d, imem_addr=%x, dmem_addr=%x, awvalid=%d, wvalid=%d, bvalid=%d, arvalid=%d, rvalid=%d\n",
        bridgeState.asUInt, imem_addr_reg, dmem_addr_reg,
        cpuAxi.aw.awvalid, cpuAxi.w.wvalid, cpuAxi.b.bvalid,
        cpuAxi.ar.arvalid, cpuAxi.r.rvalid)
    }
  }.otherwise {
    stall_counter := 0.U
  }
  
  switch(bridgeState) {
    is(sBridgeIdle) {
      // Prioritize data memory accesses over starting a new instruction fetch
      // Only do this if we didn't just complete a data transaction in the previous cycle,
      // which allows the CPU pipeline to advance and retire the completed transaction.
      when((core.io.dmem_write || core.io.dmem_read) && !dmem_just_completed) {
        when(core.io.dmem_write) {
          dmem_addr_reg := core.io.dmem_addr
          dmem_wdata_reg := core.io.dmem_wdata
          
          val strb = WireDefault(0xF.U(4.W))
          switch(core.io.dmem_size) {
            is(0.U) { strb := (1.U << core.io.dmem_addr(1, 0)) }
            is(1.U) { strb := (3.U << (core.io.dmem_addr(1) << 1)) }
            is(2.U) { strb := 0xF.U }
          }
          dmem_strb_reg := strb
          bridgeState := sBridgeDmemWriteAddr
        }.otherwise { // core.io.dmem_read
          dmem_addr_reg := core.io.dmem_addr
          bridgeState := sBridgeDmemReadAddr
        }
      }.otherwise {
        // Reset the retirement flag and start the next instruction fetch
        dmem_just_completed := false.B
        imem_addr_reg := core.io.imem_addr
        bridgeState := sBridgeImemRead
      }
    }
    
    is(sBridgeImemRead) {
      cpuAxi.ar.araddr := imem_addr_reg
      cpuAxi.ar.arvalid := true.B
      
      when(cpuAxi.ar.arready) {
        bridgeState := sBridgeImemWait
      }
    }
    
    is(sBridgeImemWait) {
      cpuAxi.r.rready := true.B
      
      when(cpuAxi.r.rvalid) {
        val is_stale = imem_addr_reg =/= core.io.imem_addr
        
        when(is_stale) {
          // Address branched/flushed during this AXI read; discard stale instruction response and re-fetch from new PC
          bridgeState := sBridgeIdle
        }.otherwise {
          core_imem_data_reg := cpuAxi.r.rdata
          core_imem_valid_reg := true.B
          bridgeState := sBridgeImemDeliver
        }
      }
    }
    
    is(sBridgeImemDeliver) {
      core_imem_valid_reg := false.B
      
      // Immediately examine data memory transactions that might have been triggered 
      // by the now-valid instruction (or previous instructions)
      when(core.io.dmem_write) {
        dmem_addr_reg := core.io.dmem_addr
        dmem_wdata_reg := core.io.dmem_wdata
        
        val strb = WireDefault(0xF.U(4.W))
        switch(core.io.dmem_size) {
          is(0.U) { strb := (1.U << core.io.dmem_addr(1, 0)) }
          is(1.U) { strb := (3.U << (core.io.dmem_addr(1) << 1)) }
          is(2.U) { strb := 0xF.U }
        }
        dmem_strb_reg := strb
        bridgeState := sBridgeDmemWriteAddr
      }.elsewhen(core.io.dmem_read) {
        dmem_addr_reg := core.io.dmem_addr
        bridgeState := sBridgeDmemReadAddr
      }.otherwise {
        // Normal sequential instruction step
        bridgeState := sBridgeIdle
      }
    }
    
    is(sBridgeDmemWriteAddr) {
      cpuAxi.aw.awaddr := dmem_addr_reg
      cpuAxi.aw.awvalid := true.B
      
      when(cpuAxi.aw.awready) {
        bridgeState := sBridgeDmemWriteData
      }
    }
    
    is(sBridgeDmemWriteData) {
      cpuAxi.w.wdata := dmem_wdata_reg
      cpuAxi.w.wstrb := dmem_strb_reg
      cpuAxi.w.wvalid := true.B
      
      when(cpuAxi.w.wready) {
        bridgeState := sBridgeDmemWriteResp
      }
    }
    
    is(sBridgeDmemWriteResp) {
      cpuAxi.b.bready := true.B
      
      when(cpuAxi.b.bvalid) {
        core_dmem_valid_reg := true.B
        dmem_just_completed := true.B
        bridgeState := sBridgeDmemDeliver
      }
    }
    
    is(sBridgeDmemReadAddr) {
      cpuAxi.ar.araddr := dmem_addr_reg
      cpuAxi.ar.arvalid := true.B
      
      when(cpuAxi.ar.arready) {
        bridgeState := sBridgeDmemReadData
      }
    }
    
    is(sBridgeDmemReadData) {
      cpuAxi.r.rready := true.B
      
      when(cpuAxi.r.rvalid) {
        core_dmem_rdata_reg := cpuAxi.r.rdata
        core_dmem_valid_reg := true.B
        dmem_just_completed := true.B
        bridgeState := sBridgeDmemDeliver
      }
    }
    
    is(sBridgeDmemDeliver) {
      core_dmem_valid_reg := false.B
      bridgeState := sBridgeIdle
    }
  }
}
