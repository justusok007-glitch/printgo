# HPLIP's hpcups CUPS filter, built from the same file list as HPLIP's Makefile.am (hpcups_SOURCES).
# Differences from a desktop build:
#  - DISABLE_IMAGEPROCESSOR: HP ships the image processor only as an x86 binary blob, and it is used only
#    for "ljzjstream" printers, which also need HP's proprietary plug-in. Those printers are left out.
#  - No D-Bus (HAVE_DBUS undefined): there is no desktop session to notify.
# The program is named libpg_hpcups.so so that Android installs it next to the app's native code.
cmake_minimum_required(VERSION 3.18)
project(pg_hpcups C CXX)

set(HPLIP_SRC "" CACHE PATH "Unpacked hplip source")
set(CUPS_INCLUDE "" CACHE PATH "Directory containing cups/*.h")
set(CUPS_LIB "" CACHE FILEPATH "libcups.a")
set(JPEG_SRC "" CACHE PATH "libjpeg-turbo source (for jpegint.h)")
set(JPEG_BUILD "" CACHE PATH "libjpeg-turbo build dir (jconfig.h, libjpeg.a)")

set(H ${HPLIP_SRC}/prnt/hpcups)
set(SRCS
  HPCupsFilter.cpp dbuscomm.cpp Compressor.cpp Mode2.cpp Mode3.cpp Mode9.cpp Mode10.cpp ModeDeltaPlus.cpp
  ModeJbig.cpp ErnieFilter.cpp EncapsulatorFactory.cpp Encapsulator.cpp Pcl3.cpp Pcl3Gui.cpp Pcl3Gui2.cpp
  LJMono.cpp LJColor.cpp LJFastRaster.cpp LJJetReady.cpp LJZjStream.cpp LJZxStream.cpp Job.cpp Pipeline.cpp
  Processor.cpp RasterSender.cpp ColorMatcher.cpp Halftoner.cpp Scaler.cpp dj400ColorMaps.cpp
  dj600ColorMaps.cpp dj970ColorMaps.cpp dj8xxColorMaps.cpp dj4100ColorMaps.cpp BreakTables.cpp Utils.cpp
  SystemServices.cpp QuickConnect.cpp ModeJpeg.cpp jccolor.c jdatadbf.c Lidil.cpp LidilCompress.cpp
  dj3320ColorMaps.cpp dj3600ColorMaps.cpp Hbpl1.cpp Hbpl1_Wrapper.cpp genPCLm.cpp genJPEGStrips.cpp
  RunLenEncoding.cpp)
list(TRANSFORM SRCS PREPEND ${H}/)
add_executable(pg_hpcups ${SRCS} ${HPLIP_SRC}/common/utils.c)
target_include_directories(pg_hpcups PRIVATE ${H} ${HPLIP_SRC}/common ${HPLIP_SRC}/io/hpmud ${HPLIP_SRC} ${CUPS_INCLUDE}
  ${JPEG_BUILD} ${JPEG_SRC}/src ${JPEG_SRC})
target_compile_definitions(pg_hpcups PRIVATE APDK_LITTLE_ENDIAN DISABLE_IMAGEPROCESSOR
  CONFDIR="/nonexistent/hplip" VERSION="3.25.8")
target_compile_options(pg_hpcups PRIVATE -w -fcommon $<$<COMPILE_LANGUAGE:CXX>:-fpermissive>)
target_link_libraries(pg_hpcups PRIVATE ${CUPS_LIB} ${JPEG_BUILD}/libjpeg.a z m)
if(ANDROID)
  set_target_properties(pg_hpcups PROPERTIES OUTPUT_NAME "libpg_hpcups.so" SUFFIX "")
  target_link_options(pg_hpcups PRIVATE -Wl,-z,max-page-size=16384)
else()
  find_package(Threads REQUIRED)
  target_link_libraries(pg_hpcups PRIVATE Threads::Threads dl)
endif()
install(TARGETS pg_hpcups RUNTIME DESTINATION .)
