package palmsoc.memory

import chisel3._
import chisel3.util._

/**
 * Generic dual-port memory.
 *
 * Features:
 * - Configurable data width (`bitWidth`) and memory depth (`depth`)
 * - Two independent ports
 * - Each port supports read or write per cycle
 * - Synchronous read behavior
 *
 * Notes:
 * - Read data is returned one cycle after a read request.
 * - Simultaneous writes to the same address from both ports are not resolved here;
 *   the resulting value is implementation-dependent.
 */
class DualPortMemoryPortIO(addrWidth: Int, bitWidth: Int) extends Bundle {
  val en   = Input(Bool())
  val we   = Input(Bool())
  val addr = Input(UInt(addrWidth.W))
  val din  = Input(UInt(bitWidth.W))
  val dout = Output(UInt(bitWidth.W))
}

class DualPortMemory(bitWidth: Int, depth: Int) extends Module {
  require(bitWidth > 0, "bitWidth must be > 0")
  require(depth > 0, "depth must be > 0")

  private val addrWidth = log2Ceil(depth)

  val io = IO(new Bundle {
    val portA = new DualPortMemoryPortIO(addrWidth, bitWidth)
    val portB = new DualPortMemoryPortIO(addrWidth, bitWidth)
  })

  val mem = SyncReadMem(depth, UInt(bitWidth.W))

  val portAReadEn = io.portA.en && !io.portA.we
  val portBReadEn = io.portB.en && !io.portB.we

  val portAReadData = mem.read(io.portA.addr, portAReadEn)
  val portBReadData = mem.read(io.portB.addr, portBReadEn)

  val portAReadValid = RegNext(portAReadEn, false.B)
  val portBReadValid = RegNext(portBReadEn, false.B)

  when(io.portA.en && io.portA.we) {
    mem.write(io.portA.addr, io.portA.din)
  }

  when(io.portB.en && io.portB.we) {
    mem.write(io.portB.addr, io.portB.din)
  }

  io.portA.dout := Mux(portAReadValid, portAReadData, 0.U)
  io.portB.dout := Mux(portBReadValid, portBReadData, 0.U)
}

object DualPortMemory {
  def apply(bitWidth: Int, depth: Int): DualPortMemory = new DualPortMemory(bitWidth, depth)
}
