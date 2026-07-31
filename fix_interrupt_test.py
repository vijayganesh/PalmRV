import re

with open("src/test/scala/palmsoc/peripheral/GPIOTest.scala", "r") as f:
    content = f.read()

# We need to change the order of configuration.
# Right now it's: INT_EN (0x1C), INT_TYPE (0x20), INT_POL (0x24)
# We want: INT_TYPE (0x20), INT_POL (0x24), INT_EN (0x1C)

# Let's just find "Enable interrupt for pin 0" and "Set INT_TYPE to edge" and swap the blocks.
# Actually, the simplest fix is to just clear the INT_STAT right before testing!

# Let's insert a clear right before applying the rising edge!
clear_code = """
        // Clear any spurious interrupts that might have fired during configuration
        dut.io.axi.awaddr.poke(0x28.U)  // INT_STAT
        dut.io.axi.awvalid.poke(true.B)
        while (!dut.io.axi.awready.peek().litToBoolean) { dut.clock.step(1) }
        dut.clock.step(1)
        dut.io.axi.awvalid.poke(false.B)
        
        dut.io.axi.wdata.poke(0xFFFFFFFF.U)
        dut.io.axi.wstrb.poke(0xF.U)
        dut.io.axi.wvalid.poke(true.B)
        while (!dut.io.axi.wready.peek().litToBoolean) { dut.clock.step(1) }
        dut.clock.step(1)
        dut.io.axi.wvalid.poke(false.B)
        
        dut.io.axi.bready.poke(true.B)
        while (!dut.io.axi.bvalid.peek().litToBoolean) { dut.clock.step(1) }
        dut.clock.step(1)
        dut.io.axi.bready.poke(false.B)
        dut.clock.step(1)
        
"""

# Find:
#         // Apply rising edge on pin 0
#         dut.gpio_in.poke(0x00.U)
#         dut.clock.step(2)

content = content.replace(
    "        // Apply rising edge on pin 0\n        dut.gpio_in.poke(0x00.U)\n        dut.clock.step(2)\n",
    clear_code + "        // Apply rising edge on pin 0\n        dut.gpio_in.poke(0x00.U)\n        dut.clock.step(2)\n"
)

with open("src/test/scala/palmsoc/peripheral/GPIOTest.scala", "w") as f:
    f.write(content)
