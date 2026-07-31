package palmsoc.core

import chisel3._ 
import chisel3.util._ 

class ExecuteStage(val config: palmsoc.config.SoCConfig = palmsoc.config.DefaultSoCConfig()) extends Module {
  val io = IO(new Bundle {
    // Inputs from decode
    val pc_in = Input(UInt(32.W))
    val rs1_data = Input(UInt(32.W))
    val rs2_data = Input(UInt(32.W))
    val rs1_addr_in = Input(UInt(5.W))
    val rs2_addr_in = Input(UInt(5.W))
    val forward_a_sel = Input(UInt(2.W))
    val forward_b_sel = Input(UInt(2.W))
    val forward_mem_data = Input(UInt(32.W))
    val forward_wb_data = Input(UInt(32.W))
    val imm = Input(UInt(32.W))
    val rd_addr_in = Input(UInt(5.W))
    val alu_op = Input(ALUOp())
    val alu_src1_sel = Input(Bool())
    val alu_src2_sel = Input(Bool())
    val branch = Input(Bool())
    val jump = Input(Bool())
    val mem_read_in = Input(Bool())
    val mem_write_in = Input(Bool())
    val mem_size_in = Input(UInt(2.W))
    val mem_unsigned_in = Input(Bool())
    val reg_write_in = Input(Bool())
    val wb_sel_in = Input(UInt(2.W))
    val valid_in = Input(Bool())
    
    // CSR ports
    val csr_cmd_in = Input(UInt(3.W))
    val csr_addr_in = Input(UInt(12.W))
    val mret_in = Input(Bool())
    
    // Branch/Jump outputs
    val branch_taken = Output(Bool())
    val jump_taken = Output(Bool())
    val target_pc = Output(UInt(32.W))
    
    // ALU result and pass-through
    val alu_result = Output(UInt(32.W))
    val mem_write_data = Output(UInt(32.W))
    val rd_addr_out = Output(UInt(5.W))
    val mem_read_out = Output(Bool())
    val mem_write_out = Output(Bool())
    val mem_size_out = Output(UInt(2.W))
    val mem_unsigned_out = Output(Bool())
    val reg_write_out = Output(Bool())
    val wb_sel_out = Output(UInt(2.W))
    val pc_plus_4 = Output(UInt(32.W))
    val valid_out = Output(Bool())
    
    // CSR outputs
    val csr_cmd_out = Output(UInt(3.W))
    val csr_addr_out = Output(UInt(12.W))
    val mret_out = Output(Bool())
    
    // Control
    val stall = Input(Bool())
    val flush = Input(Bool())
    val ex_stall = Output(Bool())
  })
  
  // Forwarding multiplexers
  val resolved_rs1_data = MuxLookup(io.forward_a_sel, io.rs1_data)(Seq(
    1.U -> io.forward_mem_data,
    2.U -> io.forward_wb_data
  ))
  
  val resolved_rs2_data = MuxLookup(io.forward_b_sel, io.rs2_data)(Seq(
    1.U -> io.forward_mem_data,
    2.U -> io.forward_wb_data
  ))
  
  // ALU source selection
  val alu_src1 = Mux(io.alu_src1_sel, io.pc_in, resolved_rs1_data)
  val alu_src2 = Mux(io.alu_src2_sel, io.imm, resolved_rs2_data)
  
  // High-speed parallel ALU optimization:
  val is_sub = io.alu_op === ALUOp.SUB || io.alu_op === ALUOp.SLT || io.alu_op === ALUOp.SLTU
  val adder_out = Mux(is_sub, alu_src1 - alu_src2, alu_src1 + alu_src2)

  val slt_val = alu_src1.asSInt < alu_src2.asSInt
  val sltu_val = alu_src1 < alu_src2
  val comparison_out = Mux(io.alu_op === ALUOp.SLT, slt_val, sltu_val)

  val is_comparison = io.alu_op === ALUOp.SLT || io.alu_op === ALUOp.SLTU
  val arith_out = Cat(adder_out(31, 1), Mux(is_comparison, comparison_out, adder_out(0)))

  val logical_out = Mux(io.alu_op === ALUOp.AND, alu_src1 & alu_src2,
                    Mux(io.alu_op === ALUOp.OR,  alu_src1 | alu_src2,
                                                 alu_src1 ^ alu_src2))

