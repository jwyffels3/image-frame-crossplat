package io.github.jwyffels3.imageframecrossplay;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Keeps QR maps in their owner's own inventory: they can't be dropped, stored, framed, put in
 * bundles, crafted/copied or picked up by others, and leftovers are deleted when found.
 * (A stray copy would show "Link expired" anyway.)
 */
final class QrItemGuard implements Listener {

  private final QrMaps maps;
  private final UploadWatcher watcher;

  QrItemGuard(QrMaps maps, UploadWatcher watcher) {
    this.maps = maps;
    this.watcher = watcher;
  }

  @EventHandler(ignoreCancelled = true)
  public void onDrop(PlayerDropItemEvent event) {
    if (maps.isQr(event.getItemDrop().getItemStack())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onClick(InventoryClickEvent event) {
    Inventory top = event.getView().getTopInventory();
    boolean clickedTop = event.getClickedInventory() == top;
    ItemStack cursor = event.getCursor();
    ItemStack current = event.getCurrentItem();

    // Into a bundle, or a bundle onto the map
    if ((maps.isQr(cursor) && isBundle(current)) || (maps.isQr(current) && isBundle(cursor))) {
      event.setCancelled(true);
      return;
    }
    // Placing from the cursor into a chest, crafting grid, cartography table, ...
    if (clickedTop && maps.isQr(cursor)) {
      event.setCancelled(true);
      return;
    }
    // Number-key or offhand (F) swap into a top slot
    if (clickedTop) {
      ItemStack swapped = event.getClick() == ClickType.SWAP_OFFHAND
          ? event.getWhoClicked().getInventory().getItemInOffHand()
          : event.getHotbarButton() >= 0
              ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) : null;
      if (maps.isQr(swapped)) {
        event.setCancelled(true);
        return;
      }
    }
    // Shift-click from the player's inventory into an open container
    if (!clickedTop && event.isShiftClick() && maps.isQr(current)
        && top.getType() != InventoryType.CRAFTING) {
      event.setCancelled(true);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onDrag(InventoryDragEvent event) {
    if (!maps.isQr(event.getOldCursor())) {
      return;
    }
    int topSize = event.getView().getTopInventory().getSize();
    if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
      event.setCancelled(true);
    }
  }

  /** Item frames, allays and any other "give the held item to an entity". */
  @EventHandler(ignoreCancelled = true)
  public void onInteractEntity(PlayerInteractEntityEvent event) {
    if (maps.isQr(event.getPlayer().getInventory().getItem(event.getHand()))) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onDeath(PlayerDeathEvent event) {
    event.getDrops().removeIf(maps::isQr);
  }

  /** Dropped maps (full inventory) are owner-only for players; mobs never take them. */
  @EventHandler(ignoreCancelled = true)
  public void onPickup(EntityPickupItemEvent event) {
    if (maps.isQr(event.getItem().getItemStack()) && !(event.getEntity() instanceof Player)) {
      event.setCancelled(true);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void onHopperPickup(InventoryPickupItemEvent event) {
    if (maps.isQr(event.getItem().getItemStack())) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onJoin(PlayerJoinEvent event) {
    maps.purge(event.getPlayer(), watcher::isActive);
  }

  @EventHandler(ignoreCancelled = true)
  public void onOpen(InventoryOpenEvent event) {
    maps.purge(event.getInventory(), watcher::isActive);
    if (event.getPlayer() instanceof Player player) {
      maps.purge(player, watcher::isActive);
    }
  }

  /** A dropped map in a chunk that was unloaded when its link ended. */
  @EventHandler
  public void onEntitiesLoad(EntitiesLoadEvent event) {
    for (Entity entity : event.getEntities()) {
      if (entity instanceof Item item) {
        java.util.UUID link = maps.linkOf(item.getItemStack());
        if (link != null && !watcher.isActive(link)) {
          item.remove();
        }
      }
    }
  }

  private static boolean isBundle(ItemStack item) {
    return item != null && item.getType().name().endsWith("BUNDLE");
  }
}
