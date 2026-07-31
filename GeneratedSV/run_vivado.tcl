# Vivado TCL batch run script for ConfigurablePalmSoC (without LSTM accelerator)
# Target: Artix-7 (xc7a100tcsg324-1)

set part_name "xc7a100tcsg324-1"
set output_dir "GeneratedSV/reports"
file mkdir $output_dir

# List of modules to process: {top_module_name source_verilog_file}
set modules {
  {"ConfigurablePalmSoC" "GeneratedSV/ConfigurablePalmSoC.sv"}
}

foreach mod_info $modules {
  set top_name [lindex $mod_info 0]
  set src_file [lindex $mod_info 1]
  
  puts "======================================================================"
  puts "Processing Top Module: $top_name"
  puts "======================================================================"
  
  # Create a clean in-memory project for the run
  create_project -in_memory -part $part_name -force
  
  # Read design sources
  read_verilog -sv $src_file
  
  # Read XDC constraints
  read_xdc GeneratedSV/constraints.xdc
  
  # Synthesize Design
  if {[catch {synth_design -top $top_name -part $part_name} err]} {
    puts "ERROR during synthesis of $top_name: $err"
    continue
  }
  
  # Run Design Optimization
  if {[catch {opt_design} err]} {
    puts "ERROR during opt_design of $top_name: $err"
    continue
  }
  
  # Run Placement
  if {[catch {place_design} err]} {
    puts "ERROR during place_design of $top_name: $err"
    continue
  }
  
  # Run Routing
  if {[catch {route_design} err]} {
    puts "ERROR during route_design of $top_name: $err"
    continue
  }
  
  # Write checkpoint
  write_checkpoint -force "${output_dir}/${top_name}_routed.dcp"
  
  # Generate Bitstream
  if {[catch {write_bitstream -force "${output_dir}/${top_name}.bit"} err]} {
    puts "ERROR during write_bitstream of $top_name: $err"
  }
  
  # Generate reports
  report_utilization -file "${output_dir}/${top_name}_utilization.rpt"
  report_power -file "${output_dir}/${top_name}_power.rpt"
  report_timing_summary -file "${output_dir}/${top_name}_timing.rpt"
  
  # Clean up project
  close_project
}

puts "All Vivado runs finished."
exit
