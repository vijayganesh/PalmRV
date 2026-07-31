import re

with open("src/test/scala/palmsoc/peripheral/GPIOTest.scala", "r") as f:
    content = f.read()

# We want to replace patterns like:
# dut.io.axi.arvalid.poke(true.B)
# while (!dut.io.axi.arready.peek().litToBoolean) {
#   dut.clock.step(1)
# }
# dut.io.axi.arvalid.poke(false.B)
#
# with:
# dut.io.axi.arvalid.poke(true.B)
# while (!dut.io.axi.arready.peek().litToBoolean) { dut.clock.step(1) }
# dut.clock.step(1)
# dut.io.axi.arvalid.poke(false.B)

# Specifically, we look for:
# while (!dut.io.axi.X.peek().litToBoolean) { ... }
# (optional newlines/spaces)
# dut.io.axi.Y.poke(false.B)

# Actually, the most foolproof way is to find:
# while (!dut.io.axi.(awready|wready|bvalid|arready|rvalid).peek().litToBoolean) \{[^}]*\}
# and append `\n      dut.clock.step(1)` right after it!

def replacer(match):
    return match.group(0) + "\n      dut.clock.step(1)"

pattern = re.compile(r'while\s*\(!dut\.io\.axi\.(awready|wready|bvalid|arready|rvalid)\.peek\(\)\.litToBoolean\)\s*\{[^}]*\}')

content = pattern.sub(replacer, content)

# But wait! For `bvalid` and `rvalid`, when we wait for them, we usually then check .expect(true.B) or poke ready false.
# Let's check how they are used.

with open("src/test/scala/palmsoc/peripheral/GPIOTest.scala", "w") as f:
    f.write(content)
