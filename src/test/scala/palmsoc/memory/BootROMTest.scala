package palmsoc.memory

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim
import palmsoc.config.AXI4LiteConfig
import palmsoc.bus.AXI4LiteResp

/**
 * Test Suite for Boot ROM with Wishbone and AXI4-Lite Interfaces
 * 
 * Tests:
 * 1. Basic Read Operations - Read from initialized ROM
 * 2. Write Protection - Verify writes return errors
 * 3. Sequential Read - Multiple consecutive reads
 * 4. Address Bounds - Out of bounds error handling
 * 5. Default Boot Code - Verify default initialization
 * 6. Custom Initialization - Test with custom ROM content
 */

class BootROMTest extends AnyFunSpec with ChiselSim {
  val addrWidth = 16  // 64K words
  val dataWidth = 32
  val depth = 256     // Small ROM for testing
  
  describe("BootROM Wishbone Interface") {
    
    it("should read default boot code") {
      simulate(new BootROM(addrWidth, dataWidth, depth)) { dut =>
        // Read from address 0 (should contain jump instruction)
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.adr.poke(0.U)
        dut.io.wb.sel.poke(0xF.U)
        dut.clock.step(1)
        
        // Wait for read acknowledgment
        dut.io.wb.ack.expect(false.B)
        dut.clock.step(1)
        dut.io.wb.ack.expect(true.B)
        dut.io.wb.dat_r.expect(0x0000006fL.U)  // j . instruction
        
        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)
      }
    }
    
    it("should reject write attempts with error") {
      simulate(new BootROM(addrWidth, dataWidth, depth)) { dut =>
        // Try to write to ROM
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.adr.poke(0x10.U)
        dut.io.wb.dat_w.poke(0xDEADBEEFL.U)
        dut.io.wb.sel.poke(0xF.U)
        dut.clock.step(1)
        
        // Should get error signal immediately
        dut.io.wb.err.expect(true.B)
        dut.io.wb.ack.expect(false.B)
        
        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)
      }
    }
    
    it("should handle sequential read operations") {
      simulate(new BootROM(addrWidth, dataWidth, depth)) { dut =>
        val expectedValues = Seq(0x0000006fL, 0x00000013L, 0x00000013L, 0x00000013L)
        
        for ((expected, addr) <- expectedValues.zipWithIndex) {
          dut.io.wb.cyc.poke(true.B)
          dut.io.wb.stb.poke(true.B)
          dut.io.wb.we.poke(false.B)
          dut.io.wb.adr.poke(addr.U)
          dut.io.wb.sel.poke(0xF.U)
          dut.clock.step(1)
          dut.clock.step(1)
          dut.io.wb.ack.expect(true.B)
          dut.io.wb.dat_r.expect(expected.U)
          dut.io.wb.cyc.poke(false.B)
          dut.io.wb.stb.poke(false.B)
          dut.clock.step(1)
        }
      }
    }
    
    it("should detect out-of-bounds address errors") {
      simulate(new BootROM(addrWidth, dataWidth, depth)) { dut =>
        // Try to read beyond ROM depth
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.adr.poke((depth + 100).U)
        dut.io.wb.sel.poke(0xF.U)
        dut.clock.step(1)
        
        // Should get error signal
        dut.io.wb.err.expect(true.B)
        dut.io.wb.ack.expect(false.B)
        
        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)
      }
    }
    
    it("should read custom initialized content") {
      val customContent = Seq(
        0xAAAAAAAAL.U,
        0x55555555L.U,
        0x12345678L.U,
        0xFEDCBA98L.U
      )
      
      simulate(new BootROM(addrWidth, dataWidth, depth, Some(customContent))) { dut =>
        for ((expected, addr) <- customContent.zipWithIndex) {
          dut.io.wb.cyc.poke(true.B)
          dut.io.wb.stb.poke(true.B)
          dut.io.wb.we.poke(false.B)
          dut.io.wb.adr.poke(addr.U)
          dut.io.wb.sel.poke(0xF.U)
          dut.clock.step(1)
          dut.clock.step(1)
          dut.io.wb.ack.expect(true.B)
          dut.io.wb.dat_r.expect(expected)
          dut.io.wb.cyc.poke(false.B)
          dut.io.wb.stb.poke(false.B)
          dut.clock.step(1)
        }
      }
    }
  }
}

