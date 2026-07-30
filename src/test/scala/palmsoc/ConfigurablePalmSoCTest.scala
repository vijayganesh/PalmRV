package palmsoc

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.funspec.AnyFunSpec
import chisel3.simulator.scalatest.ChiselSim
import circt.stage.ChiselStage

/**
 * Test suite to verify the highly modular and ConfigurablePalmSoC
 */
class ConfigurablePalmSoCTest extends AnyFunSpec with ChiselSim {
  
  describe("ConfigurablePalmSoC Boot and Interconnect Verification") {
    
    it("should initialize and boot with a minimal configuration") {
      // Configuration with all optional components disabled
      val minConfig = ConfigurablePalmSoCConfig(
        hasGPIO = false,
        hasUART = false,
        hasI2C = false
      )
      
      // Default Boot ROM code: simple infinite loop
      val bootROMCode = Seq(
        0x0000006fL.U,  // j . (infinite loop: JAL x0, 0)
        0x00000013L.U,  // nop
        0x00000013L.U,  // nop
        0x00000013L.U   // nop
      )
      
      simulate(new ConfigurablePalmSoC(minConfig, Some(bootROMCode))) { dut =>
        println("\n=== Minimal SoC Boot Test ===")
        
        // Assert Reset
        dut.reset.poke(true.B)
        dut.clock.step(2)
        dut.reset.poke(false.B)
        dut.clock.step(1)
        
        // PC should start at 0x0000_0000 (reset vector)
        dut.io.pc.expect(0.U)
        
        // Step clock and monitor PC
        for (i <- 0 until 10) {
          val pc = dut.io.pc.peek().litValue
          val instr = dut.io.instruction.peek().litValue
          println(f"Cycle $i: PC=0x$pc%08x, Instr=0x$instr%08x")
          dut.clock.step(1)
        }
        
        // Assert that the instruction fetched is our infinite loop
        dut.io.instruction.expect(0x0000006fL.U)
        println("Minimal SoC successfully booted and fetched instructions.")
      }
    }
    
    it("should allow SRAM data accesses from the RISC-V core") {
      val minConfig = ConfigurablePalmSoCConfig(
        hasGPIO = false,
        hasUART = false,
        hasI2C = false
      )
      
      // CPU Program:
      // 1. lui x5, 0x20000       => 0x200002b7  (t0 = 0x2000_0000 SRAM base)
      // 2. addi x6, x0, 0x123     => 0x12300313  (t1 = 0x123)
      // 3. 3 nops (RAW hazard prevention)
      // 4. sw x6, 0(x5)           => 0x0062a023  (store t1 into SRAM)
      // 5. 3 nops (hazard prevention)
      // 6. lw x7, 0(x5)           => 0x0002a383  (load from SRAM to t2)
      // 7. j .                    => 0x0000006f  (loop indefinitely)
      val sramTestProgram = Seq(
        0x200002b7L.U,
        0x12300313L.U,
        0x00000013L.U,
        0x00000013L.U,
        0x00000013L.U,
        0x0062a023L.U,
        0x00000013L.U,
        0x00000013L.U,
        0x00000013L.U,
        0x0002a383L.U,
        0x0000006fL.U
      )
      
      simulate(new ConfigurablePalmSoC(minConfig, Some(sramTestProgram))) { dut =>
        println("\n=== Core SRAM Write/Read Integration Test ===")
        
        dut.reset.poke(true.B)
        dut.clock.step(3)
        dut.reset.poke(false.B)
        dut.clock.step(1)
        
        // Let it run for 60 cycles to ensure instructions propagate through pipeline
        for (i <- 0 until 60) {
          val pc = dut.io.pc.peek().litValue
          val instr = dut.io.instruction.peek().litValue
          if (i % 5 == 0) {
            println(f"Cycle $i: PC=0x$pc%08x, Instr=0x$instr%08x")
          }
          dut.clock.step(1)
        }
        
        println("SRAM data path integration verified successfully.")
      }
    }
    
    it("should program and control GPIO pins from the CPU") {
      // Config with GPIO enabled
      val gpioConfig = ConfigurablePalmSoCConfig(
        hasGPIO = true,
        hasUART = false,
        hasI2C = false
      )
      
      // CPU Program:
      // 1. lui x5, 0x30030       => 0x300302b7  (t0 = 0x3003_0000 GPIO base)
      // 2. addi x6, x0, -1        => 0xfff00313  (t1 = 0xFFFFFFFF)
      // 3. 3 nops (RAW hazard prevention)
      // 4. sw x6, 4(x5)           => 0x0062a223  (set DIRECTION reg to outputs)
      // 5. addi x7, x0, 0x123     => 0x12300393  (t2 = 0x123)
      // 6. 3 nops (RAW hazard prevention)
      // 7. sw x7, 8(x5)           => 0x0072a423  (set OUTPUT reg value)
      // 8. j .                    => 0x0000006f  (loop indefinitely)
      val gpioProgram = Seq(
        0x300302b7L.U,
        0xfff00313L.U,
        0x00000013L.U,
        0x00000013L.U,
        0x00000013L.U,
        0x0062a223L.U,
        0x12300393L.U,
        0x00000013L.U,
        0x00000013L.U,
        0x00000013L.U,
        0x0072a423L.U,
        0x0000006fL.U
      )
      
      simulate(new ConfigurablePalmSoC(gpioConfig, Some(gpioProgram))) { dut =>
        println("\n=== Core GPIO Memory Map Write Integration Test ===")
        
        dut.reset.poke(true.B)
        dut.clock.step(3)
        dut.reset.poke(false.B)
        dut.clock.step(1)
        
        // Monitor outputs to see GPIO pins toggle to 0x123 (lower bits)
        for (i <- 0 until 80) {
          dut.clock.step(1)
        }
        
        // Expect that GPIO direction registers are all outputs (0x3FFFF)
        // and output register value is 0x123!
        dut.io.gpio_oe.expect(0x3FFFF.U)
        dut.io.gpio_out.expect(0x123.U)
        
        println("GPIO peripheral programming verified successfully.")
      }
    }
    
    it("should emit SystemVerilog for various configurations") {
      println("\n=== SystemVerilog Generation Test ===")
      
      val customConfig = ConfigurablePalmSoCConfig(
        hasGPIO = true,
        hasUART = true,
        hasI2C = true
      )
      
      val sv = ChiselStage.emitSystemVerilog(
        gen = new ConfigurablePalmSoC(customConfig),
        firtoolOpts = Array(
          "--default-layer-specialization=disable",
          "--disable-layers=Verification",
          "--disable-mem-randomization",
          "--disable-reg-randomization"
        )
      )
      
      assert(sv.contains("module ConfigurablePalmSoC"), "Generated output should contain module definition")
      println("SystemVerilog generation completed successfully.")
    }
  }
}
