package palmsoc.core


import chisel3._ 
import chisel3.util._ 
/**
 * Instruction Decode Stage
 * 
 * Decodes instructions, reads register file, and generates control signals.
 */
class DecodeStage(val config: palmsoc.config.SoCConfig = palmsoc.config.DefaultSoCConfig()) extends Module {
  val io = IO(new Bundle {
    // Input from fetch
    val instruction = Input(UInt(32.W))
    val pc_in = Input(UInt(32.W))
    val valid_in = Input(Bool())
    
    // Register file read ports
    val rs1_addr = Output(UInt(5.W))
    val rs2_addr = Output(UInt(5.W))
    val rs1_data = Input(UInt(32.W))
    val rs2_data = Input(UInt(32.W))
    
    // Outputs to execute stage
    val pc_out = Output(UInt(32.W))
    val rs1_data_out = Output(UInt(32.W))
    val rs2_data_out = Output(UInt(32.W))
    val rs1_addr_out = Output(UInt(5.W))
    val rs2_addr_out = Output(UInt(5.W))
    val imm = Output(UInt(32.W))
    val rd_addr = Output(UInt(5.W))
    val alu_op = Output(ALUOp())
    val alu_src1_sel = Output(Bool())  // 0: rs1, 1: pc
    val alu_src2_sel = Output(Bool())  // 0: rs2, 1: imm
    val branch = Output(Bool())
    val jump = Output(Bool())
    val mem_read = Output(Bool())
    val mem_write = Output(Bool())
    val mem_size = Output(UInt(2.W))  // 00: byte, 01: half, 10: word
    val mem_unsigned = Output(Bool())
    val reg_write = Output(Bool())
    val wb_sel = Output(UInt(2.W))  // 0: alu, 1: mem, 2: pc+4, 3: csr
    val valid_out = Output(Bool())
    
    // CSR outputs
    val csr_cmd = Output(UInt(3.W))
    val csr_addr = Output(UInt(12.W))
    val mret = Output(Bool())
    
    // Forwarding/hazard detection
    val rs1_used = Output(Bool())
    val rs2_used = Output(Bool())
    
    // Control signals
    val stall = Input(Bool())
    val flush = Input(Bool())
  })
  
  val inst = io.instruction
  
  // Instruction fields
  val opcode = inst(6, 0)
  val rd = inst(11, 7)
  val funct3 = inst(14, 12)
  val rs1 = inst(19, 15)
  val rs2 = inst(24, 20)
  val funct7 = inst(31, 25)
  
  // Immediate generation
  val imm_i = Cat(Fill(20, inst(31)), inst(31, 20))
  val imm_s = Cat(Fill(20, inst(31)), inst(31, 25), inst(11, 7))
  val imm_b = Cat(Fill(19, inst(31)), inst(31), inst(7), inst(30, 25), inst(11, 8), 0.U(1.W))
  val imm_u = Cat(inst(31, 12), 0.U(12.W))
  val imm_j = Cat(Fill(11, inst(31)), inst(31), inst(19, 12), inst(20), inst(30, 21), 0.U(1.W))
  
  // Control signal defaults
  val alu_op = WireDefault(ALUOp.ADD)
  val alu_src1_sel = WireDefault(false.B)
  val alu_src2_sel = WireDefault(false.B)
  val branch = WireDefault(false.B)
  val jump = WireDefault(false.B)
  val mem_read = WireDefault(false.B)
  val mem_write = WireDefault(false.B)
  val mem_size = WireDefault(2.U(2.W))  // word
  val mem_unsigned = WireDefault(false.B)
  val reg_write = WireDefault(false.B)
  val wb_sel = WireDefault(0.U(2.W))
  val imm_out = WireDefault(imm_i)
  val rs1_used = WireDefault(true.B)
  val rs2_used = WireDefault(true.B)
  
  val csr_cmd = WireDefault(0.U(3.W))
  val csr_addr = WireDefault(0.U(12.W))
  val mret = WireDefault(false.B)
  
