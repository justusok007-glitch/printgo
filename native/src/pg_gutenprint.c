/*
 * pg_gutenprint: prints a PrintGo raster stream (pgr.h) on stdin with a Gutenprint driver and writes
 * the printer's own data to stdout.
 *
 * Copyright (C) 2026 the PrintGo authors. SPDX-License-Identifier: GPL-3.0-or-later
 * Uses libgutenprint (GPL-2.0-or-later).
 *
 *   pg_gutenprint --list                       one line per printer: driver, make, model, family, device id,
 *                                              colour (0/1), two-sided (0/1)
 *   pg_gutenprint --describe DRIVER            the options a driver accepts
 *   pg_gutenprint DRIVER [Key=Value ...]       print; keys are Gutenprint parameters, plus
 *        PaperPt=WIDTHxHEIGHT   paper size in points; the closest named PageSize is chosen
 *   Gutenprint's data files are found through STP_DATA_PATH (or --data=DIR as first argument).
 *   Values a driver does not offer are ignored (with a note on stderr) so that a job still prints.
 */
#include <gutenprint/gutenprint.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "pgr.h"

static FILE *in;
static pgr_page cur;
static int crop_x, crop_y, img_w, img_h;
static uint32_t next_row;
static unsigned char *rowbuf;
static int read_failed;

static void out_fn(void *data, const char *buf, size_t n) { fwrite(buf, 1, n, (FILE *) data); }
static void err_fn(void *data, const char *buf, size_t n) { fwrite(buf, 1, n, (FILE *) data); }

static void img_init(stp_image_t *i) { (void) i; }
static void img_reset(stp_image_t *i) { (void) i; }
static int img_width(stp_image_t *i) { (void) i; return img_w; }
static int img_height(stp_image_t *i) { (void) i; return img_h; }
static const char *img_appname(stp_image_t *i) { (void) i; return "PrintGo"; }
static void img_conclude(stp_image_t *i) { (void) i; }

static stp_image_status_t img_get_row(stp_image_t *i, unsigned char *data, size_t limit, int row) {
  (void) i;
  size_t ch = cur.channels;
  size_t want = (size_t) img_w * ch;
  if (want > limit) want = limit;
  long sy = (long) crop_y + row;
  memset(data, 0xff, want);
  if (sy < 0 || sy >= (long) cur.height) return STP_IMAGE_STATUS_OK;
  while ((long) next_row <= sy) {
    if (!pgr_read_row(in, &cur, rowbuf)) { read_failed = 1; return STP_IMAGE_STATUS_ABORT; }
    next_row++;
  }
  long sx = crop_x;
  for (long x = 0; x < img_w && (size_t) x * ch < want; x++, sx++) {
    if (sx < 0 || sx >= (long) cur.width) continue;
    memcpy(data + x * ch, rowbuf + sx * ch, ch);
  }
  return STP_IMAGE_STATUS_OK;
}

static stp_image_t image = {
  img_init, img_reset, img_width, img_height, img_get_row, img_appname, img_conclude, NULL
};

static int offers(const stp_vars_t *v, const char *name, const char *value) {
  stp_parameter_t d;
  int ok = 0;
  stp_describe_parameter(v, name, &d);
  if (d.p_type == STP_PARAMETER_TYPE_STRING_LIST && d.bounds.str) {
    int n = stp_string_list_count(d.bounds.str);
    for (int k = 0; k < n && !ok; k++)
      ok = strcmp(stp_string_list_param(d.bounds.str, k)->name, value) == 0;
  } else if (d.p_type != STP_PARAMETER_TYPE_INVALID) {
    ok = 1;
  }
  stp_parameter_description_destroy(&d);
  return ok;
}

