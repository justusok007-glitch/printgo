/*
 * PrintGo raster stream ("PGR"): how the app hands rendered pages to a driver program.
 * Copyright (C) 2026 the PrintGo authors. SPDX-License-Identifier: GPL-3.0-or-later
 *
 *   stream := "PGR1" page* "DONE"
 *   page   := "PAGE" width height xdpi ydpi channels flags   (six little-endian uint32)
 *             height rows of width*channels bytes, top row first
 *   channels: 3 = RGB (8 bit each), 1 = grey (0 black .. 255 white)
 *   flags:    bit 0 = this is the back side of a two-sided sheet
 */
#ifndef PGR_H
#define PGR_H
#include <stdint.h>
#include <stdio.h>
#include <string.h>

typedef struct {
  uint32_t width, height, xdpi, ydpi, channels, flags;
} pgr_page;

static int pgr_read_exact(FILE *in, void *buf, size_t n) {
  return fread(buf, 1, n, in) == n;
}

static uint32_t pgr_u32(const unsigned char *p) {
  return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

/* 1 when the stream starts with the PGR1 magic. */
static int pgr_read_start(FILE *in) {
  char m[4];
  return pgr_read_exact(in, m, 4) && memcmp(m, "PGR1", 4) == 0;
}

/* 1 = a page header was read, 0 = end of stream, -1 = broken stream. */
static int pgr_next_page(FILE *in, pgr_page *pg) {
  unsigned char b[28];
  if (!pgr_read_exact(in, b, 4)) return -1;
  if (memcmp(b, "DONE", 4) == 0) return 0;
  if (memcmp(b, "PAGE", 4) != 0) return -1;
  if (!pgr_read_exact(in, b + 4, 24)) return -1;
  pg->width = pgr_u32(b + 4);
  pg->height = pgr_u32(b + 8);
  pg->xdpi = pgr_u32(b + 12);
  pg->ydpi = pgr_u32(b + 16);
  pg->channels = pgr_u32(b + 20);
  pg->flags = pgr_u32(b + 24);
  if (pg->width == 0 || pg->height == 0 || pg->width > 40000 || pg->height > 80000) return -1;
  if (pg->channels != 1 && pg->channels != 3) return -1;
  if (pg->xdpi == 0) pg->xdpi = 300;
  if (pg->ydpi == 0) pg->ydpi = pg->xdpi;
  return 1;
}

static int pgr_read_row(FILE *in, const pgr_page *pg, unsigned char *row) {
  return pgr_read_exact(in, row, (size_t)pg->width * pg->channels);
}

#endif