  // Decode logic
  switch(opcode) {
    is(RV32Instructions.OPCODE_OP_IMM) {
      reg_write := true.B
      alu_src2_sel := true.B  // use immediate
      rs2_used := false.B
      imm_out := imm_i
      
      switch(funct3) {
        is(RV32Instructions.FUNCT3_ADD_SUB) { alu_op := ALUOp.ADD }
        is(RV32Instructions.FUNCT3_SLT) { alu_op := ALUOp.SLT }
        is(RV32Instructions.FUNCT3_SLTU) { alu_op := ALUOp.SLTU }
        is(RV32Instructions.FUNCT3_XOR) { alu_op := ALUOp.XOR }
        is(RV32Instructions.FUNCT3_OR) { alu_op := ALUOp.OR }
        is(RV32Instructions.FUNCT3_AND) { alu_op := ALUOp.AND }
        is(RV32Instructions.FUNCT3_SLL) { 
          when(config.enableBExtension.B && funct7 === 0x30.U) {
            switch(rs2) {
              is(0.U) { alu_op := ALUOp.CLZ }
              is(1.U) { alu_op := ALUOp.CTZ }
              is(2.U) { alu_op := ALUOp.CPOP }
            }
          }.otherwise {
            alu_op := ALUOp.SLL 
          }
        }
        is(RV32Instructions.FUNCT3_SRL_SRA) {
          alu_op := Mux(inst(30), ALUOp.SRA, ALUOp.SRL)
        }
      }
    }
    
    is(RV32Instructions.OPCODE_OP) {
      reg_write := true.B
      alu_src2_sel := false.B  // use rs2
      
      when(config.enableMExtension.B && funct7 === 1.U) {
        // M-Extension
        switch(funct3) {
          is(0.U) { alu_op := ALUOp.MUL }
          is(1.U) { alu_op := ALUOp.MULH }
          is(2.U) { alu_op := ALUOp.MULHSU }
          is(3.U) { alu_op := ALUOp.MULHU }
          is(4.U) { alu_op := ALUOp.DIV }
          is(5.U) { alu_op := ALUOp.DIVU }
          is(6.U) { alu_op := ALUOp.REM }
          is(7.U) { alu_op := ALUOp.REMU }
        }
      }.elsewhen(config.enableBExtension.B && funct7 === 0x20.U) {
        // Zbb logical with negate & base SUB/SRA
        switch(funct3) {
          is(RV32Instructions.FUNCT3_AND) { alu_op := ALUOp.ANDN }
          is(RV32Instructions.FUNCT3_OR) { alu_op := ALUOp.ORN }
          is(RV32Instructions.FUNCT3_XOR) { alu_op := ALUOp.XNOR }
          is(RV32Instructions.FUNCT3_ADD_SUB) { alu_op := ALUOp.SUB }
          is(RV32Instructions.FUNCT3_SRL_SRA) { alu_op := ALUOp.SRA }
        }
      }.elsewhen(config.enableBExtension.B && funct7 === 0x05.U) {
        // Zbb min/max
        switch(funct3) {
          is(4.U) { alu_op := ALUOp.MIN }
          is(5.U) { alu_op := ALUOp.MAX }
          is(6.U) { alu_op := ALUOp.MINU }
          is(7.U) { alu_op := ALUOp.MAXU }
        }
      }.otherwise {
        // Base RV32I
        val is_sub = funct7(5)
        switch(funct3) {
          is(RV32Instructions.FUNCT3_ADD_SUB) {
            alu_op := Mux(is_sub, ALUOp.SUB, ALUOp.ADD)
          }
          is(RV32Instructions.FUNCT3_SLT) { alu_op := ALUOp.SLT }
          is(RV32Instructions.FUNCT3_SLTU) { alu_op := ALUOp.SLTU }
          is(RV32Instructions.FUNCT3_XOR) { alu_op := ALUOp.XOR }
          is(RV32Instructions.FUNCT3_OR) { alu_op := ALUOp.OR }
          is(RV32Instructions.FUNCT3_AND) { alu_op := ALUOp.AND }
          is(RV32Instructions.FUNCT3_SLL) { alu_op := ALUOp.SLL }
          is(RV32Instructions.FUNCT3_SRL_SRA) {
            alu_op := Mux(funct7(5), ALUOp.SRA, ALUOp.SRL)
          }
        }
      }
    }
    
    is(RV32Instructions.OPCODE_LUI) {
      reg_write := true.B
      alu_op := ALUOp.COPY_B
      alu_src2_sel := true.B
      imm_out := imm_u
      rs1_used := false.B
      rs2_used := false.B
    }
    
    is(RV32Instructions.OPCODE_AUIPC) {
      reg_write := true.B
      alu_op := ALUOp.ADD
      alu_src1_sel := true.B  // use PC
      alu_src2_sel := true.B  // use immediate
      imm_out := imm_u
      rs1_used := false.B
      rs2_used := false.B
    }
    
    is(RV32Instructions.OPCODE_JAL) {
      reg_write := true.B
      jump := true.B
      wb_sel := 2.U  // write PC+4
      imm_out := imm_j
      rs1_used := false.B
      rs2_used := false.B
    }
    
    is(RV32Instructions.OPCODE_JALR) {
      reg_write := true.B
      jump := true.B
      wb_sel := 2.U  // write PC+4
      alu_src2_sel := true.B
      imm_out := imm_i
      rs2_used := false.B
    }
    
    is(RV32Instructions.OPCODE_BRANCH) {
      branch := true.B
      imm_out := imm_b
      reg_write := false.B
      
      switch(funct3) {
        is(RV32Instructions.FUNCT3_BEQ) { alu_op := ALUOp.SUB }
        is(RV32Instructions.FUNCT3_BNE) { alu_op := ALUOp.SUB }
        is(RV32Instructions.FUNCT3_BLT) { alu_op := ALUOp.SLT }
        is(RV32Instructions.FUNCT3_BGE) { alu_op := ALUOp.SLT }
        is(RV32Instructions.FUNCT3_BLTU) { alu_op := ALUOp.SLTU }
        is(RV32Instructions.FUNCT3_BGEU) { alu_op := ALUOp.SLTU }
      }
    }
    
    is(RV32Instructions.OPCODE_LOAD) {
      reg_write := true.B
      mem_read := true.B
      wb_sel := 1.U  // write from memory
      alu_op := ALUOp.ADD
      alu_src2_sel := true.B
      imm_out := imm_i
      rs2_used := false.B
      
      mem_size := funct3(1, 0)
      mem_unsigned := funct3(2)
    }
    
    is(RV32Instructions.OPCODE_STORE) {
      mem_write := true.B
      alu_op := ALUOp.ADD
      alu_src2_sel := true.B
      imm_out := imm_s
      reg_write := false.B
      
      mem_size := funct3(1, 0)
    }
    
    is(RV32Instructions.OPCODE_SYSTEM) {
      reg_write := (funct3 =/= 0.U)
      wb_sel := 3.U // select CSR read data
      rs2_used := false.B
      
      val is_imm_cmd = funct3(2) // CSRRWI/RSI/RCI
      val zimm = Cat(0.U(27.W), inst(19, 15))
      imm_out := Mux(is_imm_cmd, zimm, imm_i)
      
      alu_op := Mux(is_imm_cmd, ALUOp.COPY_B, ALUOp.COPY_A)
      
      rs1_used := !is_imm_cmd && (funct3 =/= 0.U)
      
      csr_cmd := funct3
      csr_addr := inst(31, 20)
      mret := (funct3 === 0.U) && (inst(31, 20) === 0x302.U)
    }
  }
  
