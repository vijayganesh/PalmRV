package palmsoc.core

import chisel3._
import chisel3.util._

class SequentialMultiplier extends Module {
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

  val multiplicand = Reg(UInt(64.W))
  val multiplier = Reg(UInt(32.W))
  val accumulator = Reg(UInt(64.W))
  val count = RegInit(0.U(6.W))
  val final_sign = RegInit(false.B)
  val saved_alu_op = RegInit(ALUOp.ADD)
  
  // Is this a multiply operation?
  val is_mul_op = io.alu_op === ALUOp.MUL || io.alu_op === ALUOp.MULH || 
                  io.alu_op === ALUOp.MULHSU || io.alu_op === ALUOp.MULHU
                  
  io.ready := (state === s_done)
  
  // Calculate final result based on sign
  val final_acc = Mux(final_sign, (~accumulator) + 1.U, accumulator)
  val is_upper = saved_alu_op === ALUOp.MULH || saved_alu_op === ALUOp.MULHU || saved_alu_op === ALUOp.MULHSU
  
  io.result := Mux(is_upper, final_acc(63, 32), final_acc(31, 0))

  switch(state) {
    is(s_idle) {
      when(io.valid_in && is_mul_op) {
        state := s_calc
        count := 32.U
        accumulator := 0.U
        saved_alu_op := io.alu_op
        
        // Sign extension rules based on RISC-V M-Extension spec
        val sign1 = (io.alu_op === ALUOp.MULH || io.alu_op === ALUOp.MULHSU) && io.op1(31)
        val sign2 = (io.alu_op === ALUOp.MULH) && io.op2(31)
        
        val negate_op1 = sign1
        val negate_op2 = sign2
        
        val abs_op1 = Mux(negate_op1, (~io.op1) + 1.U, io.op1)
        val abs_op2 = Mux(negate_op2, (~io.op2) + 1.U, io.op2)
        
        multiplicand := Cat(0.U(32.W), abs_op1)
        multiplier := abs_op2
        
        final_sign := negate_op1 ^ negate_op2
      }
    }
    
    is(s_calc) {
      when(count === 0.U) {
        state := s_done
      }.otherwise {
        val adder_res = Mux(multiplier(0), accumulator + multiplicand, accumulator)
        accumulator := adder_res
        multiplicand := multiplicand << 1
        multiplier := multiplier >> 1
        count := count - 1.U
      }
    }
    
    is(s_done) {
      // Stay in done until valid drops or another handshake implies we're done.
      // In this core, valid_in will stay high until the pipeline is unstalled.
      // So we wait for valid_in to drop to go back to idle.
      when(!io.valid_in) {
        state := s_idle
      }
    }
  }
}
