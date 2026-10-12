# Epson Inkjet Printer Driver (ESC/P-R) for Linux, built the same way as its own Makefile.am:
# the ESC/P-R library with its DEFINES, and the "epson-escpr" filter program on top.
# The program is named libpg_escpr.so so that Android installs it next to the app's native code.
cmake_minimum_required(VERSION 3.18)
project(pg_escpr C)
include(${CMAKE_CURRENT_LIST_DIR}/../common.cmake)

set(ESCPR_SRC "" CACHE PATH "Unpacked epson-inkjet-printer-escpr source")
set(L ${ESCPR_SRC}/lib)
set(S ${ESCPR_SRC}/src)

add_library(escpr STATIC
  ${L}/epson-cbt.c ${L}/epson-escpr-api.c ${L}/epson-escpr-dbg.c ${L}/epson-escpr-mem.c
  ${L}/epson-escpr-pm.c ${L}/epson-escpr-services.c ${L}/epson-layout.c ${L}/epson-net-lpr.c
  ${L}/epson-net-raw.c ${L}/epson-net-snmp.c ${L}/epson-protocol.c ${L}/epson-usb.c)
target_compile_definitions(escpr PRIVATE
  GCOMSW_PRT_USE_USB GCOMSW_PRT_USE_LPR GCOMSW_PRT_USE_RAW GCOMSW_EL_CDLABEL GCOMSW_EF_MAINTE
  EPS_FILTER GCOMSW_ASYNCJOB_SUPPORT GCOMSW_RAWFILE_SUPPORT)
target_compile_options(escpr PRIVATE -w -fcommon)

add_executable(pg_escpr
  ${S}/filter.c ${S}/debug.c ${S}/str.c ${S}/err.c ${S}/mem.c ${S}/custompage.c ${S}/linux_cmn.c ${S}/xfifo.c)
target_include_directories(pg_escpr PRIVATE ${L})
target_compile_definitions(pg_escpr PRIVATE PACKAGE_STRING="epson-inkjet-printer-escpr 1.8.8")
target_compile_options(pg_escpr PRIVATE -w -fcommon)
target_link_libraries(pg_escpr PRIVATE escpr m)
if(ANDROID)
  set_target_properties(pg_escpr PROPERTIES OUTPUT_NAME "libpg_escpr.so" SUFFIX "")
  target_link_options(pg_escpr PRIVATE -Wl,-z,max-page-size=16384)
else()
  find_package(Threads REQUIRED)
  target_link_libraries(pg_escpr PRIVATE Threads::Threads)
endif()
install(TARGETS pg_escpr RUNTIME DESTINATION .)
