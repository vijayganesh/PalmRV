package palmsoc.peripheral

import chisel3._
import chisel3.util._
import palmsoc.bus.{WishboneSlave, AXI4LiteSlave, AXI4LiteResp}
import palmsoc.config.AXI4LiteConfig

/**
 * I2C Register Map
 * 
 * Base Address + Offset:
 * 0x00: PRESCALE - Prescaler clock divisor (R/W)
 *                 Prescale = System Clock / (5 * SCL Frequency)
 * 0x04: CTRL     - I2C control register (R/W)
 *                 Bit 0: enable (enable I2C controller)
 *                 Bit 1: start (generate START or repeated START condition)
 *                 Bit 2: stop (generate STOP condition)
 *                 Bit 3: read (read byte from slave)
 *                 Bit 4: write (write byte to slave)
 *                 Bit 5: ack (ACK value to send: 0 = ACK, 1 = NACK in read)
 *                 Bit 6: int_en (interrupt enable)
 * 0x08: STATUS   - I2C status register (R/W1C - write 1 to clear interrupt)
 *                 Bit 0: busy (bus is busy)
 *                 Bit 1: rx_ack (ACK received from slave: 0 = ACK, 1 = NACK)
 *                 Bit 2: tx_empty (operation complete, ready for next command)
 *                 Bit 3: rx_full (received data is ready in DATA register)
 *                 Bit 4: arb_lost (arbitration lost)
 *                 Bit 5: int_pending (interrupt pending, write 1 to clear)
 * 0x0C: DATA     - Transmit / Receive data (R/W)
 * 0x10: ADDR     - 7-bit slave address + direction bit (R/W)
 *                 Bit [7:1]: Slave Address
 *                 Bit 0: Read/Write Direction (0 = Write, 1 = Read)
 */
object I2CRegs {
  val PRESCALE = 0x00.U
  val CTRL     = 0x04.U
  val STATUS   = 0x08.U
  val DATA     = 0x0C.U
  val ADDR     = 0x10.U
}

/**
 * Core I2C Master Controller Logic (Open-drain emulation, Clock divider, State Machine)
 */
class I2CCore extends Module {
  val io = IO(new Bundle {
    val enable      = Input(Bool())
    val prescale    = Input(UInt(16.W))
    
    // Command interface
    val cmd_start   = Input(Bool())
    val cmd_stop    = Input(Bool())
    val cmd_read    = Input(Bool())
    val cmd_write   = Input(Bool())
    val cmd_ack     = Input(Bool()) // 0 = ACK, 1 = NACK to send
    val cmd_valid   = Input(Bool())
    
    // Data & Address interface
    val tx_data     = Input(UInt(8.W))
    val rx_data     = Output(UInt(8.W))
    
    // Status interface
    val busy        = Output(Bool())
    val rx_ack      = Output(Bool()) // ACK received from slave (0 = ACK, 1 = NACK)
    val tx_empty    = Output(Bool())
    val rx_full     = Output(Bool())
    val arb_lost    = Output(Bool())
    
    // Physical pins (emulating open-drain)
    val scl_in      = Input(Bool())
    val scl_out     = Output(Bool())
    val scl_oe      = Output(Bool())
    val sda_in      = Input(Bool())
    val sda_out     = Output(Bool())
    val sda_oe      = Output(Bool())

    val clear_rx_full = Input(Bool())
  })

  // 1. Clock Divider (Generates quarter-cycle clock ticks for bit timing)
  val clk_cnt = RegInit(0.U(16.W))
  val quarter_tick = WireDefault(false.B)

  when(io.enable && io.prescale > 0.U) {
    when(clk_cnt >= io.prescale - 1.U) {
      clk_cnt := 0.U
      quarter_tick := true.B
    }.otherwise {
      clk_cnt := clk_cnt + 1.U
    }
  }.otherwise {
    clk_cnt := 0.U
  }

  // Quarter-cycle counter (0, 1, 2, 3)
  val q_cnt = RegInit(0.U(2.W))
  when(quarter_tick) {
    q_cnt := q_cnt + 1.U
  }