static void set_param(stp_vars_t *v, const char *key, const char *value) {
  stp_parameter_t d;
  stp_describe_parameter(v, key, &d);
  stp_parameter_type_t t = d.p_type;
  stp_parameter_description_destroy(&d);
  switch (t) {
    case STP_PARAMETER_TYPE_STRING_LIST:
      if (offers(v, key, value)) stp_set_string_parameter(v, key, value);
      else fprintf(stderr, "NOTE: %s=%s is not offered by this driver; using its default\n", key, value);
      break;
    case STP_PARAMETER_TYPE_INT: stp_set_int_parameter(v, key, atoi(value)); break;
    case STP_PARAMETER_TYPE_BOOLEAN: stp_set_boolean_parameter(v, key, atoi(value) != 0); break;
    case STP_PARAMETER_TYPE_DOUBLE: stp_set_float_parameter(v, key, atof(value)); break;
    default: fprintf(stderr, "NOTE: %s is not a parameter of this driver\n", key); break;
  }
}

/* Picks the named PageSize closest to w x h points (either orientation), or a custom size. */
static void set_paper(stp_vars_t *v, double w, double h) {
  stp_parameter_t d;
  const char *best = NULL;
  double best_err = 1e9;
  stp_describe_parameter(v, "PageSize", &d);
  if (d.p_type == STP_PARAMETER_TYPE_STRING_LIST && d.bounds.str) {
    int n = stp_string_list_count(d.bounds.str);
    for (int k = 0; k < n; k++) {
      const char *name = stp_string_list_param(d.bounds.str, k)->name;
      const stp_papersize_t *p = stp_describe_papersize(v, name);
      if (!p || p->width <= 0 || p->height <= 0) continue;
      double e = fabs(p->width - w) + fabs(p->height - h);
      if (e < best_err) { best_err = e; best = name; }
    }
  }
  if (best && best_err <= 6) {
    stp_set_string_parameter(v, "PageSize", best);
  } else {
    fprintf(stderr, "NOTE: no named paper of %.0fx%.0f pt; using a custom size\n", w, h);
    if (offers(v, "PageSize", "Custom")) stp_set_string_parameter(v, "PageSize", "Custom");
    stp_set_page_width(v, w);
    stp_set_page_height(v, h);
  }
  stp_parameter_description_destroy(&d);
}

static int has_color(const stp_vars_t *v) {
  return offers(v, "PrintingMode", "Color");
}

static int list_printers(void) {
  int n = stp_printer_model_count();
  for (int k = 0; k < n; k++) {
    const stp_printer_t *p = stp_get_printer_by_index(k);
    stp_vars_t *v = stp_vars_create();
    stp_set_printer_defaults(v, p);
    const char *id = stp_printer_get_device_id(p);
    printf("%s\t%s\t%s\t%s\t%s\t%d\t%d\n", stp_printer_get_driver(p), stp_printer_get_manufacturer(p),
           stp_printer_get_long_name(p), stp_printer_get_family(p), id ? id : "", has_color(v),
           offers(v, "Duplex", "DuplexNoTumble"));
    stp_vars_destroy(v);
  }
  return 0;
}

static int describe(const char *driver) {
  const stp_printer_t *p = stp_get_printer_by_driver(driver);
  if (!p) { fprintf(stderr, "ERROR: unknown driver %s\n", driver); return 2; }
  stp_vars_t *v = stp_vars_create();
  stp_set_printer_defaults(v, p);
  const char *names[] = { "PageSize", "MediaType", "InputSlot", "Resolution", "Quality", "Duplex", "PrintingMode", "InkType", NULL };
  for (int k = 0; names[k]; k++) {
    stp_parameter_t d;
    stp_describe_parameter(v, names[k], &d);
    if (d.p_type == STP_PARAMETER_TYPE_STRING_LIST && d.bounds.str) {
      printf("%s\t%s\t", names[k], d.deflt.str ? d.deflt.str : "");
      int n = stp_string_list_count(d.bounds.str);
      for (int i = 0; i < n; i++) printf("%s%s", i ? "|" : "", stp_string_list_param(d.bounds.str, i)->name);
      printf("\n");
    }
    stp_parameter_description_destroy(&d);
  }
  stp_vars_destroy(v);
  return 0;
}

