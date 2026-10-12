/*
 * pg_cupsraster: turns a PrintGo raster stream (pgr.h) into CUPS raster for a printer's PPD file and
 * runs a CUPS raster filter (HPLIP's hpcups, SpliX's rastertoqpdl) on it. The filter writes the
 * printer's own data to stdout.
 *
 * Copyright (C) 2026 the PrintGo authors. SPDX-License-Identifier: GPL-3.0-or-later
 * Uses libcups (Apache-2.0).
 *
 *   pg_cupsraster --query PPD [OPTIONS]        prints the page geometry the PPD asks for
 *   pg_cupsraster FILTER PPD [OPTIONS] [TITLE] < pgr > printer-data
 *
 * OPTIONS is one CUPS option string ("Duplex=DuplexNoTumble MediaType=Plain"). PrintGo also understands:
 *   PaperPt=WxH                 choose the PPD paper size closest to W x H points
 *   PGColor=color|gray          choose a ColorModel of that kind
 *   PGQuality=draft|normal|high choose a matching OutputMode / Resolution choice when the PPD has one
 */
#include <cups/cups.h>
#include <cups/ppd.h>
#include <cups/raster.h>
#include <errno.h>
#include <math.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/wait.h>
#include <unistd.h>
#include "pgr.h"

typedef struct {
  int num;
  cups_option_t *opts;
} optset;

static void add_opt(optset *o, const char *name, const char *value) {
  o->num = cupsAddOption(name, value, o->num, &o->opts);
}

/* Kind of colour a ColorModel choice produces, from its PostScript code. 1 = colour, 0 = grey, -1 = unknown. */
static int choice_is_color(const ppd_choice_t *c) {
  const char *p = c->code ? strstr(c->code, "cupsColorSpace") : NULL;
  if (!p) {
    if (!strcasecmp(c->choice, "Gray") || !strcasecmp(c->choice, "Grayscale") || !strcasecmp(c->choice, "KGray") ||
        !strcasecmp(c->choice, "Mono") || !strcasecmp(c->choice, "Black")) return 0;
    if (!strcasecmp(c->choice, "RGB") || !strcasecmp(c->choice, "CMYK") || !strcasecmp(c->choice, "Color")) return 1;
    return -1;
  }
  int cs = atoi(p + strlen("cupsColorSpace"));
  switch (cs) {
    case CUPS_CSPACE_W: case CUPS_CSPACE_K: case CUPS_CSPACE_SW: return 0;
    default: return 1;
  }
}

static void pick_color(ppd_file_t *ppd, optset *o, int want_color) {
  ppd_option_t *opt = ppdFindOption(ppd, "ColorModel");
  if (!opt) return;
  for (int i = 0; i < opt->num_choices; i++) {
    if (choice_is_color(&opt->choices[i]) == want_color) {
      add_opt(o, "ColorModel", opt->choices[i].choice);
      return;
    }
  }
}

static int name_has(const char *s, const char *const *words) {
  for (int i = 0; words[i]; i++)
    if (strcasestr(s, words[i])) return 1;
  return 0;
}

static void pick_quality(ppd_file_t *ppd, optset *o, const char *q) {
  static const char *const draft[] = { "Draft", "Fast", "Economy", NULL };
  static const char *const high[] = { "Best", "High", "Photo", "Fine", NULL };
  static const char *const normal[] = { "Normal", "Standard", NULL };
  const char *const *words = !strcmp(q, "draft") ? draft : !strcmp(q, "high") ? high : normal;
  const char *names[] = { "OutputMode", "PrintQuality", "Quality", NULL };
  for (int n = 0; names[n]; n++) {
    ppd_option_t *opt = ppdFindOption(ppd, names[n]);
    if (!opt) continue;
    for (int i = 0; i < opt->num_choices; i++) {
      if (name_has(opt->choices[i].choice, words) || name_has(opt->choices[i].text, words)) {
        add_opt(o, names[n], opt->choices[i].choice);
        return;
      }
    }
  }
}

