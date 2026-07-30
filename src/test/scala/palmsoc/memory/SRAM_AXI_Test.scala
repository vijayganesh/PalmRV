package palmsoc.memory

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim
import palmsoc.config.AXI4LiteConfig
import palmsoc.bus.AXI4LiteResp

/**
 * Test Suite for SRAM with AXI4-Lite Interface
 * 
 * Tests:
 * 1. Basic Write and Read - Single word operations
 * 2. Byte Enable Write - Partial word writes with WSTRB
 * 3. Sequential Access - Back-to-back transactions
 * 4. Address Bounds - Out of bounds error handling
 * 5. Protocol Compliance - Proper AXI4-Lite handshaking
 * 6. Concurrent Transactions - Write followed by read
 * 7. Address Alignment - Misaligned access error handling
 */

class SRAM_AXI_Test extends AnyFunSpec with ChiselSim {
  val config = AXI4LiteConfig(addrWidth = 32, dataWidth = 32)
  val depth = 1024  // 1K words
  
  describe("SRAM AXI4-Lite Interface") {
    
    it("should perform basic write and read operations") {
      simulate(new SRAM_AXI(config, depth)) { dut =>
        // Write 0xDEADBEEF to address 0x40 (word-aligned)
        dut.io.axi.awaddr.poke(0x40.U)
        dut.io.axi.awprot.poke(0.U)
        dut.io.axi.awvalid.poke(true.B)
        
        // Wait for awready
        while (!dut.io.axi.awready.peek().litToBoolean) {
          dut.clock.step(1)
        }
        dut.io.axi.awready.expect(true.B)
        dut.clock.step(1)
        dut.io.axi.awvalid.poke(false.B)
        
        // Send write data
        dut.io.axi.wdata.poke(0xDEADBEEFL.U)
        dut.io.axi.wstrb.poke(0xF.U)  // All bytes enabled
        dut.io.axi.wvalid.poke(true.B)
        
        // Wait for wready
        while (!dut.io.axi.wready.peek().litToBoolean) {
          dut.clock.step(1)
        }
        dut.io.axi.wready.expect(true.B)
        dut.clock.step(1)
        dut.io.axi.wvalid.poke(false.B)
        
        // Wait for write response
        dut.io.axi.bready.poke(true.B)
        while (!dut.io.axi.bvalid.peek().litToBoolean) {
          dut.clock.step(1)
        }
        dut.io.axi.bvalid.expect(true.B)
        dut.io.axi.bresp.expect(AXI4LiteResp.OKAY)
        dut.clock.step(1)
        dut.io.axi.bready.poke(false.B)
        dut.clock.step(1)
        
        // Read back from address 0x40
        dut.io.axi.araddr.poke(0x40.U)
        dut.io.axi.arprot.poke(0.U)
        dut.io.axi.arvalid.poke(true.B)
        
        // Wait for arready
        while (!dut.io.axi.arready.peek().litToBoolean) {
          dut.clock.step(1)
        }
        dut.io.axi.arready.expect(true.B)
        dut.clock.step(1)
        dut.io.axi.arvalid.poke(false.B)
        
        // Wait for read data
        dut.io.axi.rready.poke(true.B)
        while (!dut.io.axi.rvalid.peek().litToBoolean) {
          dut.clock.step(1)
        }
        dut.io.axi.rvalid.expect(true.B)
        dut.io.axi.rdata.expect(0xDEADBEEFL.U)
        dut.io.axi.rresp.expect(AXI4LiteResp.OKAY)
        dut.clock.step(1)
        dut.io.axi.rready.poke(false.B)
        dut.clock.step(1)
      }
    }
    
    it("should handle byte-enable writes correctly with WSTRB") {
      simulate(new SRAM_AXI(config, depth)) { dut =>
        // Write initial value 0xFFFFFFFF to address 0x80
        dut.io.axi.awaddr.poke(0x80.U)
        dut.io.axi.awvalid.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.awvalid.poke(false.B)
        
        dut.io.axi.wdata.poke(0xFFFFFFFFL.U)
        dut.io.axi.wstrb.poke(0xF.U)
        dut.io.axi.wvalid.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.wvalid.poke(false.B)
        
        dut.io.axi.bready.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.bresp.expect(AXI4LiteResp.OKAY)
        dut.io.axi.bready.poke(false.B)
        dut.clock.step(1)
        
        // Write only lower byte (0x78) with wstrb = 0x1
        dut.io.axi.awaddr.poke(0x80.U)
        dut.io.axi.awvalid.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.awvalid.poke(false.B)
        
        dut.io.axi.wdata.poke(0x12345678L.U)
        dut.io.axi.wstrb.poke(0x1.U)  // Only byte 0
        dut.io.axi.wvalid.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.wvalid.poke(false.B)
        
        dut.io.axi.bready.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.bresp.expect(AXI4LiteResp.OKAY)
        dut.io.axi.bready.poke(false.B)
        dut.clock.step(1)
        
        // Read back - should be 0xFFFFFF78 (only lower byte changed)
        dut.io.axi.araddr.poke(0x80.U)
        dut.io.axi.arvalid.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.arvalid.poke(false.B)
        
        dut.io.axi.rready.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.rdata.expect(0xFFFFFF78L.U)
        dut.io.axi.rresp.expect(AXI4LiteResp.OKAY)
        dut.io.axi.rready.poke(false.B)
        dut.clock.step(1)
      }
    }
    
    it("should handle sequential write and read operations") {
      simulate(new SRAM_AXI(config, depth)) { dut =>
        val testData = Seq(0xAAAAAAAAL, 0x55555555L, 0x12345678L, 0xFEDCBA98L)
        val baseAddr = 0x400
        
        // Sequential writes
        for ((data, offset) <- testData.zipWithIndex) {
          val addr = baseAddr + (offset * 4)  // Word-aligned addresses
          
          dut.io.axi.awaddr.poke(addr.U)
          dut.io.axi.awvalid.poke(true.B)
          dut.clock.step(1)
          dut.io.axi.awvalid.poke(false.B)
          
          dut.io.axi.wdata.poke(data.U)
          dut.io.axi.wstrb.poke(0xF.U)
          dut.io.axi.wvalid.poke(true.B)
          dut.clock.step(1)
          dut.io.axi.wvalid.poke(false.B)
          
          dut.io.axi.bready.poke(true.B)
          dut.clock.step(1)
          dut.io.axi.bresp.expect(AXI4LiteResp.OKAY)
          dut.io.axi.bready.poke(false.B)
          dut.clock.step(1)
        }
        
        // Sequential reads
        for ((expectedData, offset) <- testData.zipWithIndex) {
          val addr = baseAddr + (offset * 4)
          
          dut.io.axi.araddr.poke(addr.U)
          dut.io.axi.arvalid.poke(true.B)
          dut.clock.step(1)
          dut.io.axi.arvalid.poke(false.B)
          
          dut.io.axi.rready.poke(true.B)
          dut.clock.step(1)
          dut.io.axi.rdata.expect(expectedData.U)
          dut.io.axi.rresp.expect(AXI4LiteResp.OKAY)
          dut.io.axi.rready.poke(false.B)
          dut.clock.step(1)
        }
      }
    }
    
    it("should detect and signal out-of-bounds address errors") {
      simulate(new SRAM_AXI(config, depth)) { dut =>
        // Try to write to address beyond depth
        val outOfBoundsAddr = (depth * 4) + 0x100  // Beyond memory
        
        dut.io.axi.awaddr.poke(outOfBoundsAddr.U)
        dut.io.axi.awvalid.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.awvalid.poke(false.B)
        
        dut.io.axi.wdata.poke(0xDEADBEEFL.U)
        dut.io.axi.wstrb.poke(0xF.U)
        dut.io.axi.wvalid.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.wvalid.poke(false.B)
        
        dut.io.axi.bready.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.bvalid.expect(true.B)
        dut.io.axi.bresp.expect(AXI4LiteResp.SLVERR)  // Should get error
        dut.io.axi.bready.poke(false.B)
        dut.clock.step(1)
        
        // Try to read from out-of-bounds address
        dut.io.axi.araddr.poke(outOfBoundsAddr.U)
        dut.io.axi.arvalid.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.arvalid.poke(false.B)
        
        dut.io.axi.rready.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.rvalid.expect(true.B)
        dut.io.axi.rresp.expect(AXI4LiteResp.SLVERR)  // Should get error
        dut.io.axi.rready.poke(false.B)
        dut.clock.step(1)
      }
    }
    
    it("should detect misaligned address errors") {
      simulate(new SRAM_AXI(config, depth)) { dut =>
        // Try to write to misaligned address (not word-aligned)
        val misalignedAddr = 0x42  // Not aligned to 4-byte boundary
        
        dut.io.axi.awaddr.poke(misalignedAddr.U)
        dut.io.axi.awvalid.poke(true.B)
        
        // Wait for awready
        while (!dut.io.axi.awready.peek().litToBoolean) {
          dut.clock.step(1)
        }
        //dut.clock.step(1)
        dut.io.axi.awvalid.poke(false.B)
        
        dut.io.axi.wdata.poke(0x12345678L.U)
        dut.io.axi.wstrb.poke(0xF.U)
        dut.io.axi.wvalid.poke(true.B)
        
        // Wait for wready
        while (!dut.io.axi.wready.peek().litToBoolean) {
          dut.clock.step(1)
        }
        dut.clock.step(1)
        dut.io.axi.wvalid.poke(false.B)
        
        dut.io.axi.bready.poke(true.B)
        // Wait for bvalid
        while (!dut.io.axi.bvalid.peek().litToBoolean) {
          dut.clock.step(1)
        }
        dut.io.axi.bvalid.expect(true.B)
        dut.io.axi.bresp.expect(AXI4LiteResp.SLVERR)  // Should get error
        dut.clock.step(1)
        dut.io.axi.bready.poke(false.B)
        dut.clock.step(1)
        
        // Try to read from misaligned address
        dut.io.axi.araddr.poke(misalignedAddr.U)
        dut.io.axi.arvalid.poke(true.B)
        
        // Wait for arready
        while (!dut.io.axi.arready.peek().litToBoolean) {
          dut.clock.step(1)
        }
        dut.clock.step(1)
        dut.io.axi.arvalid.poke(false.B)
        
        dut.io.axi.rready.poke(true.B)
        // Wait for rvalid
        while (!dut.io.axi.rvalid.peek().litToBoolean) {
          dut.clock.step(1)
        }
        dut.io.axi.rvalid.expect(true.B)
        dut.io.axi.rresp.expect(AXI4LiteResp.SLVERR)  // Should get error
        dut.clock.step(1)
        dut.io.axi.rready.poke(false.B)
        dut.clock.step(1)
      }
    }
    
    it("should follow proper AXI4-Lite handshaking protocol") {
      simulate(new SRAM_AXI(config, depth)) { dut =>
        // Test that master can hold valid until ready is asserted
        dut.io.axi.awaddr.poke(0x100.U)
        dut.io.axi.awvalid.poke(true.B)
        dut.clock.step(1)
        
        // Write address should be accepted in one cycle
        dut.io.axi.awready.expect(true.B)
        dut.io.axi.awvalid.poke(false.B)
        
        // Now test holding write data valid
        dut.io.axi.wdata.poke(0xCAFEBABEL.U)
        dut.io.axi.wstrb.poke(0xF.U)
        dut.io.axi.wvalid.poke(true.B)
while(!dut.io.axi.wready.peek().litToBoolean) {
          dut.clock.step(1)
        }
        dut.io.axi.wvalid.poke(false.B)
        
        // Response should come, but we don't assert bready immediately
        //dut.clock.step(1)
        while(!dut.io.axi.bvalid.peek().litToBoolean) {
          dut.clock.step(1)
        }
        dut.io.axi.bvalid.expect(true.B)
        
        // Hold bready low for a few cycles
        dut.io.axi.bready.poke(false.B)
        while(!dut.io.axi.bvalid.peek().litToBoolean){
            dut.clock.step(1)
        }        
        dut.io.axi.bvalid.expect(true.B)  // Should still be valid
        
        // Now accept the response
        dut.io.axi.bready.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.bresp.expect(AXI4LiteResp.OKAY)
        dut.io.axi.bready.poke(false.B)
        dut.clock.step(1)
      }
    }
    
    it("should handle multiple byte strobe patterns") {
      simulate(new SRAM_AXI(config, depth)) { dut =>
        val addr = 0x180
        
        // Write all F's first
        dut.io.axi.awaddr.poke(addr.U)
        dut.io.axi.awvalid.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.awvalid.poke(false.B)
        
        dut.io.axi.wdata.poke(0xFFFFFFFFL.U)
        dut.io.axi.wstrb.poke(0xF.U)
        dut.io.axi.wvalid.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.wvalid.poke(false.B)
        
        dut.io.axi.bready.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.bready.poke(false.B)
        dut.clock.step(1)
        
        // Write with wstrb = 0x3 (lower 2 bytes)
        dut.io.axi.awaddr.poke(addr.U)
        dut.io.axi.awvalid.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.awvalid.poke(false.B)
        
        dut.io.axi.wdata.poke(0x12345678L.U)
        dut.io.axi.wstrb.poke(0x3.U)  // Lower 2 bytes
        dut.io.axi.wvalid.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.wvalid.poke(false.B)
        
        dut.io.axi.bready.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.bready.poke(false.B)
        dut.clock.step(1)
        
        // Read and verify - should be 0xFFFF5678
        dut.io.axi.araddr.poke(addr.U)
        dut.io.axi.arvalid.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.arvalid.poke(false.B)
        
        dut.io.axi.rready.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.rdata.expect(0xFFFF5678L.U)
        dut.io.axi.rready.poke(false.B)
        dut.clock.step(1)
        
        // Now write with wstrb = 0xC (upper 2 bytes)
        dut.io.axi.awaddr.poke(addr.U)
        dut.io.axi.awvalid.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.awvalid.poke(false.B)
        
        dut.io.axi.wdata.poke(0xABCDEF00L.U)
        dut.io.axi.wstrb.poke(0xC.U)  // Upper 2 bytes
        dut.io.axi.wvalid.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.wvalid.poke(false.B)
        
        dut.io.axi.bready.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.bready.poke(false.B)
        dut.clock.step(1)
        
        // Read and verify - should be 0xABCD5678
        dut.io.axi.araddr.poke(addr.U)
        dut.io.axi.arvalid.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.arvalid.poke(false.B)
        
        dut.io.axi.rready.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.rdata.expect(0xABCD5678L.U)
        dut.io.axi.rready.poke(false.B)
        dut.clock.step(1)
      }
    }
    
    it("should handle write followed immediately by read") {
      simulate(new SRAM_AXI(config, depth)) { dut =>
        // Write operation
        dut.io.axi.awaddr.poke(0x200.U)
        dut.io.axi.awvalid.poke(true.B)
        //dut.clock.step(1)
        while(!dut.io.axi.awready.peek().litToBoolean)
          dut.clock.step(1)
        print(s"")
        dut.io.axi.awready.expect(true.B)
        dut.clock.step(1)
        dut.io.axi.awvalid.poke(false.B)
        
        dut.io.axi.wdata.poke(0x87654321L.U)
        dut.io.axi.wstrb.poke(0xF.U)
        dut.io.axi.wvalid.poke(true.B)
        while(!dut.io.axi.wready.peek().litToBoolean)
          dut.clock.step(1)
        dut.clock.step(1)
        dut.io.axi.wvalid.poke(false.B)
        
        dut.io.axi.bready.poke(true.B)
        while(!dut.io.axi.bvalid.peek().litToBoolean)
          dut.clock.step(1)
        dut.io.axi.bresp.expect(AXI4LiteResp.OKAY)
        dut.io.axi.bready.poke(false.B)
        
        // Immediately start read (no idle cycle)
        dut.io.axi.araddr.poke(0x200.U)
        dut.io.axi.arvalid.poke(true.B)
        while(!dut.io.axi.arready.peek().litToBoolean)
          dut.clock.step(1)
        dut.io.axi.arvalid.poke(false.B)
        
        dut.io.axi.rready.poke(true.B)
        dut.clock.step(1)
        dut.io.axi.rvalid.expect(true.B)
        dut.io.axi.rdata.expect(0x87654321L.U)
        dut.io.axi.rresp.expect(AXI4LiteResp.OKAY)
        dut.io.axi.rready.poke(false.B)
        dut.clock.step(1)
      }
    }
  }
}