int main(int argc, char **argv) {
  int a = 1;
  if (a < argc && strncmp(argv[a], "--data=", 7) == 0) { setenv("STP_DATA_PATH", argv[a] + 7, 1); a++; }
  if (a >= argc) {
    fprintf(stderr, "usage: pg_gutenprint --list | --describe DRIVER | DRIVER [Key=Value ...] < pgr > out\n");
    return 1;
  }
  stp_init();
  if (strcmp(argv[a], "--list") == 0) return list_printers();
  if (strcmp(argv[a], "--describe") == 0) return a + 1 < argc ? describe(argv[a + 1]) : 1;

  const stp_printer_t *printer = stp_get_printer_by_driver(argv[a]);
  if (!printer) { fprintf(stderr, "ERROR: unknown Gutenprint driver %s\n", argv[a]); return 2; }
  in = stdin;
  if (!pgr_read_start(in)) { fprintf(stderr, "ERROR: input is not a PrintGo raster stream\n"); return 3; }
  int r = pgr_next_page(in, &cur);
  if (r <= 0) { fprintf(stderr, "ERROR: no pages\n"); return 3; }

  stp_vars_t *v = stp_vars_create();
  stp_set_printer_defaults(v, printer);
  stp_set_outfunc(v, out_fn);
  stp_set_errfunc(v, err_fn);
  stp_set_outdata(v, stdout);
  stp_set_errdata(v, stderr);
  stp_set_string_parameter(v, "InputImageType", cur.channels == 3 ? "RGB" : "Whitescale");
  stp_set_string_parameter(v, "ChannelBitDepth", "8");

  double paper_w = cur.width * 72.0 / cur.xdpi, paper_h = cur.height * 72.0 / cur.ydpi;
  for (int k = a + 1; k < argc; k++) {
    char *eq = strchr(argv[k], '=');
    if (!eq) continue;
    *eq = 0;
    if (strcmp(argv[k], "PaperPt") == 0) sscanf(eq + 1, "%lfx%lf", &paper_w, &paper_h);
    else set_param(v, argv[k], eq + 1);
    *eq = '=';
  }
  set_paper(v, paper_w, paper_h);
  stp_set_printer_defaults_soft(v, printer);

  stp_dimension_t left, right, bottom, top;
  stp_get_imageable_area(v, &left, &right, &bottom, &top);
  if (right <= left || bottom <= top) { fprintf(stderr, "ERROR: the driver reports no printable area\n"); return 4; }
  stp_set_left(v, left);
  stp_set_top(v, top);
  stp_set_width(v, right - left);
  stp_set_height(v, bottom - top);
  stp_merge_printvars(v, stp_printer_get_defaults(printer));
  if (!stp_verify(v)) { fprintf(stderr, "ERROR: Gutenprint refused these settings\n"); return 5; }

  int page = 0, status = 0;
  stp_start_job(v, &image);
  while (r > 0) {
    free(rowbuf);
    rowbuf = (unsigned char *) malloc((size_t) cur.width * cur.channels);
    if (!rowbuf) { status = 6; break; }
    /* The page covers the whole sheet; the driver wants only its printable area. */
    crop_x = (int) lround(left * cur.xdpi / 72.0);
    crop_y = (int) lround(top * cur.ydpi / 72.0);
    img_w = (int) lround((right - left) * cur.xdpi / 72.0);
    img_h = (int) lround((bottom - top) * cur.ydpi / 72.0);
    if (img_w < 1) img_w = 1;
    if (img_h < 1) img_h = 1;
    next_row = 0;
    stp_set_int_parameter(v, "PageNumber", page);
    if (stp_print(v, &image) != 1 || read_failed) {
      fprintf(stderr, "ERROR: printing page %d failed\n", page + 1);
      status = 7;
      break;
    }
    while (next_row < cur.height) {
      if (!pgr_read_row(in, &cur, rowbuf)) { status = 3; break; }
      next_row++;
    }
    if (status) break;
    page++;
    r = pgr_next_page(in, &cur);
    if (r < 0) { fprintf(stderr, "ERROR: broken page data\n"); status = 3; }
  }
  stp_end_job(v, &image);
  fflush(stdout);
  stp_vars_destroy(v);
  free(rowbuf);
  if (!status) fprintf(stderr, "INFO: printed %d page(s)\n", page);
  return status;
}
