package palmsoc.peripheral

import chisel3._
import chisel3.util._
import palmsoc.bus.{WishboneSlave, AXI4LiteSlave, AXI4LiteResp}
import palmsoc.config.AXI4LiteConfig

/**
 * UART Register Map
 * 
 * Base Address + Offset:
 * 0x00: DATA    - RX data (R) / TX data (W)
 * 0x04: STATUS  - UART status register (R)
 *                 Bit 0: rx_ready (data available)
 *                 Bit 1: tx_empty (transmitter empty)
 *                 Bit 2: tx_full (transmitter buffer full)
 *                 Bit 3: rx_overrun
 *                 Bit 4: frame_err
 * 0x08: CTRL    - UART control register (R/W)
 *                 Bit 0: tx_enable
 *                 Bit 1: rx_enable
 *                 Bit 2: tx_int_enable (interrupt when tx empty)
 *                 Bit 3: rx_int_enable (interrupt when rx ready)
 * 0x0C: DIVISOR - 16-bit clock divisor for baud rate (R/W)
 *                 Baud Rate = Clock Freq / (16 * DIVISOR)
 */
object UARTRegs {
  val DATA    = 0x00.U
  val STATUS  = 0x04.U
  val CTRL    = 0x08.U
  val DIVISOR = 0x0C.U
}

/**
 * Core UART Logic (Baud Generator, TX, RX)
 */
class UARTCore extends Module {
  val io = IO(new Bundle {
    val tx_enable = Input(Bool())
    val rx_enable = Input(Bool())
    val divisor   = Input(UInt(16.W))
    
    val tx_data   = Input(UInt(8.W))
    val tx_write  = Input(Bool())
    val tx_empty  = Output(Bool())
    val tx_full   = Output(Bool())
    
    val rx_data   = Output(UInt(8.W))
    val rx_read   = Input(Bool())
    val rx_ready  = Output(Bool())
    val rx_overrun = Output(Bool())
    val frame_err = Output(Bool())
    
    val tx        = Output(Bool())
    val rx        = Input(Bool())
  })

  // 1. Baud Rate Generator (16x oversampling clock tick)
  val div_counter = RegInit(0.U(16.W))
  val baud_tick = WireDefault(false.B)

  when(io.divisor > 0.U) {
    when(div_counter >= io.divisor - 1.U) {
      div_counter := 0.U
      baud_tick := true.B
    }.otherwise {
      div_counter := div_counter + 1.U
    }
  }.otherwise {
    div_counter := 0.U
  }

  // 2. Transmitter (TX) State Machine
  val sTxIdle :: sTxStart :: sTxData :: sTxStop :: Nil = Enum(4)
  val tx_state = RegInit(sTxIdle)
  val tx_tick_cnt = RegInit(0.U(4.W))
  val tx_bit_cnt = RegInit(0.U(3.W))
  val tx_buf = RegInit(0.U(8.W))
  val tx_buf_valid = RegInit(false.B)
  val tx_shift = RegInit(0.U(8.W))
  val tx_out = RegInit(true.B)

  io.tx := tx_out
  io.tx_full := tx_buf_valid
  io.tx_empty := !tx_buf_valid && (tx_state === sTxIdle)

  // Write to transmitter buffer
  when(io.tx_write && !tx_buf_valid) {
    tx_buf := io.tx_data
    tx_buf_valid := true.B
  }

  // Transmitter state updates
  when(baud_tick) {
    switch(tx_state) {
      is(sTxIdle) {
        tx_out := true.B
        when(tx_buf_valid && io.tx_enable) {
          tx_shift := tx_buf
          tx_buf_valid := false.B
          tx_state := sTxStart
          tx_tick_cnt := 0.U
          tx_out := false.B // Start bit (low)
        }
      }
      is(sTxStart) {
        tx_out := false.B
        when(tx_tick_cnt === 15.U) {
          tx_state := sTxData
          tx_tick_cnt := 0.U
          tx_bit_cnt := 0.U
          tx_out := tx_shift(0)
        }.otherwise {
          tx_tick_cnt := tx_tick_cnt + 1.U
        }
      }
      is(sTxData) {
        tx_out := tx_shift(0)
        when(tx_tick_cnt === 15.U) {
          tx_tick_cnt := 0.U
          when(tx_bit_cnt === 7.U) {
            tx_state := sTxStop
            tx_out := true.B // Stop bit (high)
          }.otherwise {
            tx_shift := tx_shift >> 1
            tx_bit_cnt := tx_bit_cnt + 1.U
          }
        }.otherwise {
          tx_tick_cnt := tx_tick_cnt + 1.U
        }
      }
      is(sTxStop) {
        tx_out := true.B
        when(tx_tick_cnt === 15.U) {
          tx_state := sTxIdle
        }.otherwise {
          tx_tick_cnt := tx_tick_cnt + 1.U
        }
      }
    }
  }

