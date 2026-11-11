package io.github.jwyffels3.imageframecrossplay;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shows ImageFrame upload links in a pop-up (Java dialog, Bedrock form via Geyser) with an
 * optional QR-code map, because Bedrock players can't click or copy links in chat.
 */
public final class ImageFrameCrossplayPlugin extends JavaPlugin {

  /** How often ImageFrame's pending links are checked (ticks). */
  private static final long POLL_TICKS = 5;

  @Override
  public void onEnable() {
    QrMaps maps = new QrMaps(this);
    UploadWatcher watcher = new UploadWatcher(this, new ImageFrameBridge(getLogger()), maps);
    getServer().getPluginManager().registerEvents(new QrItemGuard(maps, watcher), this);
    getServer().getScheduler().runTaskTimer(this, watcher, POLL_TICKS, POLL_TICKS);
  }
}
