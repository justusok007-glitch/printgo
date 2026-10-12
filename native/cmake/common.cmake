# Shared settings for the driver programs. The driver sources are older C/C++ that desktop GCC accepts with
# warnings; clang 16+ (Android NDK r26+) turns some of those warnings into errors. Keep them as warnings.
# Copyright (C) 2026 the PrintGo authors. SPDX-License-Identifier: GPL-3.0-or-later
add_compile_options(
  "$<$<COMPILE_LANGUAGE:C>:-Wno-error=implicit-function-declaration;-Wno-error=int-conversion;-Wno-error=incompatible-pointer-types;-Wno-error=implicit-int>"
  "$<$<COMPILE_LANGUAGE:CXX>:-Wno-register;-Wno-narrowing>")
