# Real-device test checklist

Printing can only be fully verified with a real phone and printer. Tick these off on the debug APK:

**Discovery**
- [ ] Printer on the same Wi-Fi appears under Printers within ~10 s
- [ ] "Add printer manually" with the printer's IP works
- [ ] Green connected marker + printer name on the home screen

**Printing (Wi-Fi printer)**
- [ ] Photo: 1 copy, colour, A4
- [ ] PDF, several pages, page range "1-2"
- [ ] 2 copies; grayscale; two-sided (if printer supports it)
- [ ] Cancel a job while it is sending; the job shows Cancelled
- [ ] Switch the printer off, print, see a friendly error, switch on, Retry works
- [ ] Remove paper (or open the cover) and see the reason on the job

**Other outputs**
- [ ] Save as PDF writes a file to Downloads/PrintGo and Open works
- [ ] "Print with system dialog" on a job opens Android's print dialog
- [ ] Bluetooth / USB thermal printer (only if you own one)

**App**
- [ ] Open a PDF from the Files app → PrintGo opens the preview
- [ ] Share an image from the gallery → PrintGo opens the preview
- [ ] Notification appears when a job finishes (Android 13+: permission was granted)
- [ ] History: search, filter, Print again, export CSV
- [ ] Settings: change history period, replay the quick tour
