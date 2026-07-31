import re

def fix_handshakes(filepath):
    with open(filepath, 'r') as f:
        content = f.read()
    
    # 1. Fix AWVALID/AWREADY
    # Old:
    # dut.io.axi.awvalid.poke(true.B)
    # [optional dut.clock.step(1)]
    # while (!dut.io.axi.awready.peek().litToBoolean) { ... }
    # dut.io.axi.awvalid.poke(false.B)
    content = re.sub(
        r'dut\.io\.axi\.awvalid\.poke\(true\.B\)\s*(?:dut\.clock\.step\(\d+\)\s*)?while\s*\(!dut\.io\.axi\.awready\.peek\(\)\.litToBoolean\)\s*\{\s*dut\.clock\.step\(\d+\)\s*\}\s*(?:dut\.clock\.step\(\d+\)\s*)?dut\.io\.axi\.awvalid\.poke\(false\.B\)',
        'dut.io.axi.awvalid.poke(true.B)\n      while (!dut.io.axi.awready.peek().litToBoolean) { dut.clock.step(1) }\n      dut.clock.step(1)\n      dut.io.axi.awvalid.poke(false.B)',
        content
    )
    
    # 2. Fix WVALID/WREADY
    content = re.sub(
        r'dut\.io\.axi\.wvalid\.poke\(true\.B\)\s*(?:dut\.clock\.step\(\d+\)\s*)?while\s*\(!dut\.io\.axi\.wready\.peek\(\)\.litToBoolean\)\s*\{\s*dut\.clock\.step\(\d+\)\s*\}\s*(?:dut\.clock\.step\(\d+\)\s*)?dut\.io\.axi\.wvalid\.poke\(false\.B\)',
        'dut.io.axi.wvalid.poke(true.B)\n      while (!dut.io.axi.wready.peek().litToBoolean) { dut.clock.step(1) }\n      dut.clock.step(1)\n      dut.io.axi.wvalid.poke(false.B)',
        content
    )
    
    # 3. Fix ARVALID/ARREADY
    content = re.sub(
        r'dut\.io\.axi\.arvalid\.poke\(true\.B\)\s*(?:dut\.clock\.step\(\d+\)\s*)?while\s*\(!dut\.io\.axi\.arready\.peek\(\)\.litToBoolean\)\s*\{\s*dut\.clock\.step\(\d+\)\s*\}\s*(?:dut\.clock\.step\(\d+\)\s*)?dut\.io\.axi\.arvalid\.poke\(false\.B\)',
        'dut.io.axi.arvalid.poke(true.B)\n      while (!dut.io.axi.arready.peek().litToBoolean) { dut.clock.step(1) }\n      dut.clock.step(1)\n      dut.io.axi.arvalid.poke(false.B)',
        content
    )
    
    # 4. Fix BREADY/BVALID
    content = re.sub(
        r'dut\.io\.axi\.bready\.poke\(true\.B\)\s*(?:dut\.clock\.step\(\d+\)\s*)?while\s*\(!dut\.io\.axi\.bvalid\.peek\(\)\.litToBoolean\)\s*\{\s*dut\.clock\.step\(\d+\)\s*\}\s*(?:dut\.clock\.step\(\d+\)\s*)?dut\.io\.axi\.bready\.poke\(false\.B\)',
        'dut.io.axi.bready.poke(true.B)\n      while (!dut.io.axi.bvalid.peek().litToBoolean) { dut.clock.step(1) }\n      dut.clock.step(1)\n      dut.io.axi.bready.poke(false.B)',
        content
    )
    
    # 5. Fix RREADY/RVALID (Special because expects are inside)
    # This one is trickier. Let's just fix the step before the while loop.
    content = re.sub(
        r'dut\.io\.axi\.rready\.poke\(true\.B\)\s+dut\.clock\.step\(\d+\)\s+while\s*\(!dut\.io\.axi\.rvalid\.peek\(\)\.litToBoolean\)',
        'dut.io.axi.rready.poke(true.B)\n      while (!dut.io.axi.rvalid.peek().litToBoolean)',
        content
    )

    with open(filepath, 'w') as f:
        f.write(content)

fix_handshakes("src/test/scala/palmsoc/peripheral/GPIOTest.scala")
fix_handshakes("src/test/scala/palmsoc/peripheral/UARTTest.scala")
fix_handshakes("src/test/scala/palmsoc/peripheral/I2CTest.scala")
fix_handshakes("src/test/scala/palmsoc/peripheral/DMARegressionTest.scala")
fix_handshakes("src/test/scala/palmsoc/peripheral/DMATest.scala")