  // 2. State Machine
  val sIdle :: sStart :: sTx :: sRx :: sStop :: Nil = Enum(5)
  val state = RegInit(sIdle)

  // Saved command & data registers
  val r_start = RegInit(false.B)
  val r_stop = RegInit(false.B)
  val r_read = RegInit(false.B)
  val r_write = RegInit(false.B)
  val r_ack = RegInit(false.B)
  val r_data_buf = RegInit(0.U(8.W))
  val r_bit_cnt = RegInit(0.U(4.W))

  // Output status registers
  val r_busy = RegInit(false.B)
  val r_rx_ack = RegInit(false.B)
  val r_tx_empty = RegInit(true.B)
  val r_rx_full = RegInit(false.B)
  val r_rx_data = RegInit(0.U(8.W))
  val r_arb_lost = RegInit(false.B)

  io.busy := r_busy
  io.rx_ack := r_rx_ack
  io.tx_empty := r_tx_empty
  io.rx_full := r_rx_full
  io.rx_data := r_rx_data
  io.arb_lost := r_arb_lost

  // Pin drive registers (active high for driving low - open-drain emulation)
  val scl_drive_high = RegInit(true.B) // true: float high, false: drive low
  val sda_drive_high = RegInit(true.B) // true: float high, false: drive low

  // Open-drain outputs: drive low (oe = 1, out = 0) or float high (oe = 0, out = 0)
  io.scl_out := false.B
  io.scl_oe  := !scl_drive_high
  io.sda_out := false.B
  io.sda_oe  := !sda_drive_high

  // Synchronizers for physical inputs
  val scl_sync = RegNext(RegNext(io.scl_in, true.B), true.B)
  val sda_sync = RegNext(RegNext(io.sda_in, true.B), true.B)

  // Command latching in sIdle
  when(io.cmd_valid && io.enable && state === sIdle) {
    r_start    := io.cmd_start
    r_stop     := io.cmd_stop
    r_read     := io.cmd_read
    r_write    := io.cmd_write
    r_ack      := io.cmd_ack
    r_data_buf := io.tx_data
    r_busy     := true.B
    r_tx_empty := false.B
    r_bit_cnt  := 0.U
    q_cnt      := 0.U
    clk_cnt    := 0.U

    when(io.cmd_start) {
      state := sStart
    }.elsewhen(io.cmd_write) {
      state := sTx
    }.elsewhen(io.cmd_read) {
      state := sRx
    }.elsewhen(io.cmd_stop) {
      state := sStop
    }.otherwise {
      r_busy := false.B
      r_tx_empty := true.B
    }
  }

  // Clear read flags when data is read
  when(io.enable === false.B) {
    state := sIdle
    r_busy := false.B
    r_tx_empty := true.B
    r_rx_full := false.B
    scl_drive_high := true.B
    sda_drive_high := true.B
  }

