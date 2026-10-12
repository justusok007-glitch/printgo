# SpliX "rastertoqpdl" CUPS filter for Samsung, Xerox and Dell SPL/QPDL lasers, built from the same file
# list as SpliX's src/module.mk. JBIG compression (needed by some colour models) comes from jbigkit when
# JBIG_SRC is given. SpliX is GPL-2.0-only; it runs as its own program, separate from PrintGo.
cmake_minimum_required(VERSION 3.18)
project(pg_splix C CXX)

set(SPLIX_SRC "" CACHE PATH "Unpacked splix source")
set(JBIG_SRC "" CACHE PATH "Unpacked jbigkit source (optional)")
set(CUPS_INCLUDE "" CACHE PATH "Directory containing cups/*.h")
set(CUPS_LIB "" CACHE FILEPATH "libcups.a")

set(S ${SPLIX_SRC}/src)
add_executable(pg_splix
  ${S}/rastertoqpdl.cpp ${S}/request.cpp ${S}/printer.cpp ${S}/qpdl.cpp ${S}/document.cpp ${S}/core.cpp
  ${S}/compress.cpp ${S}/algorithm.cpp ${S}/ppdfile.cpp ${S}/page.cpp ${S}/colors.cpp ${S}/band.cpp
  ${S}/bandplane.cpp ${S}/cache.cpp ${S}/rendering.cpp ${S}/semaphore.cpp ${S}/algo0x0d.cpp
  ${S}/algo0x0e.cpp ${S}/algo0x11.cpp ${S}/algo0x13.cpp ${S}/algo0x15.cpp)
target_include_directories(pg_splix PRIVATE ${SPLIX_SRC}/include ${CUPS_INCLUDE})
target_compile_definitions(pg_splix PRIVATE THREADS=2 CACHESIZE=30)
target_compile_options(pg_splix PRIVATE -w)
if(JBIG_SRC)
  add_library(jbig85 STATIC ${JBIG_SRC}/libjbig/jbig85.c ${JBIG_SRC}/libjbig/jbig_ar.c)
  target_include_directories(jbig85 PUBLIC ${JBIG_SRC}/libjbig)
  target_link_libraries(pg_splix PRIVATE jbig85)
else()
  target_compile_definitions(pg_splix PRIVATE DISABLE_JBIG)
endif()
target_link_libraries(pg_splix PRIVATE ${CUPS_LIB} z m)
if(ANDROID)
  set_target_properties(pg_splix PROPERTIES OUTPUT_NAME "libpg_splix.so" SUFFIX "")
  target_link_options(pg_splix PRIVATE -Wl,-z,max-page-size=16384 -static-libstdc++)
else()
  find_package(Threads REQUIRED)
  target_link_libraries(pg_splix PRIVATE Threads::Threads)
endif()
install(TARGETS pg_splix RUNTIME DESTINATION .)