static void pick_paper(ppd_file_t *ppd, optset *o, double w, double h) {
  const char *best = NULL;
  double best_err = 1e9;
  for (int i = 0; i < ppd->num_sizes; i++) {
    ppd_size_t *s = ppd->sizes + i;
    if (!strcmp(s->name, "Custom")) continue;
    double e = fabs(s->width - w) + fabs(s->length - h);
    /* Prefer the plain size over its borderless or ".Fullbleed" variants. */
    if (strchr(s->name, '.') || strcasestr(s->name, "borderless")) e += 2;
    if (e < best_err) { best_err = e; best = s->name; }
  }
  if (best && best_err <= 8) {
    add_opt(o, "PageSize", best);
  } else if (ppd->variable_sizes) {
    char buf[64];
    snprintf(buf, sizeof buf, "Custom.%.0fx%.0f", w, h);
    add_opt(o, "PageSize", buf);
  }
}

/* Applies PrintGo's own options and returns the option set to mark in the PPD. */
static optset build_options(ppd_file_t *ppd, const char *optstr) {
  optset in = { 0, NULL }, out = { 0, NULL };
  in.num = cupsParseOptions(optstr ? optstr : "", 0, &in.opts);
  for (int i = 0; i < in.num; i++) {
    const char *n = in.opts[i].name, *v = in.opts[i].value;
    if (!strcmp(n, "PaperPt")) {
      double w = 0, h = 0;
      if (sscanf(v, "%lfx%lf", &w, &h) == 2) pick_paper(ppd, &out, w, h);
    } else if (!strcmp(n, "PGColor")) {
      pick_color(ppd, &out, !strcmp(v, "color"));
    } else if (!strcmp(n, "PGQuality")) {
      pick_quality(ppd, &out, v);
    } else {
      add_opt(&out, n, v);
      /* Two-sided needs the duplex unit to count as installed in HP's PPDs. */
      if (!strcmp(n, "Duplex") && strcmp(v, "None") && ppdFindOption(ppd, "OptionDuplex"))
        add_opt(&out, "OptionDuplex", "True");
    }
  }
  cupsFreeOptions(in.num, in.opts);
  return out;
}

/* ---- pixel conversion ------------------------------------------------------------------------ */


/* Floyd-Steinberg on one channel; ink[x] is 0 (none) .. 255 (full). Writes packed bits, MSB first. */
static void dither_row(const unsigned char *ink, int w, unsigned char *bits, float **cur, float **next) {
  float *c = *cur, *n = *next;
  memset(n, 0, sizeof(float) * (w + 2));
  memset(bits, 0, (size_t) (w + 7) / 8);
  for (int x = 0; x < w; x++) {
    float v = ink[x] + c[x + 1];
    int on = v >= 128.0f;
    float e = v - (on ? 255.0f : 0.0f);
    if (on) bits[x >> 3] |= (unsigned char) (0x80 >> (x & 7));
    c[x + 2] += e * 7 / 16;
    n[x] += e * 3 / 16;
    n[x + 1] += e * 5 / 16;
    n[x + 2] += e * 1 / 16;
  }
  *cur = n;
  *next = c;
}

static inline unsigned char lum(const unsigned char *p, int ch) {
  if (ch == 1) return p[0];
  return (unsigned char) ((77 * p[0] + 150 * p[1] + 29 * p[2]) >> 8);
}

typedef struct {
  float *cur[4], *next[4];
  unsigned char *ink[4];
} dither_state;

static int dither_init(dither_state *d, int w) {
  for (int k = 0; k < 4; k++) {
    d->cur[k] = calloc((size_t) w + 2, sizeof(float));
    d->next[k] = calloc((size_t) w + 2, sizeof(float));
    d->ink[k] = malloc((size_t) w);
    if (!d->cur[k] || !d->next[k] || !d->ink[k]) return 0;
  }
  return 1;
}

