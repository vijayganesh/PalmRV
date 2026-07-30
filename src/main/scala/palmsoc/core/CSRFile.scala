package palmsoc.core

import chisel3._
import chisel3.util._

/**
 * RISC-V Control and Status Register (CSR) Addresses
 * 
 * Implements machine-mode CSRs for RV32I privilege architecture
 */
object CSRAddress {
  // Machine Information Registers (read-only)
  val MVENDORID = 0xF11.U(12.W)  // Vendor ID
  val MARCHID   = 0xF12.U(12.W)  // Architecture ID
  val MIMPID    = 0xF13.U(12.W)  // Implementation ID
  val MHARTID   = 0xF14.U(12.W)  // Hardware thread ID
  
  // Machine Trap Setup
  val MSTATUS   = 0x300.U(12.W)  // Machine status register
  val MISA      = 0x301.U(12.W)  // ISA and extensions
  val MEDELEG   = 0x302.U(12.W)  // Machine exception delegation
  val MIDELEG   = 0x303.U(12.W)  // Machine interrupt delegation
  val MIE       = 0x304.U(12.W)  // Machine interrupt-enable
  val MTVEC     = 0x305.U(12.W)  // Machine trap-handler base address
  val MCOUNTEREN= 0x306.U(12.W)  // Machine counter enable
  
  // Machine Trap Handling
  val MSCRATCH  = 0x340.U(12.W)  // Machine scratch register
  val MEPC      = 0x341.U(12.W)  // Machine exception program counter
  val MCAUSE    = 0x342.U(12.W)  // Machine trap cause
  val MTVAL     = 0x343.U(12.W)  // Machine bad address or instruction
  val MIP       = 0x344.U(12.W)  // Machine interrupt pending
  
  // Machine Memory Protection (optional)
  val PMPCFG0   = 0x3A0.U(12.W)  // PMP configuration register 0
  val PMPADDR0  = 0x3B0.U(12.W)  // PMP address register 0
  
  // Machine Counter/Timers
  val MCYCLE    = 0xB00.U(12.W)  // Machine cycle counter
  val MINSTRET  = 0xB02.U(12.W)  // Machine instructions-retired counter
  val MCYCLEH   = 0xB80.U(12.W)  // Upper 32 bits of mcycle (RV32 only)
  val MINSTRETH = 0xB82.U(12.W)  // Upper 32 bits of minstret (RV32 only)
  
  // User Counter/Timers (read-only shadows)
  val CYCLE     = 0xC00.U(12.W)  // Cycle counter
  val TIME      = 0xC01.U(12.W)  // Timer
  val INSTRET   = 0xC02.U(12.W)  // Instructions-retired counter
  val CYCLEH    = 0xC80.U(12.W)  // Upper 32 bits of cycle
  val TIMEH     = 0xC81.U(12.W)  // Upper 32 bits of time
  val INSTRETH  = 0xC82.U(12.W)  // Upper 32 bits of instret
}

/**
 * Exception and Interrupt Causes
 */
object TrapCause {
  // Interrupts (bit 31 = 1)
  val MACHINE_SOFTWARE_INT    = (1L << 31 | 3).U(32.W)
  val MACHINE_TIMER_INT       = (1L << 31 | 7).U(32.W)
  val MACHINE_EXTERNAL_INT    = (1L << 31 | 11).U(32.W)
  
  // Exceptions (bit 31 = 0)
  val INSTRUCTION_ADDR_MISALIGNED = 0.U(32.W)
  val INSTRUCTION_ACCESS_FAULT    = 1.U(32.W)
  val ILLEGAL_INSTRUCTION         = 2.U(32.W)
  val BREAKPOINT                  = 3.U(32.W)
  val LOAD_ADDR_MISALIGNED        = 4.U(32.W)
  val LOAD_ACCESS_FAULT           = 5.U(32.W)
  val STORE_ADDR_MISALIGNED       = 6.U(32.W)
  val STORE_ACCESS_FAULT          = 7.U(32.W)
  val ECALL_FROM_U                = 8.U(32.W)
  val ECALL_FROM_S                = 9.U(32.W)
  val ECALL_FROM_M                = 11.U(32.W)
  val INSTRUCTION_PAGE_FAULT      = 12.U(32.W)
  val LOAD_PAGE_FAULT             = 13.U(32.W)
  val STORE_PAGE_FAULT            = 15.U(32.W)
}

