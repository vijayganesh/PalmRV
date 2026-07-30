#!/bin/bash
set -e

echo "======================================================================"
echo "Starting Synthesis and Report Collection for PalmSoC (Processor Only)"
echo "======================================================================"

# 1. Ensure reports directory exists
mkdir -p GeneratedSV/reports

# 2. Run Chisel generation
echo "Generating SystemVerilog files using sbt..."
sbt "runMain palmsoc.GenerateConfigurableSoCSV gpio=true uart=true i2c=true targetDir=GeneratedSV outputFile=ConfigurablePalmSoC.sv"

# 3. Verify files exist
if [ ! -f "GeneratedSV/ConfigurablePalmSoC.sv" ]; then
    echo "ERROR: Generated SV files not found in GeneratedSV/"
    exit 1
fi

# 4. Run Vivado synthesis and implementation
VIVADO_BIN="/chips/xilinx/Vivado/2023.2/bin/vivado"
if [ ! -f "$VIVADO_BIN" ]; then
    echo "ERROR: Vivado not found at $VIVADO_BIN"
    exit 1
fi

echo "Running Vivado in batch mode..."
$VIVADO_BIN -mode batch -source GeneratedSV/run_vivado.tcl \
    -log GeneratedSV/vivado.log \
    -journal GeneratedSV/vivado.jou

echo "Vivado runs completed. Parsing reports..."
echo ""

# 5. Run report parser
python3 GeneratedSV/parse_reports.py

echo "======================================================================"
echo "Done!"
echo "======================================================================"