  // Pipeline registers
  val pc_reg = RegInit(0.U(32.W))
  val rs1_data_reg = RegInit(0.U(32.W))
  val rs2_data_reg = RegInit(0.U(32.W))
  val rs1_addr_reg = RegInit(0.U(5.W))
  val rs2_addr_reg = RegInit(0.U(5.W))
  val imm_reg = RegInit(0.U(32.W))
  val rd_addr_reg = RegInit(0.U(5.W))
  val alu_op_reg = RegInit(ALUOp.ADD)
  val alu_src1_sel_reg = RegInit(false.B)
  val alu_src2_sel_reg = RegInit(false.B)
  val branch_reg = RegInit(false.B)
  val jump_reg = RegInit(false.B)
  val mem_read_reg = RegInit(false.B)
  val mem_write_reg = RegInit(false.B)
  val mem_size_reg = RegInit(0.U(2.W))
  val mem_unsigned_reg = RegInit(false.B)
  val reg_write_reg = RegInit(false.B)
  val wb_sel_reg = RegInit(0.U(2.W))
  val valid_reg = RegInit(false.B)
  
  // CSR pipeline registers
  val csr_cmd_reg = RegInit(0.U(3.W))
  val csr_addr_reg = RegInit(0.U(12.W))
  val mret_reg = RegInit(false.B)
  
  when(io.flush) {
    valid_reg := false.B
    reg_write_reg := false.B
    mem_read_reg := false.B
    mem_write_reg := false.B
    branch_reg := false.B
    jump_reg := false.B
    csr_cmd_reg := 0.U
    csr_addr_reg := 0.U
    mret_reg := false.B
  }.elsewhen(!io.stall) {
    pc_reg := io.pc_in
    rs1_data_reg := io.rs1_data
    rs2_data_reg := io.rs2_data
    rs1_addr_reg := rs1
    rs2_addr_reg := rs2
    imm_reg := imm_out
    rd_addr_reg := rd
    alu_op_reg := alu_op
    alu_src1_sel_reg := alu_src1_sel
    alu_src2_sel_reg := alu_src2_sel
    branch_reg := branch
    jump_reg := jump
    mem_read_reg := mem_read
    mem_write_reg := mem_write
    mem_size_reg := mem_size
    mem_unsigned_reg := mem_unsigned
    reg_write_reg := reg_write
    wb_sel_reg := wb_sel
    valid_reg := io.valid_in
    csr_cmd_reg := csr_cmd
    csr_addr_reg := csr_addr
    mret_reg := mret
  }
  
  // Outputs
  io.rs1_addr := rs1
  io.rs2_addr := rs2
  io.pc_out := pc_reg
  io.rs1_data_out := rs1_data_reg
  io.rs2_data_out := rs2_data_reg
  io.rs1_addr_out := rs1_addr_reg
  io.rs2_addr_out := rs2_addr_reg
  io.imm := imm_reg
  io.rd_addr := rd_addr_reg
  io.alu_op := alu_op_reg
  io.alu_src1_sel := alu_src1_sel_reg
  io.alu_src2_sel := alu_src2_sel_reg
  io.branch := branch_reg
  io.jump := jump_reg
  io.mem_read := mem_read_reg
  io.mem_write := mem_write_reg
  io.mem_size := mem_size_reg
  io.mem_unsigned := mem_unsigned_reg
  io.reg_write := reg_write_reg
  io.wb_sel := wb_sel_reg
  io.valid_out := valid_reg
  io.rs1_used := rs1_used
  io.rs2_used := rs2_used
  io.csr_cmd := csr_cmd_reg
  io.csr_addr := csr_addr_reg
  io.mret := mret_reg
}