/**
 * MSTATUS bit fields
 */
object MStatus {
  val UIE  = 0   // User Interrupt Enable
  val SIE  = 1   // Supervisor Interrupt Enable
  val MIE  = 3   // Machine Interrupt Enable
  val UPIE = 4   // User Previous Interrupt Enable
  val SPIE = 5   // Supervisor Previous Interrupt Enable
  val MPIE = 7   // Machine Previous Interrupt Enable
  val SPP  = 8   // Supervisor Previous Privilege
  val MPP  = 11  // Machine Previous Privilege (2 bits: 11-12)
  val FS   = 13  // Floating-point Status (2 bits: 13-14)
  val XS   = 15  // Extension Status (2 bits: 15-16)
  val MPRV = 17  // Modify PRiVilege
  val SUM  = 18  // permit Supervisor User Memory access
  val MXR  = 19  // Make eXecutable Readable
  val TVM  = 20  // Trap Virtual Memory
  val TW   = 21  // Timeout Wait
  val TSR  = 22  // Trap SRET
  val SD   = 31  // State Dirty
}

/**
 * Control and Status Register File
 * 
 * Implements machine-mode CSRs for RV32I
 * Supports read/write operations and trap handling
 */
class CSRFile extends Module {
  val io = IO(new Bundle {
    // CSR Read/Write Interface
    val csr_addr = Input(UInt(12.W))
    val csr_cmd = Input(UInt(3.W))  // 0: None, 1: RW, 2: RS, 3: RC, 5: RWI, 6: RSI, 7: RCI
    val csr_wdata = Input(UInt(32.W))
    val csr_rdata = Output(UInt(32.W))
    val csr_valid = Output(Bool())
    
    // Exception/Interrupt Interface
    val exception = Input(Bool())
    val exception_cause = Input(UInt(32.W))
    val exception_pc = Input(UInt(32.W))
    val exception_tval = Input(UInt(32.W))
    
    val interrupt = Input(Bool())
    val interrupt_cause = Input(UInt(32.W))
    
    // Trap Return
    val mret = Input(Bool())
    val trap_vector = Output(UInt(32.W))
    val epc = Output(UInt(32.W))
    val taking_trap = Output(Bool())
    
    // External Interrupts
    val external_interrupt = Input(Bool())
    val timer_interrupt = Input(Bool())
    val software_interrupt = Input(Bool())
    
    // Privilege Mode
    val priv_mode = Output(UInt(2.W))  // 0: U, 1: S, 3: M
    
    // Performance Counters
    val instret = Input(Bool())  // Instruction retired
  })
  
  // Machine mode = 3
  val PRIV_M = 3.U(2.W)
  val priv_mode_reg = RegInit(PRIV_M)
  
  // Machine Information Registers (read-only)
  val mvendorid = 0.U(32.W)  // Non-commercial implementation
  val marchid = 0.U(32.W)    // Not assigned
  val mimpid = 0x00010000.U(32.W)  // Version 1.0
  val mhartid = 0.U(32.W)    // Single hart
  
  // Machine ISA Register
  val misa = Cat(
    1.U(2.W),        // MXL = 1 (32-bit)
    0.U(4.W),        // Reserved
    0.U(26.W)        // Extensions (set I bit for RV32I)
  ) | (1.U << 8)     // Bit 8 = I (base integer ISA)
  
  // Machine Status Register
  val mstatus = RegInit(0.U(32.W))
  val mie_bit = mstatus(MStatus.MIE)
  val mpie_bit = mstatus(MStatus.MPIE)
  val mpp_bits = mstatus(MStatus.MPP + 1, MStatus.MPP)
  
  // Machine Trap Vector
  val mtvec = RegInit(0.U(32.W))  // Trap vector base address
  
  // Machine Interrupt Enable/Pending
  val mie = RegInit(0.U(32.W))
  val mip_reg = RegInit(0.U(32.W))
  
