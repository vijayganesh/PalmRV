package palmsoc.memory

import chisel3._
// import chiseltest._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import chisel3.simulator.scalatest.ChiselSim
import chisel3.simulator.stimulus.{RunUntilFinished, RunUntilSuccess}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.Tag
// import svsim.verilator.Backend
// import svsim.verilator.Backend.CompilationSettings


/**
 * Test Suite for SRAM with Wishbone Interface
 * 
 * Tests:
 * 1. Basic Write and Read - Single word operations
 * 2. Byte Enable Write - Partial word writes
 * 3. Sequential Access - Back-to-back transactions
 * 4. Address Bounds - Out of bounds error handling
 * 5. Protocol Compliance - Proper CYC/STB/ACK handshaking
 */

abstract class SRAMBaseSpec extends AnyFunSpec with ChiselSim {
final val AutoTag = "palmsoc.SRAM"

  override def tags: Map[String, Set[String]] =
    super.tags.view.mapValues(_ + AutoTag).toMap
}
class SRAMTest extends AnyFunSpec with ChiselSim {
  val addrWidth = 12  // 4K words
  val dataWidth = 32  // 32-bit data
  val depth = 1024    // 1K words
  
//   val backendSettings =  Backend.Settings(
//       CompilationSettings(
//         trace = true,        // enable tracing
//         traceFormat = "vcd"  // or "fst"
//       )
//     )
 describe("SRAM Wishbone Interface") {
  
  it("perform basic write and read operations") {
    simulate(new SRAM(addrWidth, dataWidth, depth),      
        
        ) { dut =>
      // Write 0xDEADBEEF to address 0x10
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(0x10.U)
      dut.io.wb.dat_w.poke(0xDEADBEEFL.U)
      dut.io.wb.sel.poke(0xF.U)  // All bytes enabled
      dut.clock.step(1)
      
      // Wait for write acknowledgment
      dut.io.wb.ack.expect(false.B)
      dut.clock.step(1)
      dut.io.wb.ack.expect(true.B)
      
      // End transaction
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
      
      // Read back from address 0x10
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(0x10.U)
      dut.clock.step(1)
      
      // Wait for read acknowledgment
      dut.io.wb.ack.expect(false.B)
      dut.clock.step(1)
      dut.io.wb.ack.expect(true.B)
      dut.io.wb.dat_r.expect(0xDEADBEEFL.U)
      
      // End transaction
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }
  
  it("handle byte-enable writes correctly") {
    simulate(new SRAM(addrWidth, dataWidth, depth)) { dut =>
      // Write initial value 0xFFFFFFFF to address 0x20
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(0x20.U)
      dut.io.wb.dat_w.poke(0xFFFFFFFFL.U)
      dut.io.wb.sel.poke(0xF.U)
      dut.clock.step(1)
      dut.clock.step(1)  // Wait for ack
      dut.io.wb.ack.expect(true.B)
      
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
      
      // Write only lower byte (0x12) with sel = 0x1
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(0x20.U)
      dut.io.wb.dat_w.poke(0x12345678L.U)
      dut.io.wb.sel.poke(0x1.U)  // Only byte 0
      dut.clock.step(1)
      dut.clock.step(1)
      dut.io.wb.ack.expect(true.B)
      
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
      
      // Read back - should be 0xFFFFFF78 (only lower byte changed)
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(0x20.U)
      dut.clock.step(1)
      dut.clock.step(1)
      dut.io.wb.ack.expect(true.B)
      dut.io.wb.dat_r.expect(0xFFFFFF78L.U)
      
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }
  
  it("handle sequential write and read operations") {
    simulate(new SRAM(addrWidth, dataWidth, depth)) { dut =>
      val testData = Seq(0xAAAAAAAAL, 0x55555555L, 0x12345678L, 0xFEDCBA98L)
      val baseAddr = 0x100
      
      // Sequential writes
      for ((data, offset) <- testData.zipWithIndex) {
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(true.B)
        dut.io.wb.adr.poke((baseAddr + offset).U)
        dut.io.wb.dat_w.poke(data.U)
        dut.io.wb.sel.poke(0xF.U)
        dut.clock.step(1)
        dut.clock.step(1)
        dut.io.wb.ack.expect(true.B)
        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)
      }
      
      // Sequential reads
      for ((expectedData, offset) <- testData.zipWithIndex) {
        dut.io.wb.cyc.poke(true.B)
        dut.io.wb.stb.poke(true.B)
        dut.io.wb.we.poke(false.B)
        dut.io.wb.adr.poke((baseAddr + offset).U)
        dut.clock.step(1)
        dut.clock.step(1)
        dut.io.wb.ack.expect(true.B)
        dut.io.wb.dat_r.expect(expectedData.U)
        dut.io.wb.cyc.poke(false.B)
        dut.io.wb.stb.poke(false.B)
        dut.clock.step(1)
      }
    }
  }
  
  it("detect and signal out-of-bounds address errors") {
    simulate(new SRAM(addrWidth, dataWidth, depth)) { dut =>
      // Try to access address beyond depth
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke((depth + 100).U)  // Out of bounds
      dut.io.wb.dat_w.poke(0xDEADBEEFL.U)
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
  
  it("follow proper Wishbone handshaking protocol") {
    simulate(new SRAM(addrWidth, dataWidth, depth)) { dut =>
      // Initially no transaction
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
      dut.io.wb.ack.expect(false.B)
      dut.io.wb.err.expect(false.B)
      
      // Start write with CYC but no STB - should not respond
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(false.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(0x50.U)
      dut.io.wb.dat_w.poke(0x11111111L.U)
      dut.io.wb.sel.poke(0xF.U)
      dut.clock.step(1)
      dut.io.wb.ack.expect(false.B)
      
      // Assert STB - transaction should proceed
      dut.io.wb.stb.poke(true.B)
      dut.clock.step(1)
      dut.clock.step(1)
      dut.io.wb.ack.expect(true.B)
      
      // De-assert CYC - should return to idle
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
      dut.io.wb.ack.expect(false.B)
    }
  }
  
  it("handle multiple byte select patterns") {
    simulate(new SRAM(addrWidth, dataWidth, depth)) { dut =>
      val addr = 0x60
      
      // Write all F's first
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(addr.U)
      dut.io.wb.dat_w.poke(0xFFFFFFFFL.U)
      dut.io.wb.sel.poke(0xF.U)
      dut.clock.step(2)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
      
      // Write with sel = 0x3 (lower 2 bytes)
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(true.B)
      dut.io.wb.adr.poke(addr.U)
      dut.io.wb.dat_w.poke(0x12345678L.U)
      dut.io.wb.sel.poke(0x3.U)
      dut.clock.step(2)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
      
      // Read and verify - should be 0xFFFF5678
      dut.io.wb.cyc.poke(true.B)
      dut.io.wb.stb.poke(true.B)
      dut.io.wb.we.poke(false.B)
      dut.io.wb.adr.poke(addr.U)
      dut.clock.step(2)
      dut.io.wb.dat_r.expect(0xFFFF5678L.U)
      dut.io.wb.cyc.poke(false.B)
      dut.io.wb.stb.poke(false.B)
      dut.clock.step(1)
    }
  }
}
}