  val shamt = alu_src2(4, 0)
  val shift_out = Mux(io.alu_op === ALUOp.SLL, alu_src1 << shamt,
                  Mux(io.alu_op === ALUOp.SRL, alu_src1 >> shamt,
                                               (alu_src1.asSInt >> shamt).asUInt))

  val copy_out = Mux(io.alu_op === ALUOp.COPY_A, alu_src1, alu_src2)

  val is_arith   = io.alu_op === ALUOp.ADD || io.alu_op === ALUOp.SUB || io.alu_op === ALUOp.SLT || io.alu_op === ALUOp.SLTU
  val is_logical = io.alu_op === ALUOp.AND || io.alu_op === ALUOp.OR  || io.alu_op === ALUOp.XOR
  val is_shift   = io.alu_op === ALUOp.SLL || io.alu_op === ALUOp.SRL  || io.alu_op === ALUOp.SRA

  val non_arith_out = Mux(is_logical, logical_out, Mux(is_shift, shift_out, copy_out))

  val base_alu_result = Mux(is_arith, arith_out, non_arith_out)
  
  // B Extension Logic
  val b_alu_result = WireDefault(0.U(32.W))
  if (config.enableBExtension) {
    val andn_out = alu_src1 & ~alu_src2
    val orn_out = alu_src1 | ~alu_src2
    val xnor_out = ~(alu_src1 ^ alu_src2)
    val max_out = Mux(slt_val, alu_src2, alu_src1)
    val maxu_out = Mux(sltu_val, alu_src2, alu_src1)
    val min_out = Mux(slt_val, alu_src1, alu_src2)
    val minu_out = Mux(sltu_val, alu_src1, alu_src2)
    
    // Count Leading Zeros
    val clz_out = PriorityEncoder(Reverse(alu_src1))
    val clz_res = Mux(alu_src1 === 0.U, 32.U, clz_out)
    
    // Count Trailing Zeros
    val ctz_out = PriorityEncoder(alu_src1)
    val ctz_res = Mux(alu_src1 === 0.U, 32.U, ctz_out)
    
    // Population Count
    val cpop_res = PopCount(alu_src1)

    b_alu_result := MuxLookup(io.alu_op.asUInt, 0.U)(Seq(
      ALUOp.ANDN.asUInt -> andn_out,
      ALUOp.ORN.asUInt -> orn_out,
      ALUOp.XNOR.asUInt -> xnor_out,
      ALUOp.MAX.asUInt -> max_out,
      ALUOp.MAXU.asUInt -> maxu_out,
      ALUOp.MIN.asUInt -> min_out,
      ALUOp.MINU.asUInt -> minu_out,
      ALUOp.CLZ.asUInt -> clz_res,
      ALUOp.CTZ.asUInt -> ctz_res,
      ALUOp.CPOP.asUInt -> cpop_res
    ))
  }
  
  // M Extension Logic (Stalling State Machine)
  val m_alu_result = WireDefault(0.U(32.W))
  val m_stall_req = WireDefault(false.B)
  
  if (config.enableMExtension) {
    val is_mul = io.alu_op === ALUOp.MUL || io.alu_op === ALUOp.MULH || io.alu_op === ALUOp.MULHSU || io.alu_op === ALUOp.MULHU
    val is_div = io.alu_op === ALUOp.DIV || io.alu_op === ALUOp.DIVU || io.alu_op === ALUOp.REM || io.alu_op === ALUOp.REMU
    val is_m_op = is_mul || is_div
    
    val multiplier = Module(new SequentialMultiplier)
    multiplier.io.valid_in := io.valid_in && is_mul && !io.flush
    multiplier.io.op1 := alu_src1
    multiplier.io.op2 := alu_src2
    multiplier.io.alu_op := io.alu_op
    
    val divider = Module(new SequentialDivider)
    divider.io.valid_in := io.valid_in && is_div && !io.flush
    divider.io.op1 := alu_src1
    divider.io.op2 := alu_src2
    divider.io.alu_op := io.alu_op
    
    val m_ready = Mux(is_mul, multiplier.io.ready, divider.io.ready)
    
    m_stall_req := io.valid_in && is_m_op && !m_ready
    m_alu_result := Mux(is_mul, multiplier.io.result, divider.io.result)
  }
  
  io.ex_stall := m_stall_req
  
