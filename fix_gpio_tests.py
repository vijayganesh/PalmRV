import re

with open("src/test/scala/palmsoc/peripheral/GPIOTest.scala", "r") as f:
    content = f.read()

# Replace any dut.clock.step(1) that is followed by while (!dut.io.axi.*)
# This includes optional whitespace
pattern = re.compile(r'dut\.clock\.step\(1\)\s+while\s*\(!dut\.io\.axi\.')
content = pattern.sub(r'while (!dut.io.axi.', content)

with open("src/test/scala/palmsoc/peripheral/GPIOTest.scala", "w") as f:
    f.write(content)
