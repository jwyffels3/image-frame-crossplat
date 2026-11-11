package io.github.jwyffels3.imageframecrossplay;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The upload pop-up, in two screens that look the same on both editions (Geyser turns Java
 * dialogs into Bedrock forms):
 * <ol>
 *   <li>A menu without text boxes, so Bedrock shows real buttons: Show QR code, Show link, Close.
 *   <li>Only for "Show link": the link in a text box to copy, and Back to the menu.
 * </ol>
 * Closing the menu (X / Escape) does nothing. A screen with a text box can only have one button on
 * Bedrock, which Geyser also runs when the form is closed, so X on the link screen goes back too.
 * Bedrock can't open links, so there is no "open" button; Java players can still click
 * ImageFrame's chat link.
 */
final class LinkDialog {

  private LinkDialog() {
  }

  static void show(Plugin plugin, Player player, ImageFrameBridge.PendingLink link, Runnable onShowQr) {
    Component intro = Component.text()
        .append(Component.text("Upload an image for "))
        .append(Component.text(link.imageMap(), NamedTextColor.GOLD, TextDecoration.BOLD))
        .append(Component.text(" (" + link.width() + " × " + link.height() + " maps).\n\n"))
        .append(Component.text("Scan the QR code with your phone, or copy the link and open it in a"
            + " browser on any device. The link works once and expires in 5 minutes."))
        .build();

    // The link screen's Back needs the menu and the menu's Show link needs the link screen,
    // so Back looks the menu up when pressed.
    Dialog[] menu = new Dialog[1];
    Dialog linkScreen = linkScreen(link, onMain(plugin, (response, audience) -> audience.showDialog(menu[0])));

    ActionButton qr = ActionButton.builder(Component.text("Show QR code"))
        .tooltip(Component.text("Puts a QR-code map in your hand"))
        .action(onMain(plugin, (response, audience) -> onShowQr.run()))
        .build();
    ActionButton showLink = ActionButton.builder(Component.text("Show link"))
        .action(DialogAction.staticAction(ClickEvent.showDialog(linkScreen)))
        .build();
    ActionButton close = ActionButton.builder(Component.text("Close")).build();

    menu[0] = Dialog.create(factory -> factory.empty()
        .base(DialogBase.builder(Component.text("Upload an image"))
            .canCloseWithEscape(true)
            .afterAction(DialogBase.DialogAfterAction.CLOSE)
            .body(List.of(DialogBody.plainMessage(intro, 300)))
            .build())
        .type(DialogType.multiAction(List.of(qr, showLink)).exitAction(close).columns(1).build()));
    player.showDialog(menu[0]);
  }

  /** The link in a text box, and Back. */
  private static Dialog linkScreen(ImageFrameBridge.PendingLink link, DialogAction back) {
    return Dialog.create(factory -> factory.empty()
        .base(DialogBase.builder(Component.text("Upload link"))
            .canCloseWithEscape(true)
            .afterAction(DialogBase.DialogAfterAction.CLOSE)
            .body(List.of(DialogBody.plainMessage(Component.text("Select all, copy, and open it in a"
                + " browser. It works once and expires 5 minutes after it was made."), 300)))
            .inputs(List.of(DialogInput.text("link", Component.text("Link"))
                .initial(link.url())
                .maxLength(256)
                .width(300)
                .build()))
            .build())
        .type(DialogType.notice(ActionButton.builder(Component.text("Back")).action(back).build())));
  }

  /** A button action handled by the server, run on the main thread, usable while the link is. */
  private static DialogAction onMain(Plugin plugin, DialogActionCallback callback) {
    return DialogAction.customClick((response, audience) -> {
      if (Bukkit.isPrimaryThread()) {
        callback.accept(response, audience);
      } else {
        Bukkit.getScheduler().runTask(plugin, () -> callback.accept(response, audience));
      }
    }, ClickCallback.Options.builder()
        .uses(ClickCallback.UNLIMITED_USES)
        .lifetime(Duration.ofMinutes(6))
        .build());
  }
}