static void dither_free(dither_state *d) {
  for (int k = 0; k < 4; k++) { free(d->cur[k]); free(d->next[k]); free(d->ink[k]); }
}

/* Converts one row of sampled RGB/grey pixels (ch bytes each) into the header's format. */
static int convert_row(const cups_page_header2_t *h, const unsigned char *src, int ch, unsigned char *dst,
                       dither_state *d) {
  int w = (int) h->cupsWidth;
  int bits = (int) h->cupsBitsPerColor;
  memset(dst, 0, h->cupsBytesPerLine);
  switch (h->cupsColorSpace) {
    case CUPS_CSPACE_W:
    case CUPS_CSPACE_SW:
    case CUPS_CSPACE_K:
      for (int x = 0; x < w; x++) {
        unsigned char l = lum(src + x * ch, ch);
        d->ink[0][x] = (unsigned char) (255 - l);
      }
      if (bits == 8) {
        for (int x = 0; x < w; x++) dst[x] = h->cupsColorSpace == CUPS_CSPACE_K ? d->ink[0][x] : (unsigned char) (255 - d->ink[0][x]);
      } else if (bits == 1) {
        dither_row(d->ink[0], w, dst, &d->cur[0], &d->next[0]);
        if (h->cupsColorSpace != CUPS_CSPACE_K)
          for (unsigned b = 0; b < h->cupsBytesPerLine; b++) dst[b] = (unsigned char) ~dst[b];
      } else return 0;
      return 1;
    case CUPS_CSPACE_RGB:
    case CUPS_CSPACE_SRGB:
    case CUPS_CSPACE_ADOBERGB:
      if (bits != 8 || h->cupsColorOrder != CUPS_ORDER_CHUNKED) return 0;
      for (int x = 0; x < w; x++) {
        const unsigned char *p = src + x * ch;
        dst[3 * x] = p[0];
        dst[3 * x + 1] = ch == 3 ? p[1] : p[0];
        dst[3 * x + 2] = ch == 3 ? p[2] : p[0];
      }
      return 1;
    case CUPS_CSPACE_RGBW:
      if (bits != 8 || h->cupsColorOrder != CUPS_ORDER_CHUNKED) return 0;
      for (int x = 0; x < w; x++) {
        const unsigned char *p = src + x * ch;
        unsigned char r = p[0], g = ch == 3 ? p[1] : p[0], b = ch == 3 ? p[2] : p[0];
        /* Near-black goes to the black ink (sharp text); everything else to the colour inks. */
        int black = r < 24 && g < 24 && b < 24;
        dst[4 * x] = black ? 0 : r;
        dst[4 * x + 1] = black ? 0 : g;
        dst[4 * x + 2] = black ? 0 : b;
        dst[4 * x + 3] = black ? 0 : 255;
      }
      return 1;
    case CUPS_CSPACE_CMY:
    case CUPS_CSPACE_CMYK:
    case CUPS_CSPACE_KCMY: {
      int n = h->cupsColorSpace == CUPS_CSPACE_CMY ? 3 : 4;
      for (int x = 0; x < w; x++) {
        const unsigned char *p = src + x * ch;
        int c = 255 - p[0], m = 255 - (ch == 3 ? p[1] : p[0]), y = 255 - (ch == 3 ? p[2] : p[0]);
        int k = 0;
        if (n == 4) {
          k = c < m ? (c < y ? c : y) : (m < y ? m : y);
          c -= k; m -= k; y -= k;
        }
        int v[4];
        if (h->cupsColorSpace == CUPS_CSPACE_KCMY) { v[0] = k; v[1] = c; v[2] = m; v[3] = y; }
        else { v[0] = c; v[1] = m; v[2] = y; v[3] = k; }
        for (int i = 0; i < n; i++) d->ink[i][x] = (unsigned char) v[i];
      }
      if (h->cupsColorOrder == CUPS_ORDER_CHUNKED && bits == 8) {
        for (int x = 0; x < w; x++)
          for (int i = 0; i < n; i++) dst[n * x + i] = d->ink[i][x];
        return 1;
      }
      if (h->cupsColorOrder == CUPS_ORDER_BANDED && (bits == 1 || bits == 8)) {
        size_t band = h->cupsBytesPerLine / (unsigned) n;
        for (int i = 0; i < n; i++) {
          if (bits == 8) memcpy(dst + band * i, d->ink[i], (size_t) w);
          else dither_row(d->ink[i], w, dst + band * i, &d->cur[i], &d->next[i]);
        }
        return 1;
      }
      if (h->cupsColorOrder == CUPS_ORDER_CHUNKED && bits == 1) {
        unsigned char *tmp[4];
        size_t pb = (size_t) (w + 7) / 8;
        for (int i = 0; i < n; i++) {
          tmp[i] = calloc(pb, 1);
          if (!tmp[i]) return 0;
          dither_row(d->ink[i], w, tmp[i], &d->cur[i], &d->next[i]);
        }
        for (int x = 0; x < w; x++)
          for (int i = 0; i < n; i++) {
            int bit = (tmp[i][x >> 3] >> (7 - (x & 7))) & 1;
            int pos = x * n + i;
            if (bit) dst[pos >> 3] |= (unsigned char) (0x80 >> (pos & 7));
          }
        for (int i = 0; i < n; i++) free(tmp[i]);
        return 1;
      }
      return 0;
    }
    default:
      return 0;
  }
}

