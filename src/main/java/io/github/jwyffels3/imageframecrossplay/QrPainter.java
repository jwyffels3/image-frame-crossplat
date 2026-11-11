package io.github.jwyffels3.imageframecrossplay;

import io.nayuki.qrcodegen.QrCode;

/**
 * Lays a QR code out on a 128x128 map: whole pixels per module, centred, with at least the
 * 4-module white border ("quiet zone") scanners need. No Bukkit code, so it can be unit-tested.
 */
final class QrPainter {

  static final int MAP_SIZE = 128;
  static final int QUIET_ZONE = 4;

  private QrPainter() {
  }

  /** Encodes text with the lowest error correction, giving the fewest and therefore biggest modules. */
  static QrCode encode(String text) {
    return QrCode.encodeText(text, QrCode.Ecc.LOW);
  }

  /** Pixels per module that fit on the map with the quiet zone; 0 if the code doesn't fit. */
  static int scale(QrCode qr) {
    return MAP_SIZE / (qr.size + 2 * QUIET_ZONE);
  }

  /**
   * Returns which map pixels are dark, indexed {@code [y * 128 + x]}.
   *
   * @throws IllegalArgumentException if the code is too big for one map
   */
  static boolean[] paint(QrCode qr) {
    int scale = scale(qr);
    if (scale < 1) {
      throw new IllegalArgumentException("QR code too big for a map: " + qr.size + " modules");
    }
    int offset = (MAP_SIZE - qr.size * scale) / 2;
    boolean[] dark = new boolean[MAP_SIZE * MAP_SIZE];
    for (int y = 0; y < qr.size * scale; y++) {
      for (int x = 0; x < qr.size * scale; x++) {
        dark[(offset + y) * MAP_SIZE + offset + x] = qr.getModule(x / scale, y / scale);
      }
    }
    return dark;
  }
}