  // State transitions and pin controls on quarter_tick
  when(io.enable && quarter_tick) {
    switch(state) {
      
      is(sStart) {
        // Generate START condition: SDA falling while SCL is high
        switch(q_cnt) {
          is(0.U) { sda_drive_high := true.B;  scl_drive_high := true.B }
          is(1.U) { sda_drive_high := false.B; scl_drive_high := true.B } // SDA falling edge
          is(2.U) { sda_drive_high := false.B; scl_drive_high := true.B }
          is(3.U) { 
            sda_drive_high := false.B; scl_drive_high := false.B // Pull SCL low, ready for bits
            // Transition to next pending action
            r_start := false.B
            when(r_write) {
              state := sTx
              r_bit_cnt := 0.U
            }.elsewhen(r_read) {
              state := sRx
              r_bit_cnt := 0.U
            }.otherwise {
              state := sIdle
              r_busy := false.B
              r_tx_empty := true.B
            }
          }
        }
      }

      is(sTx) {
        // Transmit 8 bits + receive 1 ACK/NACK bit (total 9 bits)
        switch(q_cnt) {
          is(0.U) {
            scl_drive_high := false.B
            when(r_bit_cnt < 8.U) {
              // Shift out MSB first
              sda_drive_high := r_data_buf((7.U - r_bit_cnt)(2, 0))
            }.otherwise {
              sda_drive_high := true.B // Release SDA to read ACK from slave
            }
          }
          is(1.U) {
            scl_drive_high := true.B // SCL rising edge
          }
          is(2.U) {
            scl_drive_high := true.B
            when(r_bit_cnt === 8.U) {
              // Sample ACK bit from slave (0 = ACK, 1 = NACK)
              r_rx_ack := sda_sync
            }
          }
          is(3.U) {
            scl_drive_high := false.B
            when(r_bit_cnt === 8.U) {
              r_write := false.B
              when(r_stop) {
                state := sStop
              }.otherwise {
                state := sIdle
                r_busy := false.B
                r_tx_empty := true.B
              }
            }.otherwise {
              r_bit_cnt := r_bit_cnt + 1.U
            }
          }
        }
      }

      is(sRx) {
        // Read 8 bits + transmit 1 ACK/NACK bit (total 9 bits)
        switch(q_cnt) {
          is(0.U) {
            scl_drive_high := false.B
            when(r_bit_cnt < 8.U) {
              sda_drive_high := true.B // Release SDA to read from slave
            }.otherwise {
              sda_drive_high := r_ack // Send our configured ACK/NACK value
            }
          }
          is(1.U) {
            scl_drive_high := true.B // SCL rising edge
          }
          is(2.U) {
            scl_drive_high := true.B
            when(r_bit_cnt < 8.U) {
              // Sample and shift in bit
              r_data_buf := Cat(r_data_buf(6, 0), sda_sync)
            }
          }
          is(3.U) {
            scl_drive_high := false.B
            when(r_bit_cnt === 8.U) {
              r_read := false.B
              r_rx_data := r_data_buf
              r_rx_full := true.B
              when(r_stop) {
                state := sStop
              }.otherwise {
                state := sIdle
                r_busy := false.B
                r_tx_empty := true.B
              }
            }.otherwise {
              r_bit_cnt := r_bit_cnt + 1.U
            }
          }
        }
      }

      is(sStop) {
        // Generate STOP condition: SDA rising while SCL is high
        switch(q_cnt) {
          is(0.U) { sda_drive_high := false.B; scl_drive_high := false.B }
          is(1.U) { sda_drive_high := false.B; scl_drive_high := true.B } // SCL rising edge
          is(2.U) { sda_drive_high := true.B;  scl_drive_high := true.B } // SDA rising edge
          is(3.U) {
            sda_drive_high := true.B;  scl_drive_high := true.B
            r_stop := false.B
            state := sIdle
            r_busy := false.B
            r_tx_empty := true.B
          }
        }
      }
    }
  }

  // Clear rx_full flag after it's acknowledged (typically on reading DATA register)
  when(io.clear_rx_full) {
    r_rx_full := false.B
  }
}

/**
 * I2C Controller with AXI4-Lite Interface
 */