/* ---- main --------------------------------------------------------------------------------------- */

static int header_for(ppd_file_t *ppd, optset *o, cups_page_header2_t *h) {
  memset(h, 0, sizeof(*h));
  if (cupsRasterInterpretPPD(h, ppd, o->num, o->opts, NULL)) {
    fprintf(stderr, "ERROR: the printer description gave no usable page settings: %s\n", cupsRasterErrorString());
    return 0;
  }
  h->NumCopies = 1;
  return 1;
}

static ppd_file_t *open_ppd(const char *path, const char *optstr, optset *o) {
  ppd_file_t *ppd = ppdOpenFile(path);
  if (!ppd) {
    int line;
    ppd_status_t st = ppdLastError(&line);
    fprintf(stderr, "ERROR: cannot read printer description %s: %s (line %d)\n", path, ppdErrorString(st), line);
    return NULL;
  }
  ppdMarkDefaults(ppd);
  *o = build_options(ppd, optstr);
  cupsMarkOptions(ppd, o->num, o->opts);
  return ppd;
}

/*
 * How the back side of a two-sided sheet must be turned before it goes to the driver, from the PPD's
 * cupsBackSide (or the older cupsFlipDuplex), the same way CUPS' own raster filters read it:
 * "none", "rotate" (180 degrees), "flipx" (mirror left-right) or "flipy" (mirror top-bottom).
 */
static const char *backside_for(ppd_file_t *ppd, const cups_page_header2_t *h) {
  if (!h->Duplex) return "none";
  ppd_attr_t *a = ppdFindAttr(ppd, "cupsBackSide", NULL);
  const char *v = a && a->value ? a->value : NULL;
  if (!v) {
    ppd_attr_t *f = ppdFindAttr(ppd, "cupsFlipDuplex", NULL);
    if (f && f->value && !strcasecmp(f->value, "true")) v = "Rotated";
  }
  if (!v) return "none";
  if (!strcasecmp(v, "ManualTumble")) return h->Tumble ? "rotate" : "none";
  if (!strcasecmp(v, "Rotated")) return h->Tumble ? "none" : "rotate";
  if (!strcasecmp(v, "Flipped")) return h->Tumble ? "flipx" : "flipy";
  return "none";
}

