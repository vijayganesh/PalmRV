import re

file_path = "src/main/scala/palmsoc/ConfigurablePalmSoC.scala"
with open(file_path, "r") as f:
    content = f.read()

# We want to replace the bridge logic from line 256 to 437
# I'll just use a regex to find the start and end and replace it.

start_marker = "val sBridgeIdle"
end_marker = "  }\n}"

match = re.search(r"(\s*val sBridgeIdle.*?)(  \}\n\})", content, re.DOTALL)
if match:
    old_bridge = match.group(1)
    
    new_bridge = """  val sBridgeIdle :: sBridgeImemRead :: sBridgeImemWait :: sBridgeDmemWriteAddr :: sBridgeDmemWriteData :: sBridgeDmemWriteResp :: sBridgeDmemReadAddr :: sBridgeDmemReadData :: Nil = Enum(8)
  val bridgeState = RegInit(sBridgeIdle)
  
  // AXI address and write registers
  val imem_addr_reg = RegInit(0.U(32.W))
  val dmem_addr_reg = RegInit(0.U(32.W))
  val dmem_wdata_reg = RegInit(0.U(32.W))
  val dmem_strb_reg = RegInit(0.U(4.W))
  
  // Default values
  cpuAxi.aw.awaddr := 0.U
  cpuAxi.aw.awprot := 0.U
  cpuAxi.aw.awvalid := false.B
  cpuAxi.w.wdata := 0.U
  cpuAxi.w.wstrb := 0.U
  cpuAxi.w.wvalid := false.B
  cpuAxi.b.bready := false.B
  cpuAxi.ar.araddr := 0.U
  cpuAxi.ar.arprot := 0.U
  cpuAxi.ar.arvalid := false.B
  cpuAxi.r.rready := false.B
  
  // Pipelined valid/data registers to break combinatorial control path
  val core_imem_valid_reg = RegInit(false.B)
  val core_imem_data_reg = Reg(UInt(32.W))
  val core_dmem_valid_reg = RegInit(false.B)
  val core_dmem_rdata_reg = Reg(UInt(32.W))
  
  core.io.imem_data := core_imem_data_reg
  core.io.imem_valid := core_imem_valid_reg
  core.io.dmem_rdata := core_dmem_rdata_reg
  core.io.dmem_valid := core_dmem_valid_reg
  
  val cpu_mem_active = core.io.dmem_write || core.io.dmem_read
  val cpu_will_advance = core_imem_valid_reg && (!cpu_mem_active || core_dmem_valid_reg)
  
  when(cpu_will_advance) {
    core_imem_valid_reg := false.B
    core_dmem_valid_reg := false.B
  }
  
  switch(bridgeState) {
    is(sBridgeIdle) {
      // Prioritize data memory accesses over instruction fetch
      when(cpu_mem_active && !core_dmem_valid_reg) {
        when(core.io.dmem_write) {
          dmem_addr_reg := core.io.dmem_addr
          dmem_wdata_reg := core.io.dmem_wdata
          
          val strb = WireDefault(0xF.U(4.W))
          switch(core.io.dmem_size) {
            is(0.U) { strb := (1.U << core.io.dmem_addr(1, 0)) }
            is(1.U) { strb := (3.U << (core.io.dmem_addr(1) << 1)) }
            is(2.U) { strb := 0xF.U }
          }
          dmem_strb_reg := strb
          bridgeState := sBridgeDmemWriteAddr
        }.otherwise { // core.io.dmem_read
          dmem_addr_reg := core.io.dmem_addr
          bridgeState := sBridgeDmemReadAddr
        }
      }.elsewhen(!core_imem_valid_reg) {
        imem_addr_reg := core.io.imem_addr
        bridgeState := sBridgeImemRead
      }
    }
    
    is(sBridgeImemRead) {
      cpuAxi.ar.araddr := imem_addr_reg
      cpuAxi.ar.arvalid := true.B
      
      when(cpuAxi.ar.arready) {
        bridgeState := sBridgeImemWait
      }
    }
    
    is(sBridgeImemWait) {
      cpuAxi.r.rready := true.B
      
      when(cpuAxi.r.rvalid) {
        val is_stale = imem_addr_reg =/= core.io.imem_addr
        
        when(is_stale) {
          // Address branched/flushed during this AXI read; discard stale instruction response and re-fetch from new PC
          bridgeState := sBridgeIdle
        }.otherwise {
          core_imem_data_reg := cpuAxi.r.rdata
          core_imem_valid_reg := true.B
          bridgeState := sBridgeIdle
        }
      }
    }
    
    is(sBridgeDmemWriteAddr) {
      cpuAxi.aw.awaddr := dmem_addr_reg
      cpuAxi.aw.awvalid := true.B
      
      when(cpuAxi.aw.awready) {
        bridgeState := sBridgeDmemWriteData
      }
    }
    
    is(sBridgeDmemWriteData) {
      cpuAxi.w.wdata := dmem_wdata_reg
      cpuAxi.w.wstrb := dmem_strb_reg
      cpuAxi.w.wvalid := true.B
      
      when(cpuAxi.w.wready) {
        bridgeState := sBridgeDmemWriteResp
      }
    }
    
    is(sBridgeDmemWriteResp) {
      cpuAxi.b.bready := true.B
      
      when(cpuAxi.b.bvalid) {
        core_dmem_valid_reg := true.B
        bridgeState := sBridgeIdle
      }
    }
    
    is(sBridgeDmemReadAddr) {
      cpuAxi.ar.araddr := dmem_addr_reg
      cpuAxi.ar.arvalid := true.B
      
      when(cpuAxi.ar.arready) {
        bridgeState := sBridgeDmemReadData
      }
    }
    
    is(sBridgeDmemReadData) {
      cpuAxi.r.rready := true.B
      
      when(cpuAxi.r.rvalid) {
        core_dmem_rdata_reg := cpuAxi.r.rdata
        core_dmem_valid_reg := true.B
        bridgeState := sBridgeIdle
      }
    }
"""
    new_content = content[:match.start()] + new_bridge + match.group(2)
    with open(file_path, "w") as f:
        f.write(new_content)
    print("Successfully patched ConfigurablePalmSoC.scala")
else:
    print("Match not found")

