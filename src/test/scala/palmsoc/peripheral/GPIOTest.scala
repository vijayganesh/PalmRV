package palmsoc.peripheral

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim
import palmsoc.config.AXI4LiteConfig
import palmsoc.bus.AXI4LiteResp

/**
 * Test Suite for GPIO with AXI4-Lite Interface
 * 
 * Tests:
 * 1. Initialization - Default state verification
 * 2. Direction Control - Configure pins as inputs/outputs
 * 3. Input Reading - Read GPIO input values
 * 4. Output Writing - Set output values
 * 5. SET Operation - Atomic bit set
 * 6. CLEAR Operation - Atomic bit clear
 * 7. TOGGLE Operation - Atomic bit toggle
 * 8. Interrupt Generation - Rising edge interrupt
 * 9. Individual Pin Test - Test all 18 pins
 */

class GPIOTest extends AnyFunSpec with ChiselSim {
  val config = AXI4LiteConfig(32, 32)
  
  describe("GPIO AXI4-Lite Interface") {
    
    it("should initialize with all pins as inputs with zero output") {
      simulate(new GPIO_AXI(config, 18)) { dut =>
        dut.clock.step(1)
        
        // All pins should be inputs (direction = 0)
        dut.gpio_oe.expect(0.U)
        
        // Output should be zero
        dut.gpio_out.expect(0.U)
        
        // No interrupt
        dut.interrupt.expect(false.B)
      }
    }
    
    it("should configure pins as outputs and set output values") {
        var count = 1
      simulate(new GPIO_AXI(config, 18)) { dut =>
      dut.clock.step(1)
      print(s" Started the simulation for configuring pins as outputs and setting output values\n")
      // Write to DIRECTION register - set all pins as outputs
      dut.io.axi.awaddr.poke(0x04.U)
      dut.io.axi.awvalid.poke(true.B)

      //dut.clock.step(1)
      print(s" Waiting for AWREADY signal\n")
      while (!dut.io.axi.awready.peek().litToBoolean) {
        dut.clock.step(1)
      }
      dut.io.axi.awvalid.poke(true.B)
      //dut.clock.step(1)  // Step clock to allow state transition
      
      dut.io.axi.wdata.poke(0x3FFFF.U)  // All 18 pins as outputs
      dut.io.axi.wstrb.poke(0xF.U)
      dut.io.axi.wvalid.poke(true.B)
      dut.io.axi.bready.poke(true.B)
      //dut.clock.step(1)
      // dut.clock.step(1)
      print(s" Waiting for WREADY signal\n")
      while (!dut.io.axi.wready.peek().litToBoolean) {
        Thread.sleep(1000)
        dut.clock.step(1)
        count = count + 1
        if (count > 10) {
          throw new RuntimeException("Timeout waiting for WREADY")
        }
      }
      count = 0
      dut.clock.step(1)  // Step clock to allow state transition
      dut.io.axi.wvalid.poke(false.B)
      
      dut.io.axi.bready.poke(true.B)
      //dut.clock.step(1)
      print(s" Waiting for BVALID signal\n")
            count = 0

      while (!(dut.io.axi.bvalid.peek().litToBoolean && dut.io.axi.bready.peek().litToBoolean)){
        dut.clock.step(1)
         count = count + 1
        if (count > 10) {
          throw new RuntimeException("Timeout waiting for BValid")
        }
      }
    
      
      dut.clock.step(1)
      dut.io.axi.bready.poke(false.B)
      // Verify direction
      dut.gpio_oe.expect(0x3FFFF.U)
      dut.clock.step(2)
      // New operation
      // Write to OUTPUT register
      dut.io.axi.awaddr.poke(0x08.U)
      dut.io.axi.awvalid.poke(true.B)
      dut.io.axi.wvalid.poke(true.B)
        dut.io.axi.bready.poke(true.B)
      
      print(s" Waiting for AWREADY signal For Second Data\n")
            count = 0
    //dut.clock.step(1)
      while (!dut.io.axi.awready.peek().litToBoolean) {
        dut.clock.step(1)
         count = count + 1
        if (count > 10) {
          throw new RuntimeException("Timeout waiting for AWREADY Second Data")
        }
      }
      dut.io.axi.awvalid.poke(true.B)
      
      dut.io.axi.wdata.poke(0x2AAAA.U)  // Alternate pattern
      dut.io.axi.wstrb.poke(0xF.U)
      dut.io.axi.wvalid.poke(true.B)
      dut.io.axi.bready.poke(true.B)
      print(s" Waiting for WREADY signal For Second Data\n")
      count = 0

      while (!dut.io.axi.wready.peek().litToBoolean) {
        Thread.sleep(1000)
        dut.clock.step(1)
        count = count + 1
        if (count > 10) {
          throw new RuntimeException("Timeout waiting for WREADY For Second Data")
        }
      }
      count = 0
      dut.clock.step(1)  // Step clock to allow state transition
      dut.io.axi.wvalid.poke(false.B)
      
      dut.io.axi.bready.poke(true.B)
      print(s" Waiting for BVALID signal For Second Data\n")
      count = 0

      while (!(dut.io.axi.bvalid.peek().litToBoolean && dut.io.axi.bready.peek().litToBoolean)){
        dut.clock.step(1)
        count = count + 1
        if (count > 10) {
          throw new RuntimeException("Timeout waiting for BValid For Second Data")
        }
      }
    
      
      dut.clock.step(1)
      dut.io.axi.bready.poke(false.B)
      dut.clock.step(1)
      
      // Verify output
      dut.gpio_out.expect(0x2AAAA.U)
      }
    }
    
    it("should read input values from GPIO pins") {
      simulate(new GPIO_AXI(config, 18)) { dut =>
        dut.clock.step(1)
        
        // Apply input pattern
        dut.gpio_in.poke(0x15555.U)
        dut.clock.step(2)  // Wait for input to be registered
      
      // Read INPUT register
      dut.io.axi.araddr.poke(0x0C.U)
      dut.io.axi.arvalid.poke(true.B)
      while (!dut.io.axi.arready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.arvalid.poke(false.B)
      
      dut.io.axi.rready.poke(true.B)
      while (!dut.io.axi.rvalid.peek().litToBoolean) {
        dut.clock.step(1)
      }
        dut.io.axi.rdata.expect(0x15555.U)
        dut.io.axi.rready.poke(false.B)
      }
    }
    
    it("should support SET operation to set individual output bits") {
      simulate(new GPIO_AXI(config, 18)) { dut =>
      dut.clock.step(1)
      
      // Configure all pins as outputs
      dut.io.axi.awaddr.poke(0x04.U)
      dut.io.axi.awvalid.poke(true.B)
      while (!dut.io.axi.awready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.awvalid.poke(false.B)
      
      dut.io.axi.wdata.poke(0x3FFFF.U)
      dut.io.axi.wstrb.poke(0xF.U)
      dut.io.axi.wvalid.poke(true.B)
      while (!dut.io.axi.wready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.wvalid.poke(false.B)
      
      dut.io.axi.bready.poke(true.B)
      while (!dut.io.axi.bvalid.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.bready.poke(false.B)
      dut.clock.step(1)
      
      // Set bits 0, 2, 4 using SET register
      dut.io.axi.awaddr.poke(0x10.U)
      dut.io.axi.awvalid.poke(true.B)
      while (!dut.io.axi.awready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.awvalid.poke(false.B)
      
      dut.io.axi.wdata.poke(0x15.U)  // Binary: 010101
      dut.io.axi.wstrb.poke(0xF.U)
      dut.io.axi.wvalid.poke(true.B)
      while (!dut.io.axi.wready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.wvalid.poke(false.B)
      
      dut.io.axi.bready.poke(true.B)
      while (!dut.io.axi.bvalid.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.bready.poke(false.B)
        dut.clock.step(1)
        
        dut.gpio_out.expect(0x15.U)
      }
    }
    
    it("should support CLEAR operation to clear individual output bits") {
      simulate(new GPIO_AXI(config, 18)) { dut =>
      dut.clock.step(1)
      
      // Configure pins as outputs and set all high
      dut.io.axi.awaddr.poke(0x04.U)
      dut.io.axi.awvalid.poke(true.B)
      while (!dut.io.axi.awready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.awvalid.poke(false.B)
      
      dut.io.axi.wdata.poke(0x3FFFF.U)
      dut.io.axi.wstrb.poke(0xF.U)
      dut.io.axi.wvalid.poke(true.B)
      while (!dut.io.axi.wready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.wvalid.poke(false.B)
      
      dut.io.axi.bready.poke(true.B)
      while (!dut.io.axi.bvalid.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.bready.poke(false.B)
      dut.clock.step(1)
      
      // Set all outputs high
      dut.io.axi.awaddr.poke(0x08.U)
      dut.io.axi.awvalid.poke(true.B)
      while (!dut.io.axi.awready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.awvalid.poke(false.B)
      
      dut.io.axi.wdata.poke(0x3FFFF.U)
      dut.io.axi.wstrb.poke(0xF.U)
      dut.io.axi.wvalid.poke(true.B)
      while (!dut.io.axi.wready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.wvalid.poke(false.B)
      
      dut.io.axi.bready.poke(true.B)
      while (!dut.io.axi.bvalid.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.bready.poke(false.B)
      dut.clock.step(1)
      
      // Clear bits 1, 3, 5 using CLEAR register
      dut.io.axi.awaddr.poke(0x14.U)
      dut.io.axi.awvalid.poke(true.B)
      while (!dut.io.axi.awready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.awvalid.poke(false.B)
      
      dut.io.axi.wdata.poke(0x2A.U)  // Binary: 101010
      dut.io.axi.wstrb.poke(0xF.U)
      dut.io.axi.wvalid.poke(true.B)
      while (!dut.io.axi.wready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.wvalid.poke(false.B)
      
      dut.io.axi.bready.poke(true.B)
      while (!dut.io.axi.bvalid.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.bready.poke(false.B)
        dut.clock.step(1)
        
        dut.gpio_out.expect(0x3FFD5.U)  // All bits set except 1, 3, 5
      }
    }
    
    it("should support TOGGLE operation to toggle individual output bits") {
      simulate(new GPIO_AXI(config, 18)) { dut =>
      dut.clock.step(1)
      
      // Configure pins and set initial value
      dut.io.axi.awaddr.poke(0x04.U)
      dut.io.axi.awvalid.poke(true.B)
      while (!dut.io.axi.awready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.awvalid.poke(false.B)
      
      dut.io.axi.wdata.poke(0x3FFFF.U)
      dut.io.axi.wstrb.poke(0xF.U)
      dut.io.axi.wvalid.poke(true.B)
      while (!dut.io.axi.wready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.wvalid.poke(false.B)
      
      dut.io.axi.bready.poke(true.B)
      while (!dut.io.axi.bvalid.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.bready.poke(false.B)
      dut.clock.step(1)
      
      // Set output to 0xAAAA
      dut.io.axi.awaddr.poke(0x08.U)
      dut.io.axi.awvalid.poke(true.B)
      while (!dut.io.axi.awready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.awvalid.poke(false.B)
      
      dut.io.axi.wdata.poke(0x2AAAA.U)
      dut.io.axi.wstrb.poke(0xF.U)
      dut.io.axi.wvalid.poke(true.B)
      while (!dut.io.axi.wready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.wvalid.poke(false.B)
      
      dut.io.axi.bready.poke(true.B)
      while (!dut.io.axi.bvalid.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.bready.poke(false.B)
      dut.clock.step(1)
      
      // Toggle all bits
      dut.io.axi.awaddr.poke(0x18.U)
      dut.io.axi.awvalid.poke(true.B)
      while (!dut.io.axi.awready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.awvalid.poke(false.B)
      
      dut.io.axi.wdata.poke(0x3FFFF.U)
      dut.io.axi.wstrb.poke(0xF.U)
      dut.io.axi.wvalid.poke(true.B)
      while (!dut.io.axi.wready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.wvalid.poke(false.B)
      
      dut.io.axi.bready.poke(true.B)
      while (!dut.io.axi.bvalid.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.bready.poke(false.B)
        dut.clock.step(1)
        
        dut.gpio_out.expect(0x15555.U)  // Toggled pattern
      }
    }
    
    it("should generate interrupt on rising edge") {
      simulate(new GPIO_AXI(config, 18)) { dut =>
      dut.clock.step(1)
      
      // 1. Set INT_TYPE to edge (1)
      dut.io.axi.awaddr.poke(0x20.U)
      dut.io.axi.awvalid.poke(true.B)
      while (!dut.io.axi.awready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.awvalid.poke(false.B)
      
      dut.io.axi.wdata.poke(0x01.U)
      dut.io.axi.wstrb.poke(0xF.U)
      dut.io.axi.wvalid.poke(true.B)
      while (!dut.io.axi.wready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.wvalid.poke(false.B)
      
      dut.io.axi.bready.poke(true.B)
      while (!dut.io.axi.bvalid.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.bready.poke(false.B)
      dut.clock.step(1)
      
      // 2. Set INT_POL to rising (1)
      dut.io.axi.awaddr.poke(0x24.U)
      dut.io.axi.awvalid.poke(true.B)
      while (!dut.io.axi.awready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.awvalid.poke(false.B)
      
      dut.io.axi.wdata.poke(0x01.U)
      dut.io.axi.wstrb.poke(0xF.U)
      dut.io.axi.wvalid.poke(true.B)
      while (!dut.io.axi.wready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.wvalid.poke(false.B)
      
      dut.io.axi.bready.poke(true.B)
      while (!dut.io.axi.bvalid.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.bready.poke(false.B)
      dut.clock.step(1)
      
      // 3. Enable interrupt for pin 0 (now safely configured as edge-triggered)
      dut.io.axi.awaddr.poke(0x1C.U)  // INT_EN
      dut.io.axi.awvalid.poke(true.B)
      while (!dut.io.axi.awready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.awvalid.poke(false.B)
      
      dut.io.axi.wdata.poke(0x01.U)
      dut.io.axi.wstrb.poke(0xF.U)
      dut.io.axi.wvalid.poke(true.B)
      while (!dut.io.axi.wready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.wvalid.poke(false.B)
      
      dut.io.axi.bready.poke(true.B)
      while (!dut.io.axi.bvalid.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.bready.poke(false.B)
      dut.clock.step(1)
      
      // Clear any spurious interrupt that might have somehow fired
      dut.io.axi.awaddr.poke(0x28.U)  // INT_STAT
      dut.io.axi.awvalid.poke(true.B)
      while (!dut.io.axi.awready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.awvalid.poke(false.B)
      dut.io.axi.wdata.poke("hFFFFFFFF".U)
      dut.io.axi.wstrb.poke(0xF.U)
      dut.io.axi.wvalid.poke(true.B)
      while (!dut.io.axi.wready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.wvalid.poke(false.B)
      dut.io.axi.bready.poke(true.B)
      while (!dut.io.axi.bvalid.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.bready.poke(false.B)
      dut.clock.step(1)
        
        // Apply rising edge on pin 0
        dut.gpio_in.poke(0x00.U)
        dut.clock.step(2)
        dut.interrupt.expect(false.B)
        
        dut.gpio_in.poke(0x01.U)
        dut.clock.step(2)
        
        // Interrupt should be asserted
        dut.interrupt.expect(true.B)
      
      // Clear interrupt
      dut.io.axi.awaddr.poke(0x28.U)  // INT_STAT
      dut.io.axi.awvalid.poke(true.B)
      while (!dut.io.axi.awready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.awvalid.poke(false.B)
      
      dut.io.axi.wdata.poke(0x01.U)  // Write 1 to clear
      dut.io.axi.wstrb.poke(0xF.U)
      dut.io.axi.wvalid.poke(true.B)
      while (!dut.io.axi.wready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.wvalid.poke(false.B)
      
      dut.io.axi.bready.poke(true.B)
      while (!dut.io.axi.bvalid.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.bready.poke(false.B)
        dut.clock.step(1)
        
        // Interrupt should be cleared
        dut.interrupt.expect(false.B)
      }
    }
    
    it("should test all 18 pins individually") {
      simulate(new GPIO_AXI(config, 18)) { dut =>
      dut.clock.step(1)
      
      // Configure all 18 pins as outputs
      dut.io.axi.awaddr.poke(0x04.U)
      dut.io.axi.awvalid.poke(true.B)
      while (!dut.io.axi.awready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.awvalid.poke(false.B)
      
      dut.io.axi.wdata.poke(0x3FFFF.U)  // 18 bits all set
      dut.io.axi.wstrb.poke(0xF.U)
      dut.io.axi.wvalid.poke(true.B)
      while (!dut.io.axi.wready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.wvalid.poke(false.B)
      
      dut.io.axi.bready.poke(true.B)
      while (!dut.io.axi.bvalid.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.bready.poke(false.B)
      dut.clock.step(1)
      
      // Test each pin individually
      for (pin <- 0 until 18) {
        val pin_mask = 1 << pin
        
        // Set only this pin high
        dut.io.axi.awaddr.poke(0x08.U)
        dut.io.axi.awvalid.poke(true.B)
      while (!dut.io.axi.awready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.awvalid.poke(false.B)
        
        dut.io.axi.wdata.poke(pin_mask.U)
        dut.io.axi.wstrb.poke(0xF.U)
        dut.io.axi.wvalid.poke(true.B)
      while (!dut.io.axi.wready.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.wvalid.poke(false.B)
        
        dut.io.axi.bready.poke(true.B)
      while (!dut.io.axi.bvalid.peek().litToBoolean) { dut.clock.step(1) }
      dut.clock.step(1)
      dut.io.axi.bready.poke(false.B)
          dut.clock.step(1)
          
          // Verify output
          dut.gpio_out.expect(pin_mask.U)
        }
      }
    }
  }
}
