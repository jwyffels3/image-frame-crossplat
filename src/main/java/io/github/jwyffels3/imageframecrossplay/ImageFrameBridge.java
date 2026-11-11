package io.github.jwyffels3.imageframecrossplay;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Reads ImageFrame's pending upload links.
 *
 * <p>ImageFrame has no API for this, so its internals are read by reflection (by name, read-only):
 * {@code ImageFrame.imageUploadManager} (public static) holds a private
 * {@code Map<UUID, PendingUpload> pendingUploads}; {@code PendingUpload} has public getters.
 * Checked against ImageFrame 2026.1.5.0. If any of it is missing (e.g. after an ImageFrame update),
 * one warning is logged and {@link #snapshot()} returns {@code null}; ImageFrame keeps working.
 */
final class ImageFrameBridge {

  /** One pending upload link. {@code done} completes when the image is uploaded or the link expires. */
  record PendingLink(UUID id, UUID creator, String url, long expire, String imageMap, int width,
      int height, Future<?> done) {
  }

  private final Logger logger;
  private boolean failed;
  private boolean hooked;

  private Field managerField;
  private Field displayUrlField;
  private Field pendingField;
  private Method getId;
  private Method getCreator;
  private Method getExpire;
  private Method getImageMap;
  private Method getWidth;
  private Method getHeight;
  private Method getFile;
  private Method getUrl;

  ImageFrameBridge(Logger logger) {
    this.logger = logger;
  }

  /**
   * Returns ImageFrame's current pending upload links, or {@code null} if ImageFrame is missing,
   * not ready, or its internals don't match.
   */
  List<PendingLink> snapshot() {
    if (failed) {
      return null;
    }
    try {
      if (managerField == null) {
        Plugin imageFrame = Bukkit.getPluginManager().getPlugin("ImageFrame");
        if (imageFrame == null || !imageFrame.isEnabled()) {
          return null;
        }
        Class<?> main = Class.forName("com.loohp.imageframe.ImageFrame", true,
            imageFrame.getClass().getClassLoader());
        managerField = main.getField("imageUploadManager");
        displayUrlField = main.getField("uploadServiceDisplayURL");
      }
      Object manager = managerField.get(null);
      if (manager == null) {
        return null;
      }
      if (pendingField == null) {
        pendingField = manager.getClass().getDeclaredField("pendingUploads");
        pendingField.setAccessible(true);
      }
      Map<?, ?> pending = (Map<?, ?>) pendingField.get(manager);
      String displayUrl = (String) displayUrlField.get(null);

      List<PendingLink> links = new ArrayList<>();
      for (Object upload : pending.values().toArray()) {
        if (getId == null) {
          resolveMethods(upload.getClass());
        }
        links.add(new PendingLink(
            (UUID) getId.invoke(upload),
            (UUID) getCreator.invoke(upload),
            (String) getUrl.invoke(upload, displayUrl),
            (long) getExpire.invoke(upload),
            (String) getImageMap.invoke(upload),
            (int) getWidth.invoke(upload),
            (int) getHeight.invoke(upload),
            (Future<?>) getFile.invoke(upload)));
      }
      if (!hooked) {
        hooked = true;
        logger.info("Hooked ImageFrame uploads");
      }
      return links;
    } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
      failed = true;
      logger.log(Level.WARNING, "Could not read ImageFrame's upload links (has ImageFrame changed?)."
          + " Pop-ups are off; ImageFrame's chat link still works.", e);
      return null;
    }
  }

  private void resolveMethods(Class<?> type) throws NoSuchMethodException {
    getId = type.getMethod("getId");
    getCreator = type.getMethod("getCreator");
    getExpire = type.getMethod("getExpire");
    getImageMap = type.getMethod("getImageMap");
    getWidth = type.getMethod("getWidth");
    getHeight = type.getMethod("getHeight");
    getFile = type.getMethod("getFile");
    getUrl = type.getMethod("getUrl", String.class);
  }
}
