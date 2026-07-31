package palmsoc.core

import chisel3._
import chisel3.util._

class SequentialDivider extends Module {
  val io = IO(new Bundle {
    val valid_in = Input(Bool())
    val op1 = Input(UInt(32.W))
    val op2 = Input(UInt(32.W))
    val alu_op = Input(ALUOp())
    
    val result = Output(UInt(32.W))
    val ready = Output(Bool())
  })

  val s_idle :: s_calc :: s_done :: Nil = Enum(3)
  val state = RegInit(s_idle)

  val divisor = Reg(UInt(32.W))
  val RQ = Reg(UInt(64.W)) // Remainder (upper 32) and Quotient (lower 32)
  val count = RegInit(0.U(6.W))
  
  val is_div_zero = RegInit(false.B)
  val q_sign = RegInit(false.B)
  val r_sign = RegInit(false.B)
  val saved_alu_op = RegInit(ALUOp.ADD)
  val saved_op1 = RegInit(0.U(32.W))
  
  val is_div_op = io.alu_op === ALUOp.DIV || io.alu_op === ALUOp.DIVU || 
                  io.alu_op === ALUOp.REM || io.alu_op === ALUOp.REMU
                  
  io.ready := (state === s_done)
  
  // Format the output
  val is_rem = saved_alu_op === ALUOp.REM || saved_alu_op === ALUOp.REMU
  val raw_q = RQ(31, 0)
  val raw_r = RQ(63, 32)
  
  val final_q = Mux(q_sign, (~raw_q) + 1.U, raw_q)
  val final_r = Mux(r_sign, (~raw_r) + 1.U, raw_r)
  
  // Handle divide-by-zero outputs per RISC-V spec
  val div_zero_q = ~(0.U(32.W)) // -1
  val div_zero_r = saved_op1
  
  val actual_q = Mux(is_div_zero, div_zero_q, final_q)
  val actual_r = Mux(is_div_zero, div_zero_r, final_r)
  
  io.result := Mux(is_rem, actual_r, actual_q)

  switch(state) {
    is(s_idle) {
      when(io.valid_in && is_div_op) {
        state := s_calc
        count := 32.U
        saved_alu_op := io.alu_op
        saved_op1 := io.op1
        
        val is_signed = io.alu_op === ALUOp.DIV || io.alu_op === ALUOp.REM
        val sign1 = is_signed && io.op1(31)
        val sign2 = is_signed && io.op2(31)
        
        val abs_op1 = Mux(sign1, (~io.op1) + 1.U, io.op1)
        val abs_op2 = Mux(sign2, (~io.op2) + 1.U, io.op2)
        
        RQ := Cat(0.U(32.W), abs_op1)
        divisor := abs_op2
        
        q_sign := sign1 ^ sign2
        r_sign := sign1
        
        is_div_zero := (io.op2 === 0.U)
        
        // Special case: overflow in signed division (-2^31 / -1)
        // RISC-V spec says q = -2^31, r = 0.
        // -2^31 is 0x80000000. 
        // With our logic, abs(-2^31) is 0x80000000. abs(-1) is 1.
        // 0x80000000 / 1 = 0x80000000. 
        // q_sign will be true (1 ^ 1 = 0? Wait, sign1=1, sign2=1 -> sign1^sign2=0! So q_sign is false).
        // If q_sign is false, final_q is 0x80000000. This perfectly matches the spec!
        // Remainder will be 0, r_sign true, -0 = 0. Matches spec perfectly.
      }
    }
    
    is(s_calc) {
      when(count === 0.U) {
        state := s_done
      }.otherwise {
        // Restoring division algorithm step
        val RQ_shifted = RQ << 1
        val rem_part = RQ_shifted(63, 32)
        val diff = rem_part - divisor
        
        // If MSB of diff is 0, it means rem_part >= divisor
        // We use a 33-bit subtraction to catch underflow easily
        val diff33 = Cat(0.U(1.W), rem_part) - Cat(0.U(1.W), divisor)
        val can_subtract = (diff33(32) === 0.U)
        
        when(can_subtract) {
          // Remainder gets difference, quotient gets 1 in LSB
          RQ := Cat(diff33(31, 0), RQ_shifted(31, 1), 1.U(1.W))
        }.otherwise {
          // Remainder stays same, quotient gets 0 in LSB
          RQ := RQ_shifted
        }
        
        count := count - 1.U
      }
    }
    
    is(s_done) {
      when(!io.valid_in) {
        state := s_idle
      }
    }
  }
}
