package palmsoc.peripheral

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim
import palmsoc.config.AXI4LiteConfig
import palmsoc.bus.AXI4LiteResp

class UARTTest extends AnyFunSpec with ChiselSim {
  val config = AXI4LiteConfig(32, 32)

  // Helper method for AXI4-Lite Write Transaction
  def axiWrite(dut: UART_AXI, addr: BigInt, data: BigInt): Unit = {
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
  def axiRead(dut: UART_AXI, addr: BigInt): BigInt = {
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

  describe("UART AXI4-Lite Peripheral") {

    it("should initialize to correct default values") {
      simulate(new UART_AXI(config)) { dut =>
        dut.clock.step(2)
        dut.tx.expect(true.B) // Idle line is high
        
        // Read STATUS: tx_empty should be 1 (bit 1), rx_ready should be 0 (bit 0)
        val status = axiRead(dut, 0x04)
        assert((status & 0x02) != 0, "tx_empty should be set initially")
        assert((status & 0x01) == 0, "rx_ready should be clear initially")
        
        // Read CTRL: should be 0
        val ctrl = axiRead(dut, 0x08)
        assert(ctrl == 0, "CTRL register should be zero initially")
        
        // Read DIVISOR: should be 0
        val div = axiRead(dut, 0x0C)
        assert(div == 0, "DIVISOR register should be zero initially")
      }
    }

    it("should allow configuring DIVISOR and CTRL registers") {
      simulate(new UART_AXI(config)) { dut =>
        dut.clock.step(1)
        
        // Configure Divisor to 27
        axiWrite(dut, 0x0C, 27)
        val div = axiRead(dut, 0x0C)
        assert(div == 27, "Divisor should be successfully updated to 27")
        
        // Configure Control to 0x0F (Enable TX/RX + both interrupts)
        axiWrite(dut, 0x08, 0x0F)
        val ctrl = axiRead(dut, 0x08)
        assert(ctrl == 0x0F, "Control should be successfully updated to 0x0F")
      }
    }

    it("should serialize and transmit data on TX pin correctly") {
      simulate(new UART_AXI(config)) { dut =>
        dut.clock.step(1)
        
        // Setup divisor = 1 (each bit lasts 16 clock cycles)
        axiWrite(dut, 0x0C, 1)
        // Enable transmitter (CTRL bit 0 = 1)
        axiWrite(dut, 0x08, 1)
        
        // Drive high initially
        dut.tx.expect(true.B)
        
        // Write byte 0x55 (binary: 01010101) to DATA register
        axiWrite(dut, 0x00, 0x55)
        
        // The serial transmission starts immediately on next clock ticks
        // Since divisor is 1, each bit lasts 16 clock cycles.
        
        // Start bit (low) - cycles 0-15
        for (_ <- 0 until 16) {
          dut.tx.expect(false.B)
          dut.clock.step(1)
        }
        
        // Data bits (0x55, LSB first: 1, 0, 1, 0, 1, 0, 1, 0)
        val expectedBits = Seq(1, 0, 1, 0, 1, 0, 1, 0)
        for (bit <- expectedBits) {
          for (_ <- 0 until 16) {
            dut.tx.expect(bit.U.asBool)
            dut.clock.step(1)
          }
        }
        
        // Stop bit (high) - cycles 144-159
        for (_ <- 0 until 16) {
          dut.tx.expect(true.B)
          dut.clock.step(1)
        }
        
        // Transmitter should be back to sIdle, status tx_empty should be high
        val status = axiRead(dut, 0x04)
        assert((status & 0x02) != 0, "tx_empty should be asserted after transmission completes")
      }
    }

    it("should receive and deserialize data on RX pin correctly") {
      simulate(new UART_AXI(config)) { dut =>
        dut.clock.step(1)
        
        // Setup divisor = 1
        axiWrite(dut, 0x0C, 1)
        // Enable receiver (CTRL bit 1 = 1)
        axiWrite(dut, 0x08, 2)
        
        dut.rx.poke(true.B) // Idle rx line high
        dut.clock.step(4)
        
        // Send byte 0x3C (binary: 00111100, LSB first: 0, 0, 1, 1, 1, 1, 0, 0)
        // Start bit (low) - 16 cycles
        dut.rx.poke(false.B)
        dut.clock.step(16)
        
        // Data bits (0, 0, 1, 1, 1, 1, 0, 0)
        val bitsToSend = Seq(0, 0, 1, 1, 1, 1, 0, 0)
        for (bit <- bitsToSend) {
          dut.rx.poke(bit.U.asBool)
          dut.clock.step(16)
        }
        
        // Stop bit (high) - 16 cycles
        dut.rx.poke(true.B)
        dut.clock.step(16)
        
        // RX should be ready, status rx_ready should be high
        var status = axiRead(dut, 0x04)
        assert((status & 0x01) != 0, "rx_ready should be asserted after valid byte is received")
        
        // Read received byte from DATA register
        val rxData = axiRead(dut, 0x00)
        assert(rxData == 0x3C, s"Received data should be 0x3C, got 0x${rxData.toString(16)}")
        
        // Status rx_ready should be cleared now
        status = axiRead(dut, 0x04)
        assert((status & 0x01) == 0, "rx_ready should be cleared after reading DATA")
      }
    }
  }
}
