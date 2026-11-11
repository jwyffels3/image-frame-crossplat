package io.github.jwyffels3.imageframecrossplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.nayuki.qrcodegen.QrCode;
import org.junit.jupiter.api.Test;

class QrPainterTest {

  /** A link shaped like the ones ImageFrame makes. */
  private static final String URL =
      "http://minecraft.example.org:8517?id=3bdb3f11-e663-4735-a973-2e395d44ec62";

  @Test
  void ourLinkGetsThreePixelModules() {
    QrCode qr = QrPainter.encode(URL);
    assertEquals(3, QrPainter.scale(qr), "QR size " + qr.size);
  }

  @Test
  void everyModuleIsDrawnWithAQuietZone() {
    QrCode qr = QrPainter.encode(URL);
    int scale = QrPainter.scale(qr);
    int offset = (QrPainter.MAP_SIZE - qr.size * scale) / 2;
    assertTrue(offset >= QrPainter.QUIET_ZONE * scale, "quiet zone " + offset + " px");

    boolean[] dark = QrPainter.paint(qr);
    for (int y = 0; y < QrPainter.MAP_SIZE; y++) {
      for (int x = 0; x < QrPainter.MAP_SIZE; x++) {
        int mx = Math.floorDiv(x - offset, scale);
        int my = Math.floorDiv(y - offset, scale);
        boolean expected = mx >= 0 && my >= 0 && mx < qr.size && my < qr.size
            && qr.getModule(mx, my);
        assertEquals(expected, dark[y * QrPainter.MAP_SIZE + x], "pixel " + x + "," + y);
      }
    }
  }

  @Test
  void longerLinksStillFit() {
    QrCode qr = QrPainter.encode(URL + "&padding=" + "x".repeat(60));
    assertTrue(QrPainter.scale(qr) >= 2);
    assertFalse(QrPainter.paint(qr)[0], "corner stays white");
  }
}
