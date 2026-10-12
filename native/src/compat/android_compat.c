/*
 * Small replacements for C library functions that Android's libc only gained in later versions than
 * PrintGo's minimum (Android 8, API 26). Linked into the driver programs that need them.
 * Copyright (C) 2026 the PrintGo authors. SPDX-License-Identifier: GPL-3.0-or-later
 */
#if defined(__ANDROID__) && __ANDROID_API__ < 28
#include <sys/time.h>
#include <sys/timeb.h>

/* ftime() arrived in API 28; the Epson library uses it only to measure short waits. */
int ftime(struct timeb *tp) {
  struct timeval tv;
  gettimeofday(&tv, 0);
  tp->time = tv.tv_sec;
  tp->millitm = (unsigned short) (tv.tv_usec / 1000);
  tp->timezone = 0;
  tp->dstflag = 0;
  return 0;
}
#else
/* Nothing to add on this platform. */
typedef int pg_android_compat_unused;
#endif
