package palmsoc.peripheral

import chisel3._
import chisel3.util._
import palmsoc.config.{AXI4LiteConfig, AXI4LiteResp}
import palmsoc.bus.{AXI4LiteIO, AXI4LiteSlave}

/**
 * 4-Channel DMA Controller (Low Power Edge Focus)
 * 
 * Features:
 * - AXI4-Lite Slave interface for CPU configuration
 * - AXI4-Lite Master interface for memory/peripheral data transfers
 * - 4 Independent Channels with strict priority (Ch 0 highest)
 * - Ultra-low power state machine: parks in idle and zeroes outputs when disabled
 */
class DMA_AXI(config: AXI4LiteConfig, numChannels: Int = 4) extends Module {
  val io = IO(new Bundle {
    // AXI Slave interface for CPU configuration
    val axi_slave = Flipped(new AXI4LiteIO(config))
    // AXI Master interface for DMA transfers
    val axi_master = new AXI4LiteIO(config)
    // Combined interrupt output
    val interrupt = Output(Bool())
  })
  
  // -------------------------------------------------------------------------
  // 1. Channel Registers
  // -------------------------------------------------------------------------
  
  // Register arrays (one per channel)
  val srcAddrRegs = RegInit(VecInit(Seq.fill(numChannels)(0.U(32.W))))
  val dstAddrRegs = RegInit(VecInit(Seq.fill(numChannels)(0.U(32.W))))
  val lenRegs = RegInit(VecInit(Seq.fill(numChannels)(0.U(32.W))))
  
  // CTRL: [0] EN, [1] INT_EN, [2] SRC_INC, [3] DST_INC
  val ctrlRegs = RegInit(VecInit(Seq.fill(numChannels)(0.U(32.W))))
  
  // STAT: [0] BUSY, [1] DONE, [2] ERR
  val statRegs = RegInit(VecInit(Seq.fill(numChannels)(0.U(32.W))))
  
  // -------------------------------------------------------------------------
  // 2. AXI Slave Interface (CPU Configuration)
  // -------------------------------------------------------------------------
  
  val sWriteIdle :: sWriteAddr :: sWriteData :: sWriteResp :: Nil = Enum(4)
  val sReadIdle :: sReadAddr :: sReadData :: Nil = Enum(3)
  
  val slaveWriteState = RegInit(sWriteIdle)
  val slaveReadState = RegInit(sReadIdle)
  
  val writeAddrReg = RegInit(0.U(32.W))
  val readAddrReg = RegInit(0.U(32.W))
  val readDataReg = RegInit(0.U(32.W))
  
  // Write channel defaults
  io.axi_slave.awready := false.B
  io.axi_slave.wready := false.B
  io.axi_slave.bvalid := false.B
  io.axi_slave.bresp := AXI4LiteResp.OKAY
  
  // Read channel defaults
  io.axi_slave.arready := false.B
  io.axi_slave.rvalid := false.B
  io.axi_slave.rdata := readDataReg
  io.axi_slave.rresp := AXI4LiteResp.OKAY
  
  // Slave Write State Machine
  switch(slaveWriteState) {
    is(sWriteIdle) {
      io.axi_slave.awready := true.B
      when(io.axi_slave.awvalid) {
        writeAddrReg := io.axi_slave.awaddr
        slaveWriteState := sWriteData
      }
    }
    is(sWriteData) {
      io.axi_slave.wready := true.B
      when(io.axi_slave.wvalid) {
        val ch = writeAddrReg(6, 5) // Truncate to 2 bits to match Vec size 4
        val offset = writeAddrReg(4, 0)
        
        when(ch < numChannels.U) {
          switch(offset) {
            is(0x00.U) { srcAddrRegs(ch) := io.axi_slave.wdata }
            is(0x04.U) { dstAddrRegs(ch) := io.axi_slave.wdata }
            is(0x08.U) { lenRegs(ch) := io.axi_slave.wdata }
            is(0x0C.U) { 
              ctrlRegs(ch) := io.axi_slave.wdata 
              // If enabling, clear DONE and ERR flags
              when(io.axi_slave.wdata(0)) {
                statRegs(ch) := statRegs(ch) & ~6.U(32.W)
              }
            }
            is(0x10.U) { 
              // Write 1 to clear status bits (W1C logic for DONE and ERR)
              val clearMask = io.axi_slave.wdata(2, 1)
              val keepMask = ~Cat(clearMask, 0.U(1.W))
              statRegs(ch) := statRegs(ch) & keepMask
            }
          }
        }
        slaveWriteState := sWriteResp
      }
    }
    is(sWriteResp) {
      io.axi_slave.bvalid := true.B
      when(io.axi_slave.bready) {
        slaveWriteState := sWriteIdle
      }
    }
  }
  