  // 3. Receiver (RX) State Machine
  val sRxIdle :: sRxStart :: sRxData :: sRxStop :: Nil = Enum(4)
  val rx_state = RegInit(sRxIdle)
  val rx_tick_cnt = RegInit(0.U(4.W))
  val rx_bit_cnt = RegInit(0.U(3.W))
  val rx_shift = RegInit(0.U(8.W))

  val rx_buf = RegInit(0.U(8.W))
  val rx_ready = RegInit(false.B)
  val rx_overrun = RegInit(false.B)
  val frame_err = RegInit(false.B)

  // Synchronizer for RX pin to prevent metastability
  val rx_sync = RegNext(RegNext(io.rx, true.B), true.B)

  io.rx_data := rx_buf
  io.rx_ready := rx_ready
  io.rx_overrun := rx_overrun
  io.frame_err := frame_err

  // Reading the RX buffer clears status flags
  when(io.rx_read) {
    rx_ready := false.B
    rx_overrun := false.B
    frame_err := false.B
  }

  // Receiver state updates
  when(baud_tick) {
    switch(rx_state) {
      is(sRxIdle) {
        when(!rx_sync && io.rx_enable) { // Detect start bit (falling edge)
          rx_state := sRxStart
          rx_tick_cnt := 0.U
        }
      }
      is(sRxStart) {
        when(rx_tick_cnt === 7.U) {
          // Verify start bit in the middle of interval
          when(rx_sync) {
            rx_state := sRxIdle // False start (glitch)
          }.otherwise {
            rx_tick_cnt := rx_tick_cnt + 1.U
          }
        }.elsewhen(rx_tick_cnt === 15.U) {
          rx_state := sRxData
          rx_tick_cnt := 0.U
          rx_bit_cnt := 0.U
        }.otherwise {
          rx_tick_cnt := rx_tick_cnt + 1.U
        }
      }
      is(sRxData) {
        when(rx_tick_cnt === 7.U) {
          // Sample data bit in the middle of interval
          rx_shift := Cat(rx_sync, rx_shift(7, 1))
          rx_tick_cnt := rx_tick_cnt + 1.U
        }.elsewhen(rx_tick_cnt === 15.U) {
          rx_tick_cnt := 0.U
          when(rx_bit_cnt === 7.U) {
            rx_state := sRxStop
          }.otherwise {
            rx_bit_cnt := rx_bit_cnt + 1.U
          }
        }.otherwise {
          rx_tick_cnt := rx_tick_cnt + 1.U
        }
      }
      is(sRxStop) {
        when(rx_tick_cnt === 7.U) {
          // Check stop bit (should be high)
          when(!rx_sync) {
            frame_err := true.B
          }
          rx_tick_cnt := rx_tick_cnt + 1.U
        }.elsewhen(rx_tick_cnt === 15.U) {
          when(rx_ready) {
            rx_overrun := true.B
          }.otherwise {
            rx_buf := rx_shift
            rx_ready := true.B
          }
          rx_state := sRxIdle
        }.otherwise {
          rx_tick_cnt := rx_tick_cnt + 1.U
        }
      }
    }
  }
}

/**
 * UART Controller with AXI4-Lite Interface
 */
