# -------------------------------------------------------------------------
# Nexys A7-100T / Nexys 4 DDR Constraints for ConfigurablePalmSoC
# -------------------------------------------------------------------------

# Clock (100 MHz)
set_property -dict { PACKAGE_PIN E3    IOSTANDARD LVCMOS33 } [get_ports { clock }];
create_clock -period 10.000 -name clk [get_ports clock]

# Reset (CPU_RESETN is active low on board, but Chisel reset is active high. 
# You may need to press it to release reset, or invert it in logic. Mapped to C12)
set_property -dict { PACKAGE_PIN C12   IOSTANDARD LVCMOS33 } [get_ports { reset }];

# -------------------------------------------------------------------------
# UART
# -------------------------------------------------------------------------
# USB-RS232 Interface
set_property -dict { PACKAGE_PIN D4    IOSTANDARD LVCMOS33 } [get_ports { io_uart_tx }];
set_property -dict { PACKAGE_PIN C4    IOSTANDARD LVCMOS33 } [get_ports { io_uart_rx }];

# -------------------------------------------------------------------------
# GPIO IN (18 bits) -> 16 Switches + 2 Buttons
# -------------------------------------------------------------------------
set_property -dict { PACKAGE_PIN J15   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_in[0] }];
set_property -dict { PACKAGE_PIN L16   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_in[1] }];
set_property -dict { PACKAGE_PIN M13   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_in[2] }];
set_property -dict { PACKAGE_PIN R15   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_in[3] }];
set_property -dict { PACKAGE_PIN R17   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_in[4] }];
set_property -dict { PACKAGE_PIN T18   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_in[5] }];
set_property -dict { PACKAGE_PIN U18   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_in[6] }];
set_property -dict { PACKAGE_PIN R13   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_in[7] }];
set_property -dict { PACKAGE_PIN T8    IOSTANDARD LVCMOS18 } [get_ports { io_gpio_in[8] }];
set_property -dict { PACKAGE_PIN U8    IOSTANDARD LVCMOS18 } [get_ports { io_gpio_in[9] }];
set_property -dict { PACKAGE_PIN R16   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_in[10] }];
set_property -dict { PACKAGE_PIN T13   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_in[11] }];
set_property -dict { PACKAGE_PIN H6    IOSTANDARD LVCMOS33 } [get_ports { io_gpio_in[12] }];
set_property -dict { PACKAGE_PIN U12   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_in[13] }];
set_property -dict { PACKAGE_PIN U11   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_in[14] }];
set_property -dict { PACKAGE_PIN V10   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_in[15] }];
# Buttons for remaining 2 bits
set_property -dict { PACKAGE_PIN M18   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_in[16] }]; # BTNU
set_property -dict { PACKAGE_PIN P18   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_in[17] }]; # BTND

# -------------------------------------------------------------------------
# GPIO OUT (18 bits) -> 16 LEDs + 2 RGB LED pins
# -------------------------------------------------------------------------
set_property -dict { PACKAGE_PIN H17   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_out[0] }];
set_property -dict { PACKAGE_PIN K15   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_out[1] }];
set_property -dict { PACKAGE_PIN J13   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_out[2] }];
set_property -dict { PACKAGE_PIN N14   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_out[3] }];
set_property -dict { PACKAGE_PIN R18   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_out[4] }];
set_property -dict { PACKAGE_PIN V17   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_out[5] }];
set_property -dict { PACKAGE_PIN U17   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_out[6] }];
set_property -dict { PACKAGE_PIN U16   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_out[7] }];
set_property -dict { PACKAGE_PIN V16   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_out[8] }];
set_property -dict { PACKAGE_PIN T15   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_out[9] }];
set_property -dict { PACKAGE_PIN U14   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_out[10] }];
set_property -dict { PACKAGE_PIN T16   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_out[11] }];
set_property -dict { PACKAGE_PIN V15   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_out[12] }];
set_property -dict { PACKAGE_PIN V14   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_out[13] }];
set_property -dict { PACKAGE_PIN V12   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_out[14] }];
set_property -dict { PACKAGE_PIN V11   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_out[15] }];
# Remaining 2 bits to RGB LED pins
set_property -dict { PACKAGE_PIN R12   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_out[16] }]; # LED16_B
set_property -dict { PACKAGE_PIN M16   IOSTANDARD LVCMOS33 } [get_ports { io_gpio_out[17] }]; # LED16_G

# -------------------------------------------------------------------------
# Ignore I2C and GPIO OE pins for now (Bitstream generation will pass with warnings)
# -------------------------------------------------------------------------
set_property -dict { PACKAGE_PIN A14   IOSTANDARD LVCMOS33 } [get_ports { io_i2c_scl_in }]; # Dummy Pmod
set_property -dict { PACKAGE_PIN A13   IOSTANDARD LVCMOS33 } [get_ports { io_i2c_sda_in }]; # Dummy Pmod

# Unconnected outputs can be left alone, Vivado will optimize them away or leave them unconnected.