  // Slave Read State Machine
  switch(slaveReadState) {
    is(sReadIdle) {
      io.axi_slave.arready := true.B
      when(io.axi_slave.arvalid) {
        readAddrReg := io.axi_slave.araddr
        
        val ch = io.axi_slave.araddr(6, 5) // Truncate to 2 bits
        val offset = io.axi_slave.araddr(4, 0)
        
        readDataReg := 0.U // Default to 0 (Zero-Toggle when unused)
        when(ch < numChannels.U) {
          switch(offset) {
            is(0x00.U) { readDataReg := srcAddrRegs(ch) }
            is(0x04.U) { readDataReg := dstAddrRegs(ch) }
            is(0x08.U) { readDataReg := lenRegs(ch) }
            is(0x0C.U) { readDataReg := ctrlRegs(ch) }
            is(0x10.U) { readDataReg := statRegs(ch) }
          }
        }
        slaveReadState := sReadData
      }
    }
    is(sReadData) {
      io.axi_slave.rvalid := true.B
      when(io.axi_slave.rready) {
        slaveReadState := sReadIdle
      }
    }
  }
  
  // -------------------------------------------------------------------------
  // 3. DMA Master Engine (Low Power State Machine)
  // -------------------------------------------------------------------------
  
  val mIdle :: mReadReq :: mReadWait :: mWriteReq :: mWriteWait :: mUpdate :: Nil = Enum(6)
  val masterState = RegInit(mIdle)
  
  // Internal buffers
  val activeCh = RegInit(0.U(log2Ceil(numChannels).W))
  val dataBuf = RegInit(0.U(32.W))
  
  // Arbitration: Find highest priority active channel (Ch 0 highest)
  val chActive = Wire(Vec(numChannels, Bool()))
  for (i <- 0 until numChannels) {
    chActive(i) := ctrlRegs(i)(0) && lenRegs(i) =/= 0.U
  }
  
  val nextActiveCh = PriorityEncoder(chActive)
  val anyActive = chActive.asUInt =/= 0.U
  
  // Master Output Defaults (Low Power Clamping)
  io.axi_master.araddr := 0.U
  io.axi_master.arprot := 0.U
  io.axi_master.arvalid := false.B
  
  io.axi_master.awaddr := 0.U
  io.axi_master.awprot := 0.U
  io.axi_master.awvalid := false.B
  
  io.axi_master.wdata := 0.U
  io.axi_master.wstrb := 0.U
  io.axi_master.wvalid := false.B
  
  io.axi_master.rready := false.B
  io.axi_master.bready := false.B
  
  // Master State Machine
  switch(masterState) {
    is(mIdle) {
      // Dynamic Clock Gating Equivalent: state machine sleeps here if !anyActive
      when(anyActive) {
        activeCh := nextActiveCh
        // Set busy flag
        statRegs(nextActiveCh) := statRegs(nextActiveCh) | 1.U
        masterState := mReadReq
      }
    }
    
    is(mReadReq) {
      io.axi_master.araddr := srcAddrRegs(activeCh)
      io.axi_master.arvalid := true.B
      when(io.axi_master.arready) {
        masterState := mReadWait
      }
    }
    
    is(mReadWait) {
      io.axi_master.rready := true.B
      when(io.axi_master.rvalid) {
        dataBuf := io.axi_master.rdata
        masterState := mWriteReq
      }
    }
    
    is(mWriteReq) {
      io.axi_master.awaddr := dstAddrRegs(activeCh)
      io.axi_master.awvalid := true.B
      when(io.axi_master.awready) {
        masterState := mWriteWait
      }
    }
    
    is(mWriteWait) {
      io.axi_master.wvalid := true.B
      io.axi_master.wdata := dataBuf
      io.axi_master.wstrb := "b1111".U // Default to Word transfer for now
      
      when(io.axi_master.wready) {
        masterState := mUpdate
      }
    }
    
    is(mUpdate) {
      io.axi_master.bready := true.B
      when(io.axi_master.bvalid) {
        // Decrement length, increment addresses
        val len = lenRegs(activeCh)
        val ctrl = ctrlRegs(activeCh)
        
        val srcInc = ctrl(2)
        val dstInc = ctrl(3)
        
        when(srcInc) { srcAddrRegs(activeCh) := srcAddrRegs(activeCh) + 4.U }
        when(dstInc) { dstAddrRegs(activeCh) := dstAddrRegs(activeCh) + 4.U }
        
        val nextLen = len - 4.U
        lenRegs(activeCh) := nextLen
        
        when(nextLen === 0.U) {
          // Transfer complete
          ctrlRegs(activeCh) := ctrl & ~1.U(32.W) // Clear EN bit
          statRegs(activeCh) := (statRegs(activeCh) & ~1.U(32.W)) | 2.U(32.W) // Clear BUSY, Set DONE
        }
        
        masterState := mIdle
      }
    }
  }
  
  // -------------------------------------------------------------------------
  // 4. Interrupt Aggregation
  // -------------------------------------------------------------------------
  
  val chInterrupts = Wire(Vec(numChannels, Bool()))
  for (i <- 0 until numChannels) {
    // Interrupt if DONE bit (1) or ERR bit (2) is set AND INT_EN (1) is set
    chInterrupts(i) := (statRegs(i)(1) || statRegs(i)(2)) && ctrlRegs(i)(1)
  }
  
  io.interrupt := chInterrupts.asUInt =/= 0.U
}
