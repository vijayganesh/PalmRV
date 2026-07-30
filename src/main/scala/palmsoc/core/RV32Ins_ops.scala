package palmsoc.core
import chisel3._ 

/**
 * RISC-V RV32I Instruction Formats and Opcodes
 */
object RV32Instructions {
  // Opcodes (bits 6:0)
  val OPCODE_LOAD     = 0x03.U(7.W)
  val OPCODE_STORE    = 0x23.U(7.W)
  val OPCODE_BRANCH   = 0x63.U(7.W)
  val OPCODE_JALR     = 0x67.U(7.W)
  val OPCODE_JAL      = 0x6F.U(7.W)
  val OPCODE_OP_IMM   = 0x13.U(7.W)
  val OPCODE_OP       = 0x33.U(7.W)
  val OPCODE_AUIPC    = 0x17.U(7.W)
  val OPCODE_LUI      = 0x37.U(7.W)
  val OPCODE_SYSTEM   = 0x73.U(7.W)
  val OPCODE_FENCE    = 0x0F.U(7.W)
  
  // Funct3 codes
  val FUNCT3_ADD_SUB  = 0.U(3.W)
  val FUNCT3_SLL      = 1.U(3.W)
  val FUNCT3_SLT      = 2.U(3.W)
  val FUNCT3_SLTU     = 3.U(3.W)
  val FUNCT3_XOR      = 4.U(3.W)
  val FUNCT3_SRL_SRA  = 5.U(3.W)
  val FUNCT3_OR       = 6.U(3.W)
  val FUNCT3_AND      = 7.U(3.W)
  
  // Branch funct3
  val FUNCT3_BEQ      = 0.U(3.W)
  val FUNCT3_BNE      = 1.U(3.W)
  val FUNCT3_BLT      = 4.U(3.W)
  val FUNCT3_BGE      = 5.U(3.W)
  val FUNCT3_BLTU     = 6.U(3.W)
  val FUNCT3_BGEU     = 7.U(3.W)
  
  // Load/Store funct3
  val FUNCT3_LB       = 0.U(3.W)
  val FUNCT3_LH       = 1.U(3.W)
  val FUNCT3_LW       = 2.U(3.W)
  val FUNCT3_LBU      = 4.U(3.W)
  val FUNCT3_LHU      = 5.U(3.W)
  val FUNCT3_SB       = 0.U(3.W)
  val FUNCT3_SH       = 1.U(3.W)
  val FUNCT3_SW       = 2.U(3.W)
}

/**
 * ALU Operations
 */
object ALUOp extends ChiselEnum {
  val ADD, SUB, AND, OR, XOR, SLT, SLTU, SLL, SRL, SRA, COPY_A, COPY_B = Value
}

