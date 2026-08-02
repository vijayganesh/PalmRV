package palmsoc.peripheral

import chisel3._
import chisel3.util._
import palmsoc.bus.{WishboneSlave, AXI4LiteSlave, AXI4LiteResp}
import palmsoc.config.AXI4LiteConfig

/**
 * GPIO Register Map
 * 
 * Base Address + Offset:
 * 0x00: DATA      - GPIO data register (R/W)
 * 0x04: DIRECTION - GPIO direction register (R/W) - 0: input, 1: output
 * 0x08: OUTPUT    - GPIO output register (R/W)
 * 0x0C: INPUT     - GPIO input register (R)
 * 0x10: SET       - Set bits in OUTPUT (W)
 * 0x14: CLEAR     - Clear bits in OUTPUT (W)
 * 0x18: TOGGLE    - Toggle bits in OUTPUT (W)
 * 0x1C: INT_EN    - Interrupt enable (R/W)
 * 0x20: INT_TYPE  - Interrupt type (R/W) - 0: level, 1: edge
 * 0x24: INT_POL   - Interrupt polarity (R/W) - 0: low/falling, 1: high/rising
 * 0x28: INT_STAT  - Interrupt status (R/W1C - write 1 to clear)
 */
object GPIORegs {
  val DATA      = 0x00.U
  val DIRECTION = 0x04.U
  val OUTPUT    = 0x08.U
  val INPUT     = 0x0C.U
  val SET       = 0x10.U
  val CLEAR     = 0x14.U
  val TOGGLE    = 0x18.U
  val INT_EN    = 0x1C.U
  val INT_TYPE  = 0x20.U
  val INT_POL   = 0x24.U
  val INT_STAT  = 0x28.U
}

/**
 * GPIO with AXI4-Lite Interface
 * 
 * 18-pin General Purpose I/O controller with:
 * - Configurable direction per pin (input/output)
 * - Direct read/write access
 * - Set/Clear/Toggle operations
 * - Interrupt support (level/edge triggered)
 * 
 * @param config AXI4LiteConfig for bus interface
 * @param numPins Number of GPIO pins (default: 18)
 */