static int query(const char *ppd_path, const char *optstr) {
  optset o;
  ppd_file_t *ppd = open_ppd(ppd_path, optstr, &o);
  if (!ppd) return 2;
  cups_page_header2_t h;
  if (!header_for(ppd, &o, &h)) return 3;
  ppd_choice_t *ps = ppdFindMarkedChoice(ppd, "PageSize");
  printf("hwres=%ux%u\nwidth=%u\nheight=%u\ncolorspace=%u\nbits=%u\norder=%u\npagesize=%ux%u\nmargins=%ux%u\n"
         "bbox=%u,%u,%u,%u\nduplex=%u\ntumble=%u\npagesize_name=%s\noptions=",
         h.HWResolution[0], h.HWResolution[1], h.cupsWidth, h.cupsHeight, h.cupsColorSpace, h.cupsBitsPerColor,
         h.cupsColorOrder, h.PageSize[0], h.PageSize[1], h.Margins[0], h.Margins[1], h.ImagingBoundingBox[0],
         h.ImagingBoundingBox[1], h.ImagingBoundingBox[2], h.ImagingBoundingBox[3], h.Duplex, h.Tumble,
         ps ? ps->choice : "");
  for (int i = 0; i < o.num; i++) printf("%s%s=%s", i ? " " : "", o.opts[i].name, o.opts[i].value);
  printf("\nbackside=%s\n", backside_for(ppd, &h));
  return 0;
}