class I2C_AXI(config: AXI4LiteConfig = AXI4LiteConfig(32, 32)) 
    extends AXI4LiteSlave(config) {
  
  // Physical pins
  val scl_in  = IO(Input(Bool()))
  val scl_out = IO(Output(Bool()))
  val scl_oe  = IO(Output(Bool()))
  val sda_in  = IO(Input(Bool()))
  val sda_out = IO(Output(Bool()))
  val sda_oe  = IO(Output(Bool()))
  
  val interrupt = IO(Output(Bool()))

  // Controller registers
  val prescale_reg = RegInit(0.U(16.W))
  val ctrl_reg     = RegInit(0.U(7.W)) // bits: [6: int_en, 5: ack, 4: write, 3: read, 2: stop, 1: start, 0: enable]
  val data_reg     = RegInit(0.U(8.W))
  val addr_reg     = RegInit(0.U(8.W)) // [7:1] Address, [0] R/W bit

  val int_pending_reg = RegInit(false.B)

  // Instantiate core
  val core = Module(new I2CCore)
  core.io.enable   := ctrl_reg(0)
  core.io.prescale := prescale_reg
  
  // Expose core pins
  core.io.scl_in := scl_in
  scl_out        := core.io.scl_out
  scl_oe         := core.io.scl_oe
  core.io.sda_in := sda_in
  sda_out        := core.io.sda_out
  sda_oe         := core.io.sda_oe

  // Command control logic
  val cmd_valid_wire = WireDefault(false.B)
  val clear_rx_full_wire = WireDefault(false.B)

  core.io.cmd_start := ctrl_reg(1)
  core.io.cmd_stop  := ctrl_reg(2)
  core.io.cmd_read  := ctrl_reg(3)
  core.io.cmd_write := ctrl_reg(4)
  core.io.cmd_ack   := ctrl_reg(5)
  core.io.cmd_valid := cmd_valid_wire
  core.io.clear_rx_full := clear_rx_full_wire

  // Core inputs: for a write operation, we either send address or data
  core.io.tx_data := Mux(ctrl_reg(1), addr_reg, data_reg)

  // Automatically clear active command bits in ctrl_reg when core starts processing (becomes busy)
  val prev_busy = RegNext(core.io.busy)
  when(core.io.busy && !prev_busy) {
    ctrl_reg := Cat(ctrl_reg(6, 5), 0.U(4.W), ctrl_reg(0)) // Clear write, read, stop, start bits
  }

  // Interrupt logic
  // Trigger interrupt when core completes an operation (tx_empty goes high) or rx_full is raised
  val prev_tx_empty = RegNext(core.io.tx_empty)
  val op_complete = core.io.tx_empty && !prev_tx_empty
  val rx_ready_flag = core.io.rx_full && !RegNext(core.io.rx_full)

  when(ctrl_reg(0) && ctrl_reg(6) && (op_complete || rx_ready_flag)) {
    int_pending_reg := true.B
  }

  interrupt := int_pending_reg

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

        val write_mask = VecInit(Seq.tabulate(4) { i =>
          Mux(io.axi.wstrb(i), Fill(8, 1.U(1.W)), 0.U(8.W))
        }).asUInt
        val masked_data = io.axi.wdata & write_mask

        switch(reg_addr) {
          is(I2CRegs.PRESCALE) {
            prescale_reg := masked_data(15, 0)
          }
          is(I2CRegs.CTRL) {
            ctrl_reg := masked_data(6, 0)
            cmd_valid_wire := true.B
          }
          is(I2CRegs.STATUS) {
            // Write 1 to clear interrupt pending
            when(masked_data(5)) {
              int_pending_reg := false.B
            }
          }
          is(I2CRegs.DATA) {
            data_reg := masked_data(7, 0)
          }
          is(I2CRegs.ADDR) {
            addr_reg := masked_data(7, 0)
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
        is(I2CRegs.PRESCALE) {
          read_val := prescale_reg
        }
        is(I2CRegs.CTRL) {
          read_val := ctrl_reg
        }
        is(I2CRegs.STATUS) {
          // Status register construction:
          // [5]: int_pending, [4]: arb_lost, [3]: rx_full, [2]: tx_empty, [1]: rx_ack, [0]: busy
          read_val := Cat(0.U(26.W), int_pending_reg, core.io.arb_lost, core.io.rx_full, core.io.tx_empty, core.io.rx_ack, core.io.busy)
        }
        is(I2CRegs.DATA) {
          read_val := core.io.rx_data
          clear_rx_full_wire := io.axi.rready // Acknowledge read data
        }
        is(I2CRegs.ADDR) {
          read_val := addr_reg
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
 * I2C Controller with Wishbone Interface
 */
class I2C_WB(addrWidth: Int = 32, dataWidth: Int = 32)
    extends WishboneSlave(addrWidth, dataWidth) {
  
  // Physical pins
  val scl_in  = IO(Input(Bool()))
  val scl_out = IO(Output(Bool()))
  val scl_oe  = IO(Output(Bool()))
  val sda_in  = IO(Input(Bool()))
  val sda_out = IO(Output(Bool()))
  val sda_oe  = IO(Output(Bool()))
  
  val interrupt = IO(Output(Bool()))

  // Controller registers
  val prescale_reg = RegInit(0.U(16.W))
  val ctrl_reg     = RegInit(0.U(7.W)) // [6: int_en, 5: ack, 4: write, 3: read, 2: stop, 1: start, 0: enable]
  val data_reg     = RegInit(0.U(8.W))
  val addr_reg     = RegInit(0.U(8.W))

  val int_pending_reg = RegInit(false.B)

  // Instantiate core
  val core = Module(new I2CCore)
  core.io.enable   := ctrl_reg(0)
  core.io.prescale := prescale_reg
  
  core.io.scl_in := scl_in
  scl_out        := core.io.scl_out
  scl_oe         := core.io.scl_oe
  core.io.sda_in := sda_in
  sda_out        := core.io.sda_out
  sda_oe         := core.io.sda_oe

  // Command control wires
  val cmd_valid_wire = WireDefault(false.B)
  val clear_rx_full_wire = WireDefault(false.B)

  core.io.cmd_start := ctrl_reg(1)
  core.io.cmd_stop  := ctrl_reg(2)
  core.io.cmd_read  := ctrl_reg(3)
  core.io.cmd_write := ctrl_reg(4)
  core.io.cmd_ack   := ctrl_reg(5)
  core.io.cmd_valid := cmd_valid_wire
  core.io.clear_rx_full := clear_rx_full_wire

  core.io.tx_data := Mux(ctrl_reg(1), addr_reg, data_reg)

  // Command self-clear when busy starts
  val prev_busy = RegNext(core.io.busy)
  when(core.io.busy && !prev_busy) {
    ctrl_reg := Cat(ctrl_reg(6, 5), 0.U(4.W), ctrl_reg(0))
  }

  // Interrupt logic
  val prev_tx_empty = RegNext(core.io.tx_empty)
  val op_complete = core.io.tx_empty && !prev_tx_empty
  val rx_ready_flag = core.io.rx_full && !RegNext(core.io.rx_full)

  when(ctrl_reg(0) && ctrl_reg(6) && (op_complete || rx_ready_flag)) {
    int_pending_reg := true.B
  }

  interrupt := int_pending_reg

  // Wishbone bus transaction
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
      // Write operation
      val write_data = io.wb.dat_w
      switch(reg_addr) {
        is(I2CRegs.PRESCALE) {
          prescale_reg := write_data(15, 0)
        }
        is(I2CRegs.CTRL) {
          ctrl_reg := write_data(6, 0)
          cmd_valid_wire := true.B
        }
        is(I2CRegs.STATUS) {
          when(write_data(5)) {
            int_pending_reg := false.B
          }
        }
        is(I2CRegs.DATA) {
          data_reg := write_data(7, 0)
        }
        is(I2CRegs.ADDR) {
          addr_reg := write_data(7, 0)
        }
      }
    }.otherwise {
      // Read operation
      switch(reg_addr) {
        is(I2CRegs.PRESCALE) {
          readData := prescale_reg
        }
        is(I2CRegs.CTRL) {
          readData := ctrl_reg
        }
        is(I2CRegs.STATUS) {
          readData := Cat(0.U(26.W), int_pending_reg, core.io.arb_lost, core.io.rx_full, core.io.tx_empty, core.io.rx_ack, core.io.busy)
        }
        is(I2CRegs.DATA) {
          readData := core.io.rx_data
          clear_rx_full_wire := true.B
        }
        is(I2CRegs.ADDR) {
          readData := addr_reg
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
 * I2C Companion Object
 */
object I2C {
  def apply(): I2C_AXI = {
    new I2C_AXI(AXI4LiteConfig(32, 32))
  }
  def apply(config: AXI4LiteConfig): I2C_AXI = {
    new I2C_AXI(config)
  }
}