class BootROM_AXI_Test extends AnyFunSpec with ChiselSim {
  val config = AXI4LiteConfig(addrWidth = 8, dataWidth = 32)
  val depth = 256  // Small ROM for testing
  
  describe("BootROM AXI4-Lite Interface") {
    
    it("should read default boot code") {
      simulate(new BootROM_AXI(config, depth)) { dut =>
        // Read from address 0 (should contain jump instruction)
        dut.io.axi.araddr.poke(0.U)
        dut.io.axi.arprot.poke(0.U)
        dut.io.axi.arvalid.poke(true.B)
        dut.io.axi.arready.expect(true.B)
        dut.clock.step(1)
        
        var rvalidStatus = dut.io.axi.rvalid.peek().litValue.toInt
        print(s" -----------   \n rvalid status: $rvalidStatus  <--\n")
        dut.io.axi.arvalid.poke(false.B)
        dut.io.axi.rready.poke(true.B)
        
        //rvalidStatus = dut.io.axi.rvalid.peek().litToBoolean
        print(s" -----------   \n rvalid status: $rvalidStatus\n")
        dut.io.axi.rvalid.expect(true.B)
        dut.io.axi.rdata.expect(0x0000006fL.U)  // j . instruction
        dut.io.axi.rresp.expect(AXI4LiteResp.OKAY)
        dut.clock.step(1)
        dut.io.axi.rready.poke(false.B)
        dut.clock.step(1)
      }
    }
    
    it("should reject write attempts with SLVERR") {
      simulate(new BootROM_AXI(config, depth)) { dut =>
        // Try to write to ROM
        dut.io.axi.awaddr.poke(0x40.U)
        dut.io.axi.awvalid.poke(true.B)
        
        //dut.clock.step(1)
        //dut.io.axi.awready.expect(true.B)
        while (!dut.io.axi.awready.peek().litToBoolean) {
      dut.clock.step(1)
    }
    dut.clock.step(1)
        dut.io.axi.awvalid.poke(false.B)
     
        
        dut.io.axi.wdata.poke(0xDEADBEEFL.U)
        dut.io.axi.wstrb.poke(0xF.U)
        dut.io.axi.wvalid.poke(true.B)
        while (!dut.io.axi.wready.peek().litToBoolean) {
      dut.clock.step(1)
    }
        //dut.clock.step(1)
        dut.io.axi.wready.expect(true.B)
        dut.clock.step(1)
        dut.io.axi.wvalid.poke(false.B)
        
        dut.io.axi.bready.poke(true.B)
           while (!dut.io.axi.bvalid.peek().litToBoolean) {
      dut.clock.step(1)
    }
        
        dut.io.axi.bvalid.expect(true.B)
        dut.io.axi.bresp.expect(AXI4LiteResp.SLVERR)  // ROM is read-only
        dut.clock.step(1)
        dut.io.axi.bready.poke(false.B)
        dut.clock.step(1)
      }
    }
    
    it("should handle sequential read operations") {
      simulate(new BootROM_AXI(config, depth)) { dut =>
        val expectedValues = Seq(0x0000006fL, 0x00000013L, 0x00000013L, 0x00000013L)
        
        for ((expected, offset) <- expectedValues.zipWithIndex) {
          val addr = offset * 4  // Word-aligned addresses
          
          dut.io.axi.araddr.poke(addr.U)
          dut.io.axi.arvalid.poke(true.B)
          dut.clock.step(1)
          dut.io.axi.arvalid.poke(false.B)
          
          dut.io.axi.rready.poke(true.B)
          dut.clock.step(1)
          dut.io.axi.rvalid.expect(true.B)
          dut.io.axi.rdata.expect(expected.U)
          dut.io.axi.rresp.expect(AXI4LiteResp.OKAY)
          dut.io.axi.rready.poke(false.B)
          dut.clock.step(1)
        }
      }
    }
    
    it("should detect out-of-bounds address errors") {
      simulate(new BootROM_AXI(config, depth)) { dut =>
        val outOfBoundsAddr = (depth * 4) + 0x100
        
        // Try to read beyond ROM
        dut.io.axi.araddr.poke(outOfBoundsAddr.U)
        dut.io.axi.arvalid.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.arvalid.poke(false.B)
        
        dut.io.axi.rready.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.rvalid.expect(true.B)
        dut.io.axi.rresp.expect(AXI4LiteResp.SLVERR)
        dut.io.axi.rready.poke(false.B)
        dut.clock.step(1)
      }
    }
    
    it("should detect misaligned address errors") {
      simulate(new BootROM_AXI(config, depth)) { dut =>
        val misalignedAddr = 0x42  // Not word-aligned
        
        dut.io.axi.araddr.poke(misalignedAddr.U)
        dut.io.axi.arvalid.poke(true.B)
        
        // Wait for arready
        while (!dut.io.axi.arready.peek().litToBoolean) {
          dut.clock.step(1)
        }
        dut.io.axi.arready.expect(true.B)
        dut.clock.step(1)
        dut.io.axi.arvalid.poke(false.B)
        
        dut.io.axi.rready.poke(true.B)
        // Wait for rvalid (read data ready from slave)
        while (!dut.io.axi.rvalid.peek().litToBoolean) {
          dut.clock.step(1)
        }
        
        dut.io.axi.rvalid.expect(true.B)
        dut.io.axi.rresp.expect(AXI4LiteResp.SLVERR)
        
        // Complete the read transaction
        while (dut.io.axi.rready.peek().litToBoolean && dut.io.axi.rvalid.peek().litToBoolean) {
          dut.clock.step(1)
          dut.io.axi.rready.poke(false.B)
        }
        dut.clock.step(1)
      }
    }
    
    it("should read custom initialized content") {
      val customContent = Seq(
        0xAAAAAAAAL.U,
        0x55555555L.U,
        0x12345678L.U,
        0xFEDCBA98L.U
      )
      
      simulate(new BootROM_AXI(config, depth, Some(customContent))) { dut =>
        for ((expected, offset) <- customContent.zipWithIndex) {
          val addr = offset * 4
          
          dut.io.axi.araddr.poke(addr.U)
          dut.io.axi.arvalid.poke(true.B)
          dut.clock.step(1)
          dut.io.axi.arvalid.poke(false.B)
          
          dut.io.axi.rready.poke(true.B)
          dut.clock.step(1)
          dut.io.axi.rvalid.expect(true.B)
          dut.io.axi.rdata.expect(expected)
          dut.io.axi.rresp.expect(AXI4LiteResp.OKAY)
          dut.io.axi.rready.poke(false.B)
          dut.clock.step(1)
        }
      }
    }
    
    it("should follow proper AXI4-Lite handshaking for reads") {
      simulate(new BootROM_AXI(config, depth)) { dut =>
        // Issue read address
        dut.io.axi.araddr.poke(0x10.U)
        dut.io.axi.arvalid.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.arready.expect(true.B)
        dut.io.axi.arvalid.poke(false.B)
        
        // Wait without asserting rready
        dut.clock.step(1)
        dut.io.axi.rvalid.expect(true.B)
        
        // Hold rready low for multiple cycles
        dut.io.axi.rready.poke(false.B)
        dut.clock.step(2)
        dut.io.axi.rvalid.expect(true.B)  // Should remain valid
        
        // Now accept the data
        dut.io.axi.rready.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.rresp.expect(AXI4LiteResp.OKAY)
        dut.io.axi.rready.poke(false.B)
        dut.clock.step(1)
      }
    }
    
    it("should handle multiple back-to-back reads") {
      simulate(new BootROM_AXI(config, depth)) { dut =>
        for (i <- 0 until 8) {
          val addr = i * 4
          
          dut.io.axi.araddr.poke(addr.U)
          dut.io.axi.arvalid.poke(true.B)
          dut.clock.step(1)
          dut.io.axi.arvalid.poke(false.B)
          
          dut.io.axi.rready.poke(true.B)
          dut.clock.step(1)
          dut.io.axi.rvalid.expect(true.B)
          dut.io.axi.rresp.expect(AXI4LiteResp.OKAY)
          dut.io.axi.rready.poke(false.B)
          // No idle cycle - go directly to next read
        }
      }
    }
  }
}
