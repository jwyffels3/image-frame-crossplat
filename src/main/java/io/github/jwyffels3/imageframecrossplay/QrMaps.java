package io.github.jwyffels3.imageframecrossplay;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * QR-code map items. Each player has one reusable map (its id is saved in maps.yml, so map ids
 * don't pile up). The map is drawn by our renderer: the QR of the player's current link, or
 * "Link expired" once there is none, so a copy that escapes cleanup is useless.
 */
final class QrMaps {

  private final JavaPlugin plugin;
  private final NamespacedKey linkTag;
  private final File file;
  private final YamlConfiguration mapIds;
  private final Map<UUID, QrRenderer> renderers = new HashMap<>();

  QrMaps(JavaPlugin plugin) {
    this.plugin = plugin;
    this.linkTag = new NamespacedKey(plugin, "link");
    this.file = new File(plugin.getDataFolder(), "maps.yml");
    this.mapIds = YamlConfiguration.loadConfiguration(file);
    // Maps lose their renderers on restart: take every known map over again (it shows "Link expired")
    for (String player : mapIds.getKeys(false)) {
      MapView view = Bukkit.getMap(mapIds.getInt(player));
      if (view != null) {
        takeOver(UUID.fromString(player), view);
      }
    }
  }

  /** The upload link a QR map item belongs to, or {@code null} if it isn't one of ours. */
  UUID linkOf(ItemStack item) {
    if (item == null || item.getType() != Material.FILLED_MAP || !item.hasItemMeta()) {
      return null;
    }
    String id = item.getItemMeta().getPersistentDataContainer().get(linkTag, PersistentDataType.STRING);
    try {
      return id == null ? null : UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  boolean isQr(ItemStack item) {
    return linkOf(item) != null;
  }

  /** Draws the link's QR on the player's map and returns a map item showing it. */
  ItemStack createItem(Player player, ImageFrameBridge.PendingLink link) {
    MapView view = viewFor(player.getUniqueId());
    renderers.get(player.getUniqueId()).show(link.id(), QrPainter.paint(QrPainter.encode(link.url())));

    ItemStack item = new ItemStack(Material.FILLED_MAP);
    MapMeta meta = (MapMeta) item.getItemMeta();
    meta.setMapView(view);
    meta.displayName(Component.text("Upload QR: " + link.imageMap(), NamedTextColor.GOLD)
        .decoration(TextDecoration.ITALIC, false));
    meta.lore(List.of(
        Component.text("Scan with your phone camera to upload", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false),
        Component.text("Disappears when the link is used or expires", NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false)));
    meta.getPersistentDataContainer().set(linkTag, PersistentDataType.STRING, link.id().toString());
    item.setItemMeta(meta);
    return item;
  }

  /** Blanks the player's map ("Link expired") if it still shows this link. */
  void clear(UUID player, UUID linkId) {
    QrRenderer renderer = renderers.get(player);
    if (renderer != null) {
      renderer.clearIfShowing(linkId);
    }
  }

  /** Removes QR maps whose link is no longer active from the player's inventory and cursor. */
  void purge(Player player, Predicate<UUID> active) {
    purge(player.getInventory(), active);
    UUID cursorLink = linkOf(player.getItemOnCursor());
    if (cursorLink != null && !active.test(cursorLink)) {
      player.setItemOnCursor(null);
    }
  }

  /** Removes QR maps whose link is no longer active from an inventory. */
  void purge(Inventory inventory, Predicate<UUID> active) {
    ItemStack[] contents = inventory.getContents();
    for (int i = 0; i < contents.length; i++) {
      UUID link = linkOf(contents[i]);
      if (link != null && !active.test(link)) {
        inventory.setItem(i, null);
      }
    }
  }

  /** Whether the player already carries a QR map for this link. */
  boolean carries(Player player, UUID linkId) {
    for (ItemStack item : player.getInventory().getContents()) {
      if (linkId.equals(linkOf(item))) {
        return true;
      }
    }
    return linkId.equals(linkOf(player.getItemOnCursor()));
  }

  private MapView viewFor(UUID player) {
    String key = player.toString();
    MapView view = mapIds.contains(key) ? Bukkit.getMap(mapIds.getInt(key)) : null;
    if (view == null) {
      view = Bukkit.createMap(Bukkit.getWorlds().getFirst());
      mapIds.set(key, view.getId());
      try {
        mapIds.save(file);
      } catch (IOException e) {
        plugin.getLogger().log(Level.WARNING, "Could not save maps.yml", e);
      }
    }
    if (!renderers.containsKey(player)) {
      takeOver(player, view);
    }
    return view;
  }

  private void takeOver(UUID player, MapView view) {
    for (MapRenderer renderer : new ArrayList<>(view.getRenderers())) {
      view.removeRenderer(renderer);
    }
    view.setScale(MapView.Scale.CLOSEST);
    view.setTrackingPosition(false);
    view.setUnlimitedTracking(false);
    QrRenderer renderer = new QrRenderer();
    view.addRenderer(renderer);
    renderers.put(player, renderer);
  }

  /** Draws a QR code, or "Link expired". Redraws only when the content changes. */
  private static final class QrRenderer extends MapRenderer {

    private UUID linkId;
    private boolean[] dark;
    private int version;
    private int drawnVersion = -1;

    QrRenderer() {
      super(false);
    }

    void show(UUID linkId, boolean[] dark) {
      this.linkId = linkId;
      this.dark = dark;
      version++;
    }

    void clearIfShowing(UUID linkId) {
      if (linkId.equals(this.linkId)) {
        this.linkId = null;
        this.dark = null;
        version++;
      }
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
      if (drawnVersion == version) {
        return;
      }
      drawnVersion = version;
      int size = QrPainter.MAP_SIZE;
      for (int y = 0; y < size; y++) {
        for (int x = 0; x < size; x++) {
          boolean black = dark != null && dark[y * size + x];
          canvas.setPixelColor(x, y, black ? Color.BLACK : Color.WHITE);
        }
      }
      if (dark == null) {
        String text = "Link expired";
        canvas.drawText((size - MinecraftFont.Font.getWidth(text)) / 2, size / 2 - 4,
            MinecraftFont.Font, text);
      }
    }
  }
}
