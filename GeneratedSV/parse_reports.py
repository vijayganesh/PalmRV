import os
import re

def parse_utilization(file_path):
    lut, reg, dsp, bram = "N/A", "N/A", "N/A", "N/A"
    if not os.path.exists(file_path):
        return lut, reg, dsp, bram
    
    with open(file_path, 'r') as f:
        for line in f:
            if re.search(r'\|\s*Slice LUTs\*?\s*\|', line):
                parts = [p.strip() for p in line.split('|') if p.strip()]
                if len(parts) >= 2:
                    lut = parts[1]
            elif re.search(r'\|\s*Slice Registers\s*\|', line):
                parts = [p.strip() for p in line.split('|') if p.strip()]
                if len(parts) >= 2:
                    reg = parts[1]
            elif re.search(r'\|\s*DSPs\*?\s*\|', line) or re.search(r'\|\s*DSP48E\d* only\s*\|', line):
                parts = [p.strip() for p in line.split('|') if p.strip()]
                if len(parts) >= 2:
                    dsp = parts[1]
            elif re.search(r'\|\s*Block RAM Tile\s*\|', line):
                parts = [p.strip() for p in line.split('|') if p.strip()]
                if len(parts) >= 2:
                    bram = parts[1]
    return lut, reg, dsp, bram

def parse_power(file_path):
    total, dynamic, static = "N/A", "N/A", "N/A"
    if not os.path.exists(file_path):
        return total, dynamic, static
    
    with open(file_path, 'r') as f:
        for line in f:
            if "Total On-Chip Power (W)" in line:
                parts = [p.strip() for p in line.split('|') if p.strip()]
                if len(parts) >= 2:
                    try:
                        total = f"{float(parts[1]):.4f} W"
                    except ValueError:
                        total = parts[1]
            elif "Dynamic (W)" in line:
                parts = [p.strip() for p in line.split('|') if p.strip()]
                if len(parts) >= 2:
                    try:
                        dynamic = f"{float(parts[1]):.4f} W"
                    except ValueError:
                        dynamic = parts[1]
            elif "Device Static (W)" in line:
                parts = [p.strip() for p in line.split('|') if p.strip()]
                if len(parts) >= 2:
                    try:
                        static = f"{float(parts[1]):.4f} W"
                    except ValueError:
                        static = parts[1]
    return total, dynamic, static

def parse_timing(file_path):
    wns, tns = "N/A", "N/A"
    if not os.path.exists(file_path):
        return wns, tns
    
    with open(file_path, 'r') as f:
        lines = f.readlines()
        
    for i, line in enumerate(lines):
        if "WNS(ns)" in line and "TNS(ns)" in line:
            for offset in [1, 2, 3, 4]:
                if i + offset < len(lines):
                    val_line = lines[i + offset].strip()
                    if val_line and not val_line.startswith("---") and not val_line.startswith("WNS"):
                        parts = val_line.split()
                        if len(parts) >= 2:
                            wns = parts[0]
                            tns = parts[1]
                            break
            break
    return wns, tns

def main():
    reports_dir = "GeneratedSV/reports"
    modules = ["LSTM_Accelerator_Top", "ConfigurablePalmSoC"]
    
    print(f"{'Module Name':<30} | {'LUTs':<8} | {'Registers':<10} | {'DSPs':<6} | {'BRAMs':<6} | {'Total Power':<12} | {'Dynamic':<10} | {'Static':<10} | {'WNS (ns)':<8} | {'TNS (ns)':<8}")
    print("-" * 125)
    
    csv_rows = []
    for mod in modules:
        util_path = os.path.join(reports_dir, f"{mod}_utilization.rpt")
        power_path = os.path.join(reports_dir, f"{mod}_power.rpt")
        timing_path = os.path.join(reports_dir, f"{mod}_timing.rpt")
        
        lut, reg, dsp, bram = parse_utilization(util_path)
        total, dynamic, static = parse_power(power_path)
        wns, tns = parse_timing(timing_path)
        
        print(f"{mod:<30} | {lut:<8} | {reg:<10} | {dsp:<6} | {bram:<6} | {total:<12} | {dynamic:<10} | {static:<10} | {wns:<8} | {tns:<8}")
        
        total_clean = total.replace(" W", "")
        dynamic_clean = dynamic.replace(" W", "")
        static_clean = static.replace(" W", "")
        csv_rows.append(f"{mod},{lut},{reg},{dsp},{bram},{total_clean},{dynamic_clean},{static_clean},{wns},{tns}")
        
    csv_path = "docs/acc/comparison of LSTM_SoC.csv"
    os.makedirs(os.path.dirname(csv_path), exist_ok=True)
    with open(csv_path, "w") as f:
        f.write("Module Name,Slice LUTs,Slice Registers,DSPs,Block RAM,Total Power (W),Dynamic Power (W),Static Power (W),WNS (ns),TNS (ns)\n")
        for row in csv_rows:
            f.write(row + "\n")
    print(f"\nSaved CSV results to: {csv_path}")

if __name__ == "__main__":
    main()
