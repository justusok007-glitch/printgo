# PrintGo's own driver front-ends (GPL-3.0-or-later): pg_gutenprint and pg_cupsraster.
cmake_minimum_required(VERSION 3.18)
project(pg_tools C)

set(GUTENPRINT_SRC "" CACHE PATH "Unpacked gutenprint source")
set(GUTENPRINT_BUILD "" CACHE PATH "Gutenprint build dir (include/, src/main/.libs/libgutenprint.a)")
set(CUPS_INCLUDE "" CACHE PATH "Directory containing cups/*.h")
set(CUPS_LIB "" CACHE FILEPATH "libcups.a")
set(PG_SRC ${CMAKE_CURRENT_LIST_DIR}/../../src)

add_executable(pg_gutenprint ${PG_SRC}/pg_gutenprint.c)
target_include_directories(pg_gutenprint PRIVATE ${GUTENPRINT_SRC}/include ${GUTENPRINT_BUILD}/include)
target_link_libraries(pg_gutenprint PRIVATE ${GUTENPRINT_BUILD}/src/main/.libs/libgutenprint.a m)

add_executable(pg_cupsraster ${PG_SRC}/pg_cupsraster.c)
target_include_directories(pg_cupsraster PRIVATE ${CUPS_INCLUDE})
target_compile_options(pg_cupsraster PRIVATE -Wno-deprecated-declarations)
target_link_libraries(pg_cupsraster PRIVATE ${CUPS_LIB} z m)

if(ANDROID)
  foreach(t pg_gutenprint pg_cupsraster)
    set_target_properties(${t} PROPERTIES OUTPUT_NAME "lib${t}.so" SUFFIX "")
    target_link_options(${t} PRIVATE -Wl,-z,max-page-size=16384)
  endforeach()
else()
  find_package(Threads REQUIRED)
  target_link_libraries(pg_cupsraster PRIVATE Threads::Threads)
endif()
install(TARGETS pg_gutenprint pg_cupsraster RUNTIME DESTINATION .)