class UART_AXI(config: AXI4LiteConfig = AXI4LiteConfig(32, 32)) 
    extends AXI4LiteSlave(config) {
  
  val tx = IO(Output(Bool()))
  val rx = IO(Input(Bool()))
  val interrupt = IO(Output(Bool()))

  // Control, Divisor & Status registers
  val ctrl_reg = RegInit(0.U(4.W))     // bits: [3: rx_int_en, 2: tx_int_en, 1: rx_en, 0: tx_en]
  val divisor_reg = RegInit(0.U(16.W)) // Baud divisor register

  // Instantiate Core
  val core = Module(new UARTCore)
  core.io.tx_enable := ctrl_reg(0)
  core.io.rx_enable := ctrl_reg(1)
  core.io.divisor   := divisor_reg
  core.io.rx        := rx
  tx                := core.io.tx

  // TX & RX bus trigger wires
  val tx_write_wire = WireDefault(false.B)
  val tx_data_wire = WireDefault(0.U(8.W))
  val rx_read_wire = WireDefault(false.B)

  core.io.tx_write := tx_write_wire
  core.io.tx_data  := tx_data_wire
  core.io.rx_read  := rx_read_wire

  // Connect interrupts (active if enabled and status condition is met)
  val tx_int_pending = ctrl_reg(2) && core.io.tx_empty
  val rx_int_pending = ctrl_reg(3) && core.io.rx_ready
  interrupt := tx_int_pending || rx_int_pending

  // AXI4-Lite State Machine
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
        val reg_addr = writeAddr(7, 0)
        
        // Handle strobe and write masks
        val write_mask = VecInit(Seq.tabulate(4) { i =>
          Mux(io.axi.wstrb(i), Fill(8, 1.U(1.W)), 0.U(8.W))
        }).asUInt
        val masked_data = io.axi.wdata & write_mask

        switch(reg_addr) {
          is(UARTRegs.DATA) {
            tx_write_wire := true.B
            tx_data_wire := masked_data(7, 0)
          }
          is(UARTRegs.CTRL) {
            ctrl_reg := masked_data(3, 0)
          }
          is(UARTRegs.DIVISOR) {
            divisor_reg := masked_data(15, 0)
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
      val reg_addr = readAddr(7, 0)
      val read_val = WireDefault(0.U(config.dataWidth.W))

      switch(reg_addr) {
        is(UARTRegs.DATA) {
          read_val := core.io.rx_data
          // Pop read buffer when AXI master is ready to accept the read data
          rx_read_wire := io.axi.rready
        }
        is(UARTRegs.STATUS) {
          // Status register construction:
          // [4]: frame_err, [3]: rx_overrun, [2]: tx_full, [1]: tx_empty, [0]: rx_ready
          read_val := Cat(0.U(27.W), core.io.frame_err, core.io.rx_overrun, core.io.tx_full, core.io.tx_empty, core.io.rx_ready)
        }
        is(UARTRegs.CTRL) {
          read_val := ctrl_reg
        }
        is(UARTRegs.DIVISOR) {
          read_val := divisor_reg
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
 * UART Controller with Wishbone Interface
 */
class UART_WB(addrWidth: Int = 32, dataWidth: Int = 32)
    extends WishboneSlave(addrWidth, dataWidth) {
  
  val tx = IO(Output(Bool()))
  val rx = IO(Input(Bool()))
  val interrupt = IO(Output(Bool()))

  // Control & Divisor registers
  val ctrl_reg = RegInit(0.U(4.W))     // bits: [3: rx_int_en, 2: tx_int_en, 1: rx_en, 0: tx_en]
  val divisor_reg = RegInit(0.U(16.W)) // Baud divisor

  // Instantiate Core
  val core = Module(new UARTCore)
  core.io.tx_enable := ctrl_reg(0)
  core.io.rx_enable := ctrl_reg(1)
  core.io.divisor   := divisor_reg
  core.io.rx        := rx
  tx                := core.io.tx

  // TX & RX bus trigger wires
  val tx_write_wire = WireDefault(false.B)
  val tx_data_wire = WireDefault(0.U(8.W))
  val rx_read_wire = WireDefault(false.B)

  core.io.tx_write := tx_write_wire
  core.io.tx_data  := tx_data_wire
  core.io.rx_read  := rx_read_wire

  // Connect interrupts
  val tx_int_pending = ctrl_reg(2) && core.io.tx_empty
  val rx_int_pending = ctrl_reg(3) && core.io.rx_ready
  interrupt := tx_int_pending || rx_int_pending

  // Wishbone bus transaction logic
  val busy = RegInit(false.B)
  val opAddr = Reg(UInt(addrWidth.W))
  val readData = Reg(UInt(dataWidth.W))

  io.wb.dat_r := readData
  io.wb.ack   := false.B
  io.wb.err   := false.B

  when(!busy) {
    when(io.wb.cyc && io.wb.stb) {
      busy := true.B
      opAddr := io.wb.adr
    }
  }.otherwise {
    val reg_addr = opAddr(7, 0)
    
    when(io.wb.we) {
      // Write transaction
      val write_data = io.wb.dat_w
      switch(reg_addr) {
        is(UARTRegs.DATA) {
          tx_write_wire := true.B
          tx_data_wire := write_data(7, 0)
        }
        is(UARTRegs.CTRL) {
          ctrl_reg := write_data(3, 0)
        }
        is(UARTRegs.DIVISOR) {
          divisor_reg := write_data(15, 0)
        }
      }
    }.otherwise {
      // Read transaction
      switch(reg_addr) {
        is(UARTRegs.DATA) {
          readData := core.io.rx_data
          rx_read_wire := true.B
        }
        is(UARTRegs.STATUS) {
          readData := Cat(0.U(27.W), core.io.frame_err, core.io.rx_overrun, core.io.tx_full, core.io.tx_empty, core.io.rx_ready)
        }
        is(UARTRegs.CTRL) {
          readData := ctrl_reg
        }
        is(UARTRegs.DIVISOR) {
          readData := divisor_reg
        }
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
 * UART Companion Object
 */
object UART {
  def apply(): UART_AXI = {
    new UART_AXI(AXI4LiteConfig(32, 32))
  }
  def apply(config: AXI4LiteConfig): UART_AXI = {
    new UART_AXI(config)
  }
}