  // Update MIP based on external signals
  val mip = Cat(
    0.U(20.W),
    io.external_interrupt,  // MEIP (bit 11)
    0.U(3.W),
    io.timer_interrupt,     // MTIP (bit 7)
    0.U(3.W),
    io.software_interrupt,  // MSIP (bit 3)
    0.U(3.W)
  ) | mip_reg
  
  // Machine Trap Handling
  val mscratch = RegInit(0.U(32.W))
  val mepc = RegInit(0.U(32.W))
  val mcause = RegInit(0.U(32.W))
  val mtval = RegInit(0.U(32.W))
  
  // Machine Counters
  val mcycle = RegInit(0.U(64.W))
  val minstret = RegInit(0.U(64.W))
  
  // Update counters
  mcycle := mcycle + 1.U
  when(io.instret) {
    minstret := minstret + 1.U
  }
  
  // CSR Read
  val csr_rdata = WireDefault(0.U(32.W))
  val csr_valid = WireDefault(true.B)
  
  switch(io.csr_addr) {
    // Machine Information
    is(CSRAddress.MVENDORID) { csr_rdata := mvendorid }
    is(CSRAddress.MARCHID) { csr_rdata := marchid }
    is(CSRAddress.MIMPID) { csr_rdata := mimpid }
    is(CSRAddress.MHARTID) { csr_rdata := mhartid }
    
    // Machine Trap Setup
    is(CSRAddress.MSTATUS) { csr_rdata := mstatus }
    is(CSRAddress.MISA) { csr_rdata := misa }
    is(CSRAddress.MIE) { csr_rdata := mie }
    is(CSRAddress.MTVEC) { csr_rdata := mtvec }
    
    // Machine Trap Handling
    is(CSRAddress.MSCRATCH) { csr_rdata := mscratch }
    is(CSRAddress.MEPC) { csr_rdata := mepc }
    is(CSRAddress.MCAUSE) { csr_rdata := mcause }
    is(CSRAddress.MTVAL) { csr_rdata := mtval }
    is(CSRAddress.MIP) { csr_rdata := mip }
    
    // Machine Counters
    is(CSRAddress.MCYCLE) { csr_rdata := mcycle(31, 0) }
    is(CSRAddress.MCYCLEH) { csr_rdata := mcycle(63, 32) }
    is(CSRAddress.MINSTRET) { csr_rdata := minstret(31, 0) }
    is(CSRAddress.MINSTRETH) { csr_rdata := minstret(63, 32) }
    
    // User-level shadows
    is(CSRAddress.CYCLE) { csr_rdata := mcycle(31, 0) }
    is(CSRAddress.CYCLEH) { csr_rdata := mcycle(63, 32) }
    is(CSRAddress.INSTRET) { csr_rdata := minstret(31, 0) }
    is(CSRAddress.INSTRETH) { csr_rdata := minstret(63, 32) }
    is(CSRAddress.TIME) { csr_rdata := mcycle(31, 0) }  // TIME = CYCLE for now
    is(CSRAddress.TIMEH) { csr_rdata := mcycle(63, 32) }
  }
  
  // CSR Write
  val csr_wdata_final = WireDefault(io.csr_wdata)
  val do_write = io.csr_cmd(1, 0).orR  // Any write command
  
  // Calculate write data based on command
  switch(io.csr_cmd(1, 0)) {
    is(1.U) { csr_wdata_final := io.csr_wdata }  // CSRRW/CSRRWI
    is(2.U) { csr_wdata_final := csr_rdata | io.csr_wdata }  // CSRRS/CSRRSI
    is(3.U) { csr_wdata_final := csr_rdata & ~io.csr_wdata }  // CSRRC/CSRRCI
  }
  