class GPIO_AXI(config: AXI4LiteConfig = AXI4LiteConfig(32, 32), numPins: Int = 18) 
    extends AXI4LiteSlave(config) {
  
  require(numPins > 0 && numPins <= 32, "GPIO pins must be between 1 and 32")
  
  // GPIO-specific pins (AXI interface inherited from parent as io.axi)
  val gpio_in = IO(Input(UInt(numPins.W)))
  val gpio_out = IO(Output(UInt(numPins.W)))
  val gpio_oe = IO(Output(UInt(numPins.W)))  // Output enable (direction)
  val interrupt = IO(Output(Bool()))
  
  // Registers
  val direction_reg = RegInit(0.U(numPins.W))  // 0: input, 1: output
  val output_reg = RegInit(0.U(numPins.W))
  val input_reg = RegNext(gpio_in)
  val int_en_reg = RegInit(0.U(numPins.W))
  val int_type_reg = RegInit(0.U(numPins.W))  // 0: level, 1: edge
  val int_pol_reg = RegInit(0.U(numPins.W))   // 0: low/falling, 1: high/rising
  val int_stat_reg = RegInit(0.U(numPins.W))
  
  // Previous input for edge detection
  val input_prev = RegNext(input_reg)
  
  // Detect interrupts
  val interrupt_detected = VecInit(Seq.tabulate(numPins) { i =>
    val pin_in = input_reg(i)
    val pin_prev = input_prev(i)
    val is_level = !int_type_reg(i)
    val is_edge = int_type_reg(i)
    val polarity = int_pol_reg(i)
    
    // Level-triggered: pin matches polarity
    val level_trig = is_level && (pin_in === polarity)
    
    // Edge-triggered: transition matches polarity
    val rising_edge = is_edge && !pin_prev && pin_in && polarity
    val falling_edge = is_edge && pin_prev && !pin_in && !polarity
    val edge_trig = rising_edge || falling_edge
    
    int_en_reg(i) && (level_trig || edge_trig)
  }).asUInt
  
  // Update interrupt status
  int_stat_reg := int_stat_reg | interrupt_detected
  
  // Interrupt output
  interrupt := (int_stat_reg & int_en_reg).orR
  
  // GPIO outputs
  gpio_out := output_reg
  gpio_oe := direction_reg
  
  // AXI4-Lite state machine
  val sIdle :: sWriteData :: sWriteResp :: sReadData :: Nil = Enum(4)
  val state = RegInit(sIdle)
  
  val writeAddr = RegInit(0.U(config.addrWidth.W))
  val readAddr = RegInit(0.U(config.addrWidth.W))
  val readData = RegInit(0.U(config.dataWidth.W))
  
  // Default outputs
  io.axi.awready := false.B
  io.axi.wready := false.B
  io.axi.bresp := AXI4LiteResp.OKAY
  io.axi.bvalid := false.B
  io.axi.arready := false.B
  io.axi.rdata := readData
  io.axi.rresp := AXI4LiteResp.OKAY
  io.axi.rvalid := false.B
  
  // Register address decode
  val reg_addr = WireDefault(0.U(8.W))
  
  // State machine
  switch(state) {
    is(sIdle) {
        //printf(p" Inside the Sidle only\n")
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
      //printf(p"GPIO: In sWriteData, wvalid=${io.axi.wvalid}\n")
      when(io.axi.wvalid && io.axi.wready) {
        //io.axi.wready := true.B
        //printf(p"GPIO: sWriteData processing - wdata=0x${Hexadecimal(io.axi.wdata)}, wstrb=0x${Hexadecimal(io.axi.wstrb)}\n")
        // Decode register address
        reg_addr := writeAddr(7, 0)
        
        // Apply write strobes
        val write_mask = VecInit(Seq.tabulate(4) { i =>
          Mux(io.axi.wstrb(i), Fill(8, 1.U(1.W)), 0.U(8.W))
        }).asUInt
        val masked_data = (io.axi.wdata & write_mask)(numPins - 1, 0)
        
        // Register writes
        switch(reg_addr) {
          is(GPIORegs.DATA) {
            // Write to data affects output for output pins
            output_reg := masked_data & direction_reg
          }
          is(GPIORegs.DIRECTION) {
            direction_reg := masked_data
          }
          is(GPIORegs.OUTPUT) {
            output_reg := masked_data
          }
          is(GPIORegs.SET) {
            output_reg := output_reg | masked_data
          }
          is(GPIORegs.CLEAR) {
            output_reg := output_reg & ~masked_data
          }
          is(GPIORegs.TOGGLE) {
            output_reg := output_reg ^ masked_data
          }
          is(GPIORegs.INT_EN) {
            int_en_reg := masked_data
          }
          is(GPIORegs.INT_TYPE) {
            int_type_reg := masked_data
          }
          is(GPIORegs.INT_POL) {
            int_pol_reg := masked_data
          }
          is(GPIORegs.INT_STAT) {
            // Write 1 to clear
            int_stat_reg := int_stat_reg & ~masked_data
            //printf(p"GPIO: Clearing INT_STAT. Old=${int_stat_reg}, MaskedData=${masked_data}, New=${int_stat_reg & ~masked_data}\n")
          }
        }
        
        //printf(p"GPIO: sWriteData -> sWriteResp\n")
        state := sWriteResp
      }
    }
    
    is(sWriteResp) {
      //printf(p"GPIO: In sWriteResp,  bReady =${io.axi.bready}\n")
      io.axi.bvalid := true.B
      io.axi.bresp := AXI4LiteResp.OKAY
      
      when(io.axi.bvalid && io.axi.bready) {
        //printf(p"GPIO: sWriteResp -> sIdle\n")
        state := sIdle
      }
    }
    
    is(sReadData) {
      //printf(p"GPIO: In sReadData\n")
      reg_addr := readAddr(7, 0)
      
      // Register reads
      val read_value = WireDefault(0.U(32.W))
      switch(reg_addr) {
        is(GPIORegs.DATA) {
          // Read data: output for output pins, input for input pins
          read_value := (output_reg & direction_reg) | (input_reg & ~direction_reg)
        }
        is(GPIORegs.DIRECTION) {
          read_value := direction_reg
        }
        is(GPIORegs.OUTPUT) {
          read_value := output_reg
        }
        is(GPIORegs.INPUT) {
          read_value := input_reg
        }
        is(GPIORegs.INT_EN) {
          read_value := int_en_reg
        }
        is(GPIORegs.INT_TYPE) {
          read_value := int_type_reg
        }
        is(GPIORegs.INT_POL) {
          read_value := int_pol_reg
        }
        is(GPIORegs.INT_STAT) {
          read_value := int_stat_reg
        }
      }
      
      readData := read_value
      io.axi.rdata := read_value
      io.axi.rvalid := true.B
      
      when(io.axi.rready) {
        //printf(p"GPIO: sReadData -> sIdle\n")
        state := sIdle
      }
    }
  }
  //printf(p"State: $state, WriteAddr: $writeAddr, ReadAddr: $readAddr, OutputReg: $output_reg, DirectionReg: $direction_reg\n")
  //printf(p"InputReg: $input_reg, IntEnReg: $int_en_reg, IntTypeReg: $int_type_reg, IntPolReg: $int_pol_reg, IntStatReg: $int_stat_reg\n")
  // print the AXI bus signals
  //printf(p"")
//printf(p"AXI: awvalid=${io.axi.awvalid} awready=${io.axi.awready} wvalid=${io.axi.wvalid} wready=${io.axi.wready} bvalid=${io.axi.bvalid} bready=${io.axi.bready} arvalid=${io.axi.arvalid} arready=${io.axi.arready} rvalid=${io.axi.rvalid} rready=${io.axi.rready}\n")
}

