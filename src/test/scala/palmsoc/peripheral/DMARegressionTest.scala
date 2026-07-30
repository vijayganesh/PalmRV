package palmsoc.peripheral

import chisel3._
import chisel3.util._
import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim
import palmsoc.config.AXI4LiteConfig

class DMARegressionTest extends AnyFunSpec with ChiselSim {
  describe("DMA Controller Full Regression Test") {
    it("should complete a full memory transfer and assert interrupts") {
      val config = AXI4LiteConfig(32, 32)
      simulate(new DMA_AXI(config, 4)) { dut =>
        // --------------------------------------------------------
        // 1. Setup Channel 0 Configuration via Slave Port
        // --------------------------------------------------------
        
        // Helper function for AXI Slave writes
        def writeReg(addr: Int, data: Int): Unit = {
          dut.io.axi_slave.awaddr.poke(addr.U)
          dut.io.axi_slave.awvalid.poke(true.B)
          dut.io.axi_slave.wdata.poke(data.U)
          dut.io.axi_slave.wvalid.poke(true.B)
          dut.io.axi_slave.bready.poke(true.B)
          
          while(dut.io.axi_slave.awready.peek().litValue == 0) { dut.clock.step(1) }
          dut.clock.step(1)
          dut.io.axi_slave.awvalid.poke(false.B)
          
          while(dut.io.axi_slave.wready.peek().litValue == 0) { dut.clock.step(1) }
          dut.clock.step(1)
          dut.io.axi_slave.wvalid.poke(false.B)
          
          while(dut.io.axi_slave.bvalid.peek().litValue == 0) { dut.clock.step(1) }
          dut.clock.step(1)
          dut.io.axi_slave.bready.poke(false.B)
        }
        
        // Initialize AXI Master responses to prevent hangs
        dut.io.axi_master.arready.poke(false.B)
        dut.io.axi_master.rvalid.poke(false.B)
        dut.io.axi_master.rdata.poke(0.U)
        
        dut.io.axi_master.awready.poke(false.B)
        dut.io.axi_master.wready.poke(false.B)
        dut.io.axi_master.bvalid.poke(false.B)
        
        dut.clock.step(2)
        
        // Channel 0 configuration
        writeReg(0x00, 0x1000) // SRC_ADDR = 0x1000
        writeReg(0x04, 0x2000) // DST_ADDR = 0x2000
        writeReg(0x08, 0x0008) // LENGTH = 8 bytes (2 words)
        
        // CTRL = EN(1) | INT_EN(2) | SRC_INC(4) | DST_INC(8) => 0x0F
        writeReg(0x0C, 0x0F)
        
        // --------------------------------------------------------
        // 2. Simulate Master Bus responding to DMA Transfers
        // --------------------------------------------------------
        
        var wordsTransferred = 0
        var timeout = 0
        
        // Loop until transfer completes (Interrupt fires) or timeout
        while (dut.io.interrupt.peek().litValue == 0 && timeout < 100) {
          
          // Handle DMA Read Request
          if (dut.io.axi_master.arvalid.peek().litValue == 1) {
            val addr = dut.io.axi_master.araddr.peek().litValue
            assert(addr == 0x1000 + (wordsTransferred * 4), s"Unexpected Read Address: 0x${addr.toString(16)}")
            
            dut.io.axi_master.arready.poke(true.B)
            dut.clock.step(1)
            dut.io.axi_master.arready.poke(false.B)
            
            // Provide Read Data
            dut.io.axi_master.rvalid.poke(true.B)
            dut.io.axi_master.rdata.poke((0xDEADBEEFL + wordsTransferred).U)
            while(dut.io.axi_master.rready.peek().litValue == 0) { dut.clock.step(1) }
            dut.clock.step(1)
            dut.io.axi_master.rvalid.poke(false.B)
          }
          
          // Handle DMA Write Request
          if (dut.io.axi_master.awvalid.peek().litValue == 1) {
            val addr = dut.io.axi_master.awaddr.peek().litValue
            assert(addr == 0x2000 + (wordsTransferred * 4), s"Unexpected Write Address: 0x${addr.toString(16)}")
            
            dut.io.axi_master.awready.poke(true.B)
            dut.clock.step(1)
            dut.io.axi_master.awready.poke(false.B)
            
            // Accept Write Data
            dut.io.axi_master.wready.poke(true.B)
            while(dut.io.axi_master.wvalid.peek().litValue == 0) { dut.clock.step(1) }
            
            val data = dut.io.axi_master.wdata.peek().litValue
            assert(data == 0xDEADBEEFL + wordsTransferred, s"Unexpected Write Data: 0x${data.toString(16)}")
            
            dut.clock.step(1)
            dut.io.axi_master.wready.poke(false.B)
            
            // Provide Write Response
            dut.io.axi_master.bvalid.poke(true.B)
            while(dut.io.axi_master.bready.peek().litValue == 0) { dut.clock.step(1) }
            dut.clock.step(1)
            dut.io.axi_master.bvalid.poke(false.B)
            
            wordsTransferred += 1
          }
          
          dut.clock.step(1)
          timeout += 1
        }
        
        // --------------------------------------------------------
        // 3. Verify Final State
        // --------------------------------------------------------
        
        assert(timeout < 100, "DMA Transfer Timed Out")
        assert(wordsTransferred == 2, s"Expected 2 words transferred, got $wordsTransferred")
        
        // Verify interrupt is HIGH
        dut.io.interrupt.expect(true.B)
        
        // Helper to read Slave Registers
        def readReg(addr: Int): BigInt = {
          dut.io.axi_slave.araddr.poke(addr.U)
          dut.io.axi_slave.arvalid.poke(true.B)
          dut.io.axi_slave.rready.poke(true.B)
          
          while (dut.io.axi_slave.arready.peek().litValue == 0) { dut.clock.step(1) }
          dut.clock.step(1)
          dut.io.axi_slave.arvalid.poke(false.B)
          
          while (dut.io.axi_slave.rvalid.peek().litValue == 0) { dut.clock.step(1) }
          val result = dut.io.axi_slave.rdata.peek().litValue
          dut.clock.step(1)
          dut.io.axi_slave.rready.poke(false.B)
          result
        }
        
        // Length should be decremented to 0
        assert(readReg(0x08) == 0, "LENGTH register was not decremented to 0")
        
        // EN bit (Bit 0) in CTRL should be cleared automatically by DMA
        val finalCtrl = readReg(0x0C)
        assert((finalCtrl & 1) == 0, "EN bit in CTRL register was not cleared")
        
        // DONE bit (Bit 1) in STAT should be set
        val finalStat = readReg(0x10)
        assert((finalStat & 2) == 2, "DONE bit in STAT register was not set")
      }
    }
  }
}