  when(do_write && !io.exception && !io.interrupt) {
    switch(io.csr_addr) {
      // Machine Trap Setup
      is(CSRAddress.MSTATUS) {
        // Only write to implemented fields
        val new_mstatus = Cat(
          mstatus(31, 13),
          csr_wdata_final(12, 11),  // MPP
          mstatus(10, 8),
          csr_wdata_final(7),       // MPIE
          mstatus(6, 4),
          csr_wdata_final(3),       // MIE
          mstatus(2, 0)
        )
        mstatus := new_mstatus
      }
      is(CSRAddress.MIE) { mie := csr_wdata_final }
      is(CSRAddress.MTVEC) { mtvec := Cat(csr_wdata_final(31, 2), 0.U(2.W)) }  // Align to 4 bytes
      
      // Machine Trap Handling
      is(CSRAddress.MSCRATCH) { mscratch := csr_wdata_final }
      is(CSRAddress.MEPC) { mepc := Cat(csr_wdata_final(31, 2), 0.U(2.W)) }  // Align to 4 bytes
      is(CSRAddress.MCAUSE) { mcause := csr_wdata_final }
      is(CSRAddress.MTVAL) { mtval := csr_wdata_final }
      is(CSRAddress.MIP) { mip_reg := csr_wdata_final & 0x888.U }  // Only MEIP, MTIP, MSIP writable
      
      // Machine Counters (writable)
      is(CSRAddress.MCYCLE) { mcycle := Cat(mcycle(63, 32), csr_wdata_final) }
      is(CSRAddress.MCYCLEH) { mcycle := Cat(csr_wdata_final, mcycle(31, 0)) }
      is(CSRAddress.MINSTRET) { minstret := Cat(minstret(63, 32), csr_wdata_final) }
      is(CSRAddress.MINSTRETH) { minstret := Cat(csr_wdata_final, minstret(31, 0)) }
    }
  }
  
  // Exception/Interrupt Handling
  val meip_active = mip(11) && mie(11)
  val mtip_active = mip(7) && mie(7)
  val msip_active = mip(3) && mie(3)
  
  val any_interrupt_active = meip_active || mtip_active || msip_active
  val active_interrupt_cause = Mux(meip_active, TrapCause.MACHINE_EXTERNAL_INT,
                               Mux(mtip_active, TrapCause.MACHINE_TIMER_INT,
                                                TrapCause.MACHINE_SOFTWARE_INT))

  val int_active = io.interrupt || any_interrupt_active
  val int_cause = Mux(io.interrupt, io.interrupt_cause, active_interrupt_cause)

  val taking_trap = io.exception || (int_active && mie_bit)
  
  when(taking_trap) {
    // Save PC
    mepc := io.exception_pc
    
    // Save cause
    mcause := Mux(io.exception, io.exception_cause, int_cause)
    
    // Save trap value
    mtval := io.exception_tval
    
    // Update mstatus
    val new_mstatus = mstatus
    mstatus := Cat(
      new_mstatus(31, 13),
      priv_mode_reg,         // MPP = current privilege
      new_mstatus(10, 8),
      mie_bit,               // MPIE = MIE
      new_mstatus(6, 4),
      0.U(1.W),              // MIE = 0 (disable interrupts)
      new_mstatus(2, 0)
    )
    
    // Enter machine mode
    priv_mode_reg := PRIV_M
  }
  
  // Trap Return (MRET)
  when(io.mret) {
    // Restore privilege mode
    priv_mode_reg := mpp_bits
    
    // Update mstatus
    val new_mstatus = mstatus
    mstatus := Cat(
      new_mstatus(31, 13),
      3.U(2.W),              // MPP = M (reset to machine mode)
      new_mstatus(10, 8),
      1.U(1.W),              // MPIE = 1
      new_mstatus(6, 4),
      mpie_bit,              // MIE = MPIE
      new_mstatus(2, 0)
    )
  }
  
  // Outputs
  io.csr_rdata := csr_rdata
  io.csr_valid := csr_valid
  io.trap_vector := Cat(mtvec(31, 2), 0.U(2.W))
  io.epc := mepc
  io.priv_mode := priv_mode_reg
  io.taking_trap := taking_trap
}

/**
 * CSR Commands
 */
object CSRCmd {
  val NONE = 0.U(3.W)
  val RW   = 1.U(3.W)  // Read/Write
  val RS   = 2.U(3.W)  // Read and Set bits
  val RC   = 3.U(3.W)  // Read and Clear bits
  val RWI  = 5.U(3.W)  // Read/Write Immediate
  val RSI  = 6.U(3.W)  // Read and Set Immediate
  val RCI  = 7.U(3.W)  // Read and Clear Immediate
}