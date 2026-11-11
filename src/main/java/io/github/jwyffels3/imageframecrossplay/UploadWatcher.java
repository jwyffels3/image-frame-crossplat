package io.github.jwyffels3.imageframecrossplay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

/**
 * Polls ImageFrame's pending upload links (main thread, every few ticks). A new link opens the
 * pop-up for its player; a link that was used, expired or replaced is finished: pop-up closed,
 * QR map removed and blanked.
 */
final class UploadWatcher implements Runnable {

  private final Plugin plugin;
  private final ImageFrameBridge bridge;
  private final QrMaps maps;
  /** Links we showed a pop-up for, by link id. */
  private final Map<UUID, ImageFrameBridge.PendingLink> active = new HashMap<>();
  /** QR maps dropped on the ground because the inventory was full, by link id. */
  private final Map<UUID, UUID> droppedMaps = new HashMap<>();

  UploadWatcher(Plugin plugin, ImageFrameBridge bridge, QrMaps maps) {
    this.plugin = plugin;
    this.bridge = bridge;
    this.maps = maps;
  }

  boolean isActive(UUID linkId) {
    return active.containsKey(linkId);
  }

  @Override
  public void run() {
    List<ImageFrameBridge.PendingLink> links = bridge.snapshot();
    if (links == null) {
      return;
    }
    long now = System.currentTimeMillis();
    Set<UUID> present = new HashSet<>();
    for (ImageFrameBridge.PendingLink link : links) {
      present.add(link.id());
    }
    // Finish first, so a link that replaced an older one isn't closed by the old one's cleanup
    for (ImageFrameBridge.PendingLink link : new ArrayList<>(active.values())) {
      if (!present.contains(link.id()) || ended(link, now)) {
        finish(link);
      }
    }
    for (ImageFrameBridge.PendingLink link : links) {
      if (active.containsKey(link.id()) || ended(link, now)) {
        continue;
      }
      Player player = Bukkit.getPlayer(link.creator());
      if (player == null) {
        continue; // made from the console, or the player left
      }
      active.put(link.id(), link);
      UUID playerId = player.getUniqueId();
      LinkDialog.show(plugin, player, link, () -> giveQr(playerId, link.id()));
    }
  }

  private static boolean ended(ImageFrameBridge.PendingLink link, long now) {
    return link.done().isDone() || now >= link.expire();
  }

  /** "Show QR code": offhand if empty, else a free slot, else dropped at the player's feet. */
  private void giveQr(UUID playerId, UUID linkId) {
    Player player = Bukkit.getPlayer(playerId);
    ImageFrameBridge.PendingLink link = active.get(linkId);
    if (player == null) {
      return;
    }
    if (link == null) {
      player.sendMessage(Component.text("That upload link has expired.", NamedTextColor.RED));
      return;
    }
    if (maps.carries(player, linkId) || droppedMaps.containsKey(linkId)) {
      player.sendMessage(Component.text("You already have the QR map for this link.", NamedTextColor.YELLOW));
      return;
    }
    ItemStack item = maps.createItem(player, link);
    PlayerInventory inventory = player.getInventory();
    if (inventory.getItemInOffHand().isEmpty()) {
      inventory.setItemInOffHand(item);
      player.sendMessage(Component.text("The QR map is in your offhand: scan it with your phone camera.",
          NamedTextColor.GREEN));
    } else if (inventory.firstEmpty() != -1) {
      inventory.setItem(inventory.firstEmpty(), item);
      player.sendMessage(Component.text("The QR map is in your inventory: hold it and scan it with your"
          + " phone camera.", NamedTextColor.GREEN));
    } else {
      Item dropped = player.getWorld().dropItem(player.getLocation(), item);
      dropped.setOwner(playerId);
      droppedMaps.put(linkId, dropped.getUniqueId());
      player.sendMessage(Component.text("Your inventory is full, so the QR map was dropped at your feet.",
          NamedTextColor.YELLOW));
    }
  }

  private void finish(ImageFrameBridge.PendingLink link) {
    active.remove(link.id());
    maps.clear(link.creator(), link.id());
    UUID droppedId = droppedMaps.remove(link.id());
    if (droppedId != null) {
      Entity dropped = Bukkit.getEntity(droppedId);
      if (dropped != null) {
        dropped.remove();
      }
    }
    Player player = Bukkit.getPlayer(link.creator());
    if (player != null) {
      maps.purge(player, this::isActive);
      boolean hasNewerLink = active.values().stream().anyMatch(l -> l.creator().equals(link.creator()));
      if (!hasNewerLink) {
        player.closeDialog();
      }
    }
  }
}
