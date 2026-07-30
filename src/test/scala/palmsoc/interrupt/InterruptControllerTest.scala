package palmsoc.interrupt

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim
import palmsoc.config.AXI4LiteConfig
import palmsoc.bus.AXI4LiteResp

class InterruptControllerTest extends AnyFunSpec with ChiselSim {
  val config = AXI4LiteConfig(32, 32)

  // Helper method for AXI4-Lite Write Transaction
  def axiWrite(dut: InterruptController, addr: BigInt, data: BigInt): Unit = {
    dut.io.axi.awaddr.poke(addr.U)
    dut.io.axi.awvalid.poke(true.B)
    while (!dut.io.axi.awready.peek().litToBoolean) {
      dut.clock.step(1)
    }
    dut.clock.step(1)
    dut.io.axi.awvalid.poke(false.B)
    
    dut.io.axi.wdata.poke(data.U)
    dut.io.axi.wstrb.poke(0xF.U)
    dut.io.axi.wvalid.poke(true.B)
    dut.io.axi.bready.poke(true.B)
    while (!dut.io.axi.wready.peek().litToBoolean) {
      dut.clock.step(1)
    }
    dut.clock.step(1)
    dut.io.axi.wvalid.poke(false.B)
    
    while (!dut.io.axi.bvalid.peek().litToBoolean) {
      dut.clock.step(1)
    }
    dut.io.axi.bready.poke(false.B)
    dut.clock.step(1)
  }

  // Helper method for AXI4-Lite Read Transaction
  def axiRead(dut: InterruptController, addr: BigInt): BigInt = {
    dut.io.axi.araddr.poke(addr.U)
    dut.io.axi.arvalid.poke(true.B)
    dut.io.axi.rready.poke(true.B)
    
    dut.clock.step(1)
    while (!dut.io.axi.arready.peek().litToBoolean) {
      dut.clock.step(1)
    }
    dut.io.axi.arvalid.poke(false.B)
    
    while (!dut.io.axi.rvalid.peek().litToBoolean) {
      dut.clock.step(1)
    }
    val res = dut.io.axi.rdata.peek().litValue
    dut.io.axi.rready.poke(false.B)
    dut.clock.step(1)
    res
  }

  describe("Custom MMIO Interrupt Controller") {

    it("should initialize with all interrupts disabled and pending cleared") {
      simulate(new InterruptController(config, 4)) { dut =>
        dut.clock.step(2)
        dut.io_ext_int.expect(false.B)
        
        // Read INT_EN (offset 0x00)
        val en = axiRead(dut, 0x00)
        assert(en == 0, "Interrupt enable register should be 0 initially")
        
        // Read INT_PEND (offset 0x04)
        val pend = axiRead(dut, 0x04)
        assert(pend == 0, "Interrupt pending register should be 0 initially")
      }
    }

    it("should allow writing and reading to INT_EN register") {
      simulate(new InterruptController(config, 4)) { dut =>
        dut.clock.step(1)
        
        // Enable interrupts 0 and 3
        axiWrite(dut, 0x00, 0x09)
        val en = axiRead(dut, 0x00)
        assert(en == 0x09, "INT_EN register readback mismatch")
      }
    }

    it("should assert external interrupt when enabled interrupt is raised") {
      simulate(new InterruptController(config, 4)) { dut =>
        dut.clock.step(1)
        
        // Enable interrupt 0 and 3 (INT_EN = 0x09)
        axiWrite(dut, 0x00, 0x09)
        
        // Raise disabled interrupt 1 (bit 1)
        dut.io_interrupts.poke(0x02.U)
        dut.clock.step(1)
        dut.io_ext_int.expect(false.B)
        
        val pend1 = axiRead(dut, 0x04)
        assert((pend1 & 0x02) != 0, "Pending bit 1 should be set even if disabled")
        
        // Raise enabled interrupt 3 (bit 3)
        dut.io_interrupts.poke(0x0A.U) // bit 1 and bit 3
        dut.clock.step(1)
        dut.io_ext_int.expect(true.B) // Ext int should now be true
        
        val pend2 = axiRead(dut, 0x04)
        assert((pend2 & 0x08) != 0, "Pending bit 3 should be set")
      }
    }

    it("should clear pending interrupt via INT_ACK register") {
      simulate(new InterruptController(config, 4)) { dut =>
        dut.clock.step(1)
        
        // Enable interrupt 0 (INT_EN = 0x01)
        axiWrite(dut, 0x00, 0x01)
        
        // Raise interrupt 0
        dut.io_interrupts.poke(0x01.U)
        dut.clock.step(2)
        dut.io_ext_int.expect(true.B)
        
        // De-assert interrupt input
        dut.io_interrupts.poke(0x00.U)
        dut.clock.step(1)
        // Since it's latched in int_pend_reg, ext_int should remain true
        dut.io_ext_int.expect(true.B)
        
        // Write to INT_ACK (offset 0x08) to clear bit 0
        axiWrite(dut, 0x08, 0x01)
        
        // Verify pending is cleared and external interrupt is de-asserted
        dut.io_ext_int.expect(false.B)
        val pend = axiRead(dut, 0x04)
        assert((pend & 0x01) == 0, "Pending bit 0 should be cleared")
      }
    }
  }
}
