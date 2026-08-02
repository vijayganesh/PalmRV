OUTPUT_ARCH( "riscv" )
ENTRY( _start )

MEMORY
{
  ROM (rx)  : ORIGIN = 0x00000000, LENGTH = 128K
  RAM (rwx) : ORIGIN = 0x20000000, LENGTH = 256K
}

SECTIONS
{
  .text :
  {
    *(.init)
    *(.text)
    *(.text.*)
    *(.rodata)
    *(.rodata.*)
    *(.srodata)
    *(.srodata.*)
  } > ROM

  /* Load address of data section */
  _sidata = LOADADDR(.data);

  .data :
  {
    . = ALIGN(4);
    _sdata = .;
    PROVIDE( __global_pointer$ = . + 0x800 );
    *(.data)
    *(.data.*)
    *(.sdata)
    *(.sdata.*)
    . = ALIGN(4);
    _edata = .;
  } > RAM AT > ROM

  .bss :
  {
    . = ALIGN(4);
    _sbss = .;
    *(.bss)
    *(.bss.*)
    *(.sbss)
    *(.sbss.*)
    *(COMMON)
    . = ALIGN(4);
    _ebss = .;
  } > RAM

  _end = .;
  PROVIDE(end = .);
  
  /* Stack at the end of SRAM */
  _stack_top = ORIGIN(RAM) + LENGTH(RAM);
}
