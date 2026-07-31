import re

with open("src/test/scala/palmsoc/peripheral/I2CTest.scala", "r") as f:
    content = f.read()

# Let's print the full status register to see exactly what bits are set!
print_patch = """
        // Transaction should be complete.
        dut.clock.step(2)
        val status = axiRead(dut, 0x08)
        println(f"STATUS REGISTER: 0x${status}%08X")
        assert((status & 0x02) == 0, "rx_ack bit should be 0 (ACK received from slave)")
"""

content = re.sub(r'// Transaction should be complete\..*?(?=^\s*\}\s*$)', print_patch, content, flags=re.MULTILINE | re.DOTALL)

with open("src/test/scala/palmsoc/peripheral/I2CTest.scala", "w") as f:
    f.write(content)

