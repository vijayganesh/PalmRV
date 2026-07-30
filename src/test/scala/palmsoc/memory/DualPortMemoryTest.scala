package palmsoc.memory

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim

/**
 * Test Suite for DualPortMemory
 *
 * Tests:
 * 1. Port A write then Port A read
 * 2. Port A write then Port B read (cross-port visibility)
 * 3. Simultaneous writes on both ports to different addresses
 * 4. Simultaneous reads on both ports from different addresses
 * 5. Read output defaults to zero when no read request is active
 */
class DualPortMemoryTest extends AnyFunSpec with ChiselSim {
  val bitWidth = 16
  val depth = 64

  private def driveIdle(dut: DualPortMemory): Unit = {
    dut.io.portA.en.poke(false.B)
    dut.io.portA.we.poke(false.B)
    dut.io.portA.addr.poke(0.U)
    dut.io.portA.din.poke(0.U)

    dut.io.portB.en.poke(false.B)
    dut.io.portB.we.poke(false.B)
    dut.io.portB.addr.poke(0.U)
    dut.io.portB.din.poke(0.U)
  }

  describe("DualPortMemory") {

    it("should write and read through Port A") {
      simulate(new DualPortMemory(bitWidth, depth)) { dut =>
        driveIdle(dut)
        dut.clock.step(1)

        // Write via Port A: mem[5] = 0x1234
        dut.io.portA.en.poke(true.B)
        dut.io.portA.we.poke(true.B)
        dut.io.portA.addr.poke(5.U)
        dut.io.portA.din.poke(0x1234.U)
        dut.clock.step(1)

        // Read via Port A from mem[5]
        dut.io.portA.en.poke(true.B)
        dut.io.portA.we.poke(false.B)
        dut.io.portA.addr.poke(5.U)
        dut.clock.step(1)

        // Sync read data is valid now
        dut.io.portA.dout.expect(0x1234.U)

        dut.io.portA.en.poke(false.B)
        dut.clock.step(1)
      }
    }

    it("should allow write on Port A and read on Port B") {
      simulate(new DualPortMemory(bitWidth, depth)) { dut =>
        driveIdle(dut)
        dut.clock.step(1)

        // Write via Port A: mem[9] = 0xBEEF
        dut.io.portA.en.poke(true.B)
        dut.io.portA.we.poke(true.B)
        dut.io.portA.addr.poke(9.U)
        dut.io.portA.din.poke(0xBEEF.U)
        dut.clock.step(1)

        // Read via Port B from mem[9]
        dut.io.portA.en.poke(false.B)
        dut.io.portB.en.poke(true.B)
        dut.io.portB.we.poke(false.B)
        dut.io.portB.addr.poke(9.U)
        dut.clock.step(1)

        // Sync read data is valid now
        dut.io.portB.dout.expect(0xBEEF.U)

        dut.io.portB.en.poke(false.B)
        dut.clock.step(1)
      }
    }

    it("should support simultaneous writes on both ports to different addresses") {
      simulate(new DualPortMemory(bitWidth, depth)) { dut =>
        driveIdle(dut)
        dut.clock.step(1)

        // Same cycle writes to different addresses
        dut.io.portA.en.poke(true.B)
        dut.io.portA.we.poke(true.B)
        dut.io.portA.addr.poke(10.U)
        dut.io.portA.din.poke(0x1111.U)

        dut.io.portB.en.poke(true.B)
        dut.io.portB.we.poke(true.B)
        dut.io.portB.addr.poke(11.U)
        dut.io.portB.din.poke(0x2222.U)
        dut.clock.step(1)

        // Read back both values simultaneously
        dut.io.portA.we.poke(false.B)
        dut.io.portA.addr.poke(10.U)
        dut.io.portB.we.poke(false.B)
        dut.io.portB.addr.poke(11.U)
        dut.clock.step(1)

        dut.io.portA.dout.expect(0x1111.U)
        dut.io.portB.dout.expect(0x2222.U)

        dut.io.portA.en.poke(false.B)
        dut.io.portB.en.poke(false.B)
        dut.clock.step(1)
      }
    }

    it("should support simultaneous reads on both ports") {
      simulate(new DualPortMemory(bitWidth, depth)) { dut =>
        driveIdle(dut)
        dut.clock.step(1)

        // Initialize two addresses
        dut.io.portA.en.poke(true.B)
        dut.io.portA.we.poke(true.B)
        dut.io.portA.addr.poke(20.U)
        dut.io.portA.din.poke(0xAAAA.U)

        dut.io.portB.en.poke(true.B)
        dut.io.portB.we.poke(true.B)
        dut.io.portB.addr.poke(21.U)
        dut.io.portB.din.poke(0x5555.U)
        dut.clock.step(1)

        // Read both simultaneously
        dut.io.portA.we.poke(false.B)
        dut.io.portA.addr.poke(20.U)
        dut.io.portB.we.poke(false.B)
        dut.io.portB.addr.poke(21.U)
        dut.clock.step(1)

        dut.io.portA.dout.expect(0xAAAA.U)
        dut.io.portB.dout.expect(0x5555.U)

        dut.io.portA.en.poke(false.B)
        dut.io.portB.en.poke(false.B)
        dut.clock.step(1)
      }
    }

    it("should drive dout to zero when no read request is active") {
      simulate(new DualPortMemory(bitWidth, depth)) { dut =>
        driveIdle(dut)
        dut.clock.step(2)

        dut.io.portA.dout.expect(0.U)
        dut.io.portB.dout.expect(0.U)
      }
    }
  }
}
