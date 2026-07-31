package palmsoc.peripheral

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim
import palmsoc.config.AXI4LiteConfig
import palmsoc.bus.AXI4LiteResp

class I2CTest extends AnyFunSpec with ChiselSim {
  val config = AXI4LiteConfig(32, 32)

  // Helper method for AXI4-Lite Write Transaction
  def axiWrite(dut: I2C_AXI, addr: BigInt, data: BigInt): Unit = {
    dut.io.axi.awaddr.poke(addr.U)
    dut.io.axi.awvalid.poke(true.B)
      while (!dut.io.axi.awready.peek().litToBoolean) { dut.clock.step(1) }
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
    dut.clock.step(1)
    dut.io.axi.bready.poke(false.B)
  }

  // Helper method for AXI4-Lite Read Transaction
  def axiRead(dut: I2C_AXI, addr: BigInt): BigInt = {
    dut.io.axi.araddr.poke(addr.U)
    dut.io.axi.arvalid.poke(true.B)
    dut.io.axi.rready.poke(true.B)
    
    while (!dut.io.axi.arready.peek().litToBoolean) {
      dut.clock.step(1)
    }
    dut.clock.step(1)
    dut.io.axi.arvalid.poke(false.B)
    
    while (!dut.io.axi.rvalid.peek().litToBoolean) {
      dut.clock.step(1)
    }
    val res = dut.io.axi.rdata.peek().litValue
    dut.clock.step(1)
    dut.io.axi.rready.poke(false.B)
    res
  }