/**
 * GPIO with Wishbone Interface
 * 
 * 18-pin General Purpose I/O controller with Wishbone bus interface.
 */
class GPIO_WB(addrWidth: Int = 32, dataWidth: Int = 32, numPins: Int = 18)
    extends WishboneSlave(addrWidth, dataWidth) {
  
  require(numPins > 0 && numPins <= 32, "GPIO pins must be between 1 and 32")
  
  val gpio = IO(new Bundle {
    val gpio_in = Input(UInt(numPins.W))
    val gpio_out = Output(UInt(numPins.W))
    val gpio_oe = Output(UInt(numPins.W))
    val interrupt = Output(Bool())
  })
  
  // Registers
  val direction_reg = RegInit(0.U(numPins.W))
  val output_reg = RegInit(0.U(numPins.W))
  val input_reg = RegNext(gpio.gpio_in)
  val int_en_reg = RegInit(0.U(numPins.W))
  val int_type_reg = RegInit(0.U(numPins.W))
  val int_pol_reg = RegInit(0.U(numPins.W))
  val int_stat_reg = RegInit(0.U(numPins.W))
  
  val input_prev = RegNext(input_reg)
  
  // Interrupt detection
  val interrupt_detected = Wire(UInt(numPins.W))
  interrupt_detected := 0.U
  
  for (i <- 0 until numPins) {
    val pin_in = input_reg(i)
    val pin_prev = input_prev(i)
    val level_trig = !int_type_reg(i) && (pin_in === int_pol_reg(i))
    val edge_trig = int_type_reg(i) && (
      (pin_in && !pin_prev && int_pol_reg(i)) ||
      (!pin_in && pin_prev && !int_pol_reg(i))
    )
    
    when(int_en_reg(i) && (level_trig || edge_trig)) {
      interrupt_detected := interrupt_detected | (1.U << i)
    }
  }
  
  int_stat_reg := int_stat_reg | interrupt_detected
  gpio.interrupt := (int_stat_reg & int_en_reg).orR
  gpio.gpio_out := output_reg
  gpio.gpio_oe := direction_reg
  
  // Wishbone transaction
  val busy = RegInit(false.B)
  val opAddr = Reg(UInt(addrWidth.W))
  val readData = Reg(UInt(dataWidth.W))
  
  io.wb.dat_r := readData
  io.wb.ack := false.B
  io.wb.err := false.B
  
  when(!busy) {
    when(io.wb.cyc && io.wb.stb) {
      busy := true.B
      opAddr := io.wb.adr
    }
  }.otherwise {
    val reg_addr = opAddr(7, 0)
    
    when(io.wb.we) {
      // Write operation
      val write_data = io.wb.dat_w(numPins - 1, 0)
      
      switch(reg_addr) {
        is(GPIORegs.DATA) { output_reg := write_data & direction_reg }
        is(GPIORegs.DIRECTION) { direction_reg := write_data }
        is(GPIORegs.OUTPUT) { output_reg := write_data }
        is(GPIORegs.SET) { output_reg := output_reg | write_data }
        is(GPIORegs.CLEAR) { output_reg := output_reg & ~write_data }
        is(GPIORegs.TOGGLE) { output_reg := output_reg ^ write_data }
        is(GPIORegs.INT_EN) { int_en_reg := write_data }
        is(GPIORegs.INT_TYPE) { int_type_reg := write_data }
        is(GPIORegs.INT_POL) { int_pol_reg := write_data }
        is(GPIORegs.INT_STAT) { int_stat_reg := int_stat_reg & ~write_data }
      }
    }.otherwise {
      // Read operation
      switch(reg_addr) {
        is(GPIORegs.DATA) {
          readData := (output_reg & direction_reg) | (input_reg & ~direction_reg)
        }
        is(GPIORegs.DIRECTION) { readData := direction_reg }
        is(GPIORegs.OUTPUT) { readData := output_reg }
        is(GPIORegs.INPUT) { readData := input_reg }
        is(GPIORegs.INT_EN) { readData := int_en_reg }
        is(GPIORegs.INT_TYPE) { readData := int_type_reg }
        is(GPIORegs.INT_POL) { readData := int_pol_reg }
        is(GPIORegs.INT_STAT) { readData := int_stat_reg }
      }
    }
    
    io.wb.ack := true.B
    busy := false.B
  }
  
  when(!io.wb.cyc) {
    busy := false.B
  }
}

/**
 * GPIO Companion Object
 */
object GPIO {
  /**
   * Create GPIO with default 18 pins and AXI4-Lite interface
   */
  def apply(): GPIO_AXI = {
    new GPIO_AXI(AXI4LiteConfig(32, 32), 18)
  }
  
  /**
   * Create GPIO with custom number of pins
   */
  def apply(numPins: Int): GPIO_AXI = {
    new GPIO_AXI(AXI4LiteConfig(32, 32), numPins)
  }
}