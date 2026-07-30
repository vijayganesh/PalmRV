package palmsoc.peripheral

import chisel3._
import chisel3.util._
import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim
import palmsoc.config.AXI4LiteConfig

class DMATest extends AnyFunSpec with ChiselSim {
  describe("4-Channel Low Power DMA Controller") {
    it("should correctly write and read configuration registers") {
      val config = AXI4LiteConfig(32, 32)
      simulate(new DMA_AXI(config, 4)) { dut =>
        dut.clock.step(1)
        
        // Write SRC_ADDR to Channel 1 (Offset 0x20)
        dut.io.axi_slave.awaddr.poke(0x20.U)
        dut.io.axi_slave.awvalid.poke(true.B)
        dut.io.axi_slave.wdata.poke(0x1000.U)
        dut.io.axi_slave.wvalid.poke(true.B)
        dut.io.axi_slave.bready.poke(true.B)
        
        // Wait for awready
        while(dut.io.axi_slave.awready.peek().litValue == 0) { dut.clock.step(1) }
        dut.clock.step(1)
        dut.io.axi_slave.awvalid.poke(false.B)
        
        // Wait for wready
        while(dut.io.axi_slave.wready.peek().litValue == 0) { dut.clock.step(1) }
        dut.clock.step(1)
        dut.io.axi_slave.wvalid.poke(false.B)
        
        // Wait for bvalid
        while(dut.io.axi_slave.bvalid.peek().litValue == 0) { dut.clock.step(1) }
        dut.clock.step(1)
        
        // Read SRC_ADDR back from Channel 1
        dut.io.axi_slave.araddr.poke(0x20.U)
        dut.io.axi_slave.arvalid.poke(true.B)
        dut.io.axi_slave.rready.poke(true.B)
        
        dut.clock.step(1)
        dut.io.axi_slave.arvalid.poke(false.B)
        
        // Wait for valid response
        while (dut.io.axi_slave.rvalid.peek().litValue == 0) {
          dut.clock.step(1)
        }
        
        dut.io.axi_slave.rdata.expect(0x1000.U)
      }
    }
    
    it("should park in low power idle state when no channels are active") {
      val config = AXI4LiteConfig(32, 32)
      simulate(new DMA_AXI(config, 4)) { dut =>
        dut.clock.step(5)
        
        // In IDLE state, all Master interface signals must be 0 for power savings
        dut.io.axi_master.arvalid.expect(false.B)
        dut.io.axi_master.araddr.expect(0.U)
        dut.io.axi_master.awvalid.expect(false.B)
        dut.io.axi_master.awaddr.expect(0.U)
        dut.io.axi_master.wvalid.expect(false.B)
        dut.io.axi_master.wdata.expect(0.U)
      }
    }
  }
}