  describe("I2C AXI4-Lite Master Peripheral") {

    it("should initialize to correct default values") {
      simulate(new I2C_AXI(config)) { dut =>
        dut.clock.step(2)
        
        // Emulated open-drain lines should float high (OE = 0)
        dut.scl_oe.expect(false.B)
        dut.sda_oe.expect(false.B)
        
        // Read STATUS: tx_empty = 1 (bit 2), busy = 0 (bit 0), rx_ack = 0 (bit 1)
        val status = axiRead(dut, 0x08)
        assert((status & 0x04) != 0, "tx_empty should be set initially")
        assert((status & 0x01) == 0, "busy should be clear initially")
        
        // Read CTRL: should be 0
        val ctrl = axiRead(dut, 0x04)
        assert(ctrl == 0, "CTRL register should be zero initially")
      }
    }

    it("should generate a START condition correctly") {
      simulate(new I2C_AXI(config)) { dut =>
        dut.clock.step(1)
        
        // Poke physical line pull-ups as active high
        dut.scl_in.poke(true.B)
        dut.sda_in.poke(true.B)
        
        // Configure Prescaler to 1 (each quarter-cycle takes 1 clock cycle)
        axiWrite(dut, 0x00, 1)
        
        // Write to CTRL: enable first (0x01)
        axiWrite(dut, 0x04, 0x01)
        
        // Write to CTRL: enable | start (0x01 | 0x02 = 0x03)
        axiWrite(dut, 0x04, 0x03)
        
        // Wait for the START condition Phase 1 (SDA driven low)
        while (!dut.sda_oe.peek().litToBoolean) {
          dut.clock.step(1)
        }
        
        // Phase 1: SCL remains high, SDA driven low
        dut.scl_oe.expect(false.B)
        dut.sda_oe.expect(true.B) // Pull down SDA
        dut.clock.step(1)
        
        // Phase 2: SCL remains high, SDA low
        dut.scl_oe.expect(false.B)
        dut.sda_oe.expect(true.B)
        dut.clock.step(1)
        
        // Phase 3: SCL driven low, SDA low (Ready to shift bits next)
        dut.scl_oe.expect(true.B) // Pull down SCL
        dut.sda_oe.expect(true.B)
        dut.clock.step(1)
        
        // The START command should complete, and I2C FSM goes back to sIdle
        dut.clock.step(4)
        val status = axiRead(dut, 0x08)
        assert((status & 0x01) == 0, "I2C master should be back to idle/not busy")
        assert((status & 0x04) != 0, "tx_empty should assert")
      }
    }

    it("should serialize address and handle slave ACK / NACK correctly") {
      simulate(new I2C_AXI(config)) { dut =>
        dut.clock.step(1)
        dut.scl_in.poke(true.B)
        dut.sda_in.poke(true.B)
        
        // Configure prescaler = 1
        axiWrite(dut, 0x00, 1)
        
        // Set target slave address to 0x4A, write mode (Addr = 0x94)
        axiWrite(dut, 0x10, 0x94)
        
        // First enable the I2C controller
        axiWrite(dut, 0x04, 0x01)
        
        // Trigger START + WRITE (CTRL = enable | start | write = 0x01 | 0x02 | 0x10 = 0x13)
        axiWrite(dut, 0x04, 0x13)
        
        // Wait for the START condition Phase 1 (SDA drops)
        while (!dut.sda_oe.peek().litToBoolean) {
          dut.clock.step(1)
        }
        
        // We are currently at Phase 2 (q_cnt=2) because the pin drop takes 1 cycle to propagate.
        // Skip the 2 remaining clock cycles of START condition (Phase 2 and Phase 3)
        dut.clock.step(2)
        
        // Now, the 8 bits of the address byte 0x94 (binary: 10010100) are shifted out MSB-first.
        // Bit 7: 1 (SDA floats high, OE = 0)
        // Bit 6: 0 (SDA driven low, OE = 1)
        // Bit 5: 0 (SDA driven low, OE = 1)
        // Bit 6: 1 (SDA floats high, OE = 0)
        // Bit 3: 0 (SDA driven low, OE = 1)
        // Bit 2: 1 (SDA floats high, OE = 0)
        // Bit 1: 0 (SDA driven low, OE = 1)
        // Bit 0: 0 (SDA driven low, OE = 1)
        
        val bits = Seq(false, true, true, false, true, false, true, true) // inverted because true means drive low (oe=1)
        for (oe <- bits) {
          // Quarter 0: SCL low (SDA is changing, don't check it)
          dut.scl_oe.expect(true.B)
          dut.clock.step(1)
          
          // Quarter 1: SCL low (SDA is stable)
          dut.scl_oe.expect(true.B)
          dut.sda_oe.expect(oe.B)
          dut.clock.step(1)
          
          // Quarter 2: SCL high (SDA must be stable)
          dut.scl_oe.expect(false.B)
          dut.sda_oe.expect(oe.B)
          dut.clock.step(1)
          
          // Quarter 3: SCL high (SDA is stable)
          dut.scl_oe.expect(false.B)
          dut.sda_oe.expect(oe.B)
          dut.clock.step(1)
        }
        
        // Bit 9: ACK bit from slave. Master releases SDA to read input.
        // Quarter 0: SCL low (SDA is changing)
        dut.scl_oe.expect(true.B)
        dut.sda_in.poke(false.B) // Slave drives SDA low (ACK) as soon as SCL is low
        dut.clock.step(1)
        
        // Quarter 1: SCL low (SDA is stable, Master has released it)
        dut.scl_oe.expect(true.B)
        dut.sda_oe.expect(false.B)
        dut.clock.step(1)
        
        // Quarter 2: SCL high. Slave continues holding ACK.
        dut.scl_oe.expect(false.B)
        dut.sda_oe.expect(false.B)
        dut.clock.step(1)
        
        // Quarter 3: SCL high
        dut.scl_oe.expect(false.B)
        dut.clock.step(1)
        
        // Slave releases SDA on next falling edge
        dut.sda_in.poke(true.B)
        
        
        // Transaction should be complete.
        dut.clock.step(2)
        val status = axiRead(dut, 0x08)
        assert((status & 0x02) == 0, "rx_ack bit should be 0 (ACK received from slave)")
      }
    }
  }
}