int main(int argc, char **argv) {
  signal(SIGPIPE, SIG_IGN);
  if (argc >= 3 && !strcmp(argv[1], "--query")) return query(argv[2], argc > 3 ? argv[3] : "");
  if (argc < 3) {
    fprintf(stderr, "usage: pg_cupsraster --query PPD [OPTIONS] | FILTER PPD [OPTIONS] [TITLE] < pgr > out\n");
    return 1;
  }
  const char *filter = argv[1], *ppd_path = argv[2];
  const char *optstr = argc > 3 ? argv[3] : "";
  const char *title = argc > 4 ? argv[4] : "PrintGo";

  optset o;
  ppd_file_t *ppd = open_ppd(ppd_path, optstr, &o);
  if (!ppd) return 2;

  if (!pgr_read_start(stdin)) { fprintf(stderr, "ERROR: input is not a PrintGo raster stream\n"); return 3; }
  pgr_page pg;
  int r = pgr_next_page(stdin, &pg);
  if (r <= 0) { fprintf(stderr, "ERROR: no pages\n"); return 3; }

  /* The filter gets the marked options too, as CUPS would pass them. */
  char *fopts = NULL;
  {
    size_t len = 1;
    for (int i = 0; i < o.num; i++) len += strlen(o.opts[i].name) + strlen(o.opts[i].value) + 2;
    fopts = calloc(len, 1);
    for (int i = 0; i < o.num; i++) {
      if (i) strcat(fopts, " ");
      strcat(fopts, o.opts[i].name);
      strcat(fopts, "=");
      strcat(fopts, o.opts[i].value);
    }
  }

  int fd[2];
  if (pipe(fd)) { perror("pipe"); return 4; }
  pid_t pid = fork();
  if (pid < 0) { perror("fork"); return 4; }
  if (pid == 0) {
    dup2(fd[0], 0);
    close(fd[0]);
    close(fd[1]);
    setenv("PPD", ppd_path, 1);
    const char *base = strrchr(filter, '/');
    char *fargv[] = { (char *) (base ? base + 1 : filter), "1", "printgo", (char *) title, "1", fopts, NULL };
    execv(filter, fargv);
    fprintf(stderr, "ERROR: cannot start the driver %s: %s\n", filter, strerror(errno));
    _exit(127);
  }
  close(fd[0]);

  cups_raster_t *ras = cupsRasterOpen(fd[1], CUPS_RASTER_WRITE);
  int status = 0, pages = 0;
  unsigned char *srow = NULL, *line = NULL, *sampled = NULL;
  dither_state ds;
  memset(&ds, 0, sizeof ds);
  int dither_w = -1;

  while (r > 0 && !status) {
    cups_page_header2_t h;
    if (!header_for(ppd, &o, &h)) { status = 5; break; }
    if (h.cupsColorOrder == CUPS_ORDER_PLANAR) { fprintf(stderr, "ERROR: planar raster is not supported\n"); status = 5; break; }
    free(srow); free(line); free(sampled);
    srow = malloc((size_t) pg.width * pg.channels);
    line = malloc(h.cupsBytesPerLine);
    sampled = malloc((size_t) h.cupsWidth * pg.channels);
    if (!srow || !line || !sampled) { status = 6; break; }
    if (dither_w != (int) h.cupsWidth) {
      dither_free(&ds);
      memset(&ds, 0, sizeof ds);
      if (!dither_init(&ds, (int) h.cupsWidth)) { status = 6; break; }
      dither_w = (int) h.cupsWidth;
    }
    if (!cupsRasterWriteHeader2(ras, &h)) { fprintf(stderr, "ERROR: the driver stopped reading\n"); status = 7; break; }

    /* Our page covers the whole sheet; CUPS raster covers the imageable box at HWResolution. */
    double sx = (double) pg.xdpi / h.HWResolution[0], sy = (double) pg.ydpi / h.HWResolution[1];
    double off_x = h.cupsImagingBBox[0] * pg.xdpi / 72.0;
    double off_y = (h.cupsPageSize[1] - h.cupsImagingBBox[3]) * pg.ydpi / 72.0;
    uint32_t have = 0; /* rows of the source read so far */
    int have_row = 0;
    for (unsigned ty = 0; ty < h.cupsHeight && !status; ty++) {
      long src_y = lround(off_y + ty * sy);
      if (src_y >= 0 && src_y < (long) pg.height) {
        while ((long) have <= src_y) {
          if (!pgr_read_row(stdin, &pg, srow)) { status = 3; break; }
          have++;
          have_row = 1;
        }
      }
      if (status) break;
      int in_page = have_row && src_y >= 0 && src_y < (long) pg.height;
      for (unsigned tx = 0; tx < h.cupsWidth; tx++) {
        long src_x = lround(off_x + tx * sx);
        unsigned char *dp = sampled + (size_t) tx * pg.channels;
        if (in_page && src_x >= 0 && src_x < (long) pg.width) memcpy(dp, srow + (size_t) src_x * pg.channels, pg.channels);
        else memset(dp, 0xff, pg.channels);
      }
      if (!convert_row(&h, sampled, (int) pg.channels, line, &ds)) {
        fprintf(stderr, "ERROR: colour space %u with %u bits is not supported\n", h.cupsColorSpace, h.cupsBitsPerColor);
        status = 5;
        break;
      }
      if (cupsRasterWritePixels(ras, line, h.cupsBytesPerLine) != h.cupsBytesPerLine) {
        fprintf(stderr, "ERROR: the driver stopped reading\n");
        status = 7;
      }
    }
    while (!status && have < pg.height) {
      if (!pgr_read_row(stdin, &pg, srow)) { status = 3; break; }
      have++;
    }
    if (status) break;
    pages++;
    r = pgr_next_page(stdin, &pg);
    if (r < 0) { fprintf(stderr, "ERROR: broken page data\n"); status = 3; }
  }
  cupsRasterClose(ras);
  close(fd[1]);
  int wst = 0;
  waitpid(pid, &wst, 0);
  int fcode = WIFEXITED(wst) ? WEXITSTATUS(wst) : 128;
  if (!status && fcode) {
    fprintf(stderr, "ERROR: the driver ended with code %d\n", fcode);
    status = 10 + (fcode & 0x3f);
  }
  if (!status) fprintf(stderr, "INFO: printed %d page(s)\n", pages);
  dither_free(&ds);
  free(srow); free(line); free(sampled); free(fopts);
  ppdClose(ppd);
  return status;
}