  // Multiplex extensions
  val is_b_op = io.alu_op === ALUOp.ANDN || io.alu_op === ALUOp.ORN || io.alu_op === ALUOp.XNOR || io.alu_op === ALUOp.CLZ ||
                io.alu_op === ALUOp.CTZ || io.alu_op === ALUOp.CPOP || io.alu_op === ALUOp.MAX || io.alu_op === ALUOp.MAXU ||
                io.alu_op === ALUOp.MIN || io.alu_op === ALUOp.MINU
                
  val is_m_op = io.alu_op === ALUOp.MUL || io.alu_op === ALUOp.MULH || io.alu_op === ALUOp.MULHSU || io.alu_op === ALUOp.MULHU ||
                io.alu_op === ALUOp.DIV || io.alu_op === ALUOp.DIVU || io.alu_op === ALUOp.REM || io.alu_op === ALUOp.REMU
                
  val alu_result = Mux(is_b_op, b_alu_result, Mux(is_m_op, m_alu_result, base_alu_result))
  
  // Branch condition evaluation
  val branch_taken = WireDefault(false.B)
  when(io.branch && io.valid_in) {
    val eq = alu_src1 === alu_src2
    val lt = slt_val
    val ltu = sltu_val
    
    val is_sub_op = io.alu_op === ALUOp.SUB
    val is_slt_op = io.alu_op === ALUOp.SLT
    
    val slt_zero = !lt && (adder_out(31, 1) === 0.U)
    val sltu_zero = !ltu && (adder_out(31, 1) === 0.U)
    
    branch_taken := Mux(is_sub_op, eq, Mux(is_slt_op, slt_zero, sltu_zero))
  }
  
  // Jump/Branch target
  val target_pc = Mux(io.jump && io.alu_src1_sel, 
    base_alu_result & ~1.U,  // JALR: mask LSB (use base alu to prevent critical path via extensions)
    io.pc_in + io.imm   // JAL/Branch: PC + immediate
  )
  
  val pc_plus_4 = io.pc_in + 4.U
  
  // Pipeline registers
  val alu_result_reg = RegInit(0.U(32.W))
  val mem_write_data_reg = RegInit(0.U(32.W))
  val rd_addr_reg = RegInit(0.U(5.W))
  val mem_read_reg = RegInit(false.B)
  val mem_write_reg = RegInit(false.B)
  val mem_size_reg = RegInit(0.U(2.W))
  val mem_unsigned_reg = RegInit(false.B)
  val reg_write_reg = RegInit(false.B)
  val wb_sel_reg = RegInit(0.U(2.W))
  val pc_plus_4_reg = RegInit(0.U(32.W))
  val valid_reg = RegInit(false.B)
  
  // CSR registers
  val csr_cmd_reg = RegInit(0.U(3.W))
  val csr_addr_reg = RegInit(0.U(12.W))
  val mret_reg = RegInit(false.B)
  
  when(io.flush || m_stall_req) {
    valid_reg := false.B
    reg_write_reg := false.B
    mem_read_reg := false.B
    mem_write_reg := false.B
    csr_cmd_reg := 0.U
    csr_addr_reg := 0.U
    mret_reg := false.B
  }.elsewhen(!io.stall) {
    alu_result_reg := alu_result
    mem_write_data_reg := resolved_rs2_data
    rd_addr_reg := io.rd_addr_in
    mem_read_reg := io.mem_read_in
    mem_write_reg := io.mem_write_in
    mem_size_reg := io.mem_size_in
    mem_unsigned_reg := io.mem_unsigned_in
    reg_write_reg := io.reg_write_in
    wb_sel_reg := io.wb_sel_in
    pc_plus_4_reg := pc_plus_4
    valid_reg := io.valid_in
    csr_cmd_reg := io.csr_cmd_in
    csr_addr_reg := io.csr_addr_in
    mret_reg := io.mret_in
  }
  
  // Outputs
  io.branch_taken := branch_taken
  io.jump_taken := io.jump && io.valid_in
  io.target_pc := target_pc
  io.alu_result := alu_result_reg
  io.mem_write_data := mem_write_data_reg
  io.rd_addr_out := rd_addr_reg
  io.mem_read_out := mem_read_reg
  io.mem_write_out := mem_write_reg
  io.mem_size_out := mem_size_reg
  io.mem_unsigned_out := mem_unsigned_reg
  io.reg_write_out := reg_write_reg
  io.wb_sel_out := wb_sel_reg
  io.pc_plus_4 := pc_plus_4_reg
  io.valid_out := valid_reg
  io.csr_cmd_out := csr_cmd_reg
  io.csr_addr_out := csr_addr_reg
  io.mret_out := mret_reg
}
