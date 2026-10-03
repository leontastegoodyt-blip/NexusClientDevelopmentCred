package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.servers.ServerPinger;
import com.nexusclient.launcher.servers.ServerStore;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.ByteArrayInputStream;
import java.text.NumberFormat;
import java.util.function.Consumer;

/**
 * ServerCard – one server in a list: its icon, name, message of the day, live player count and
 * ping, plus Join, favourite (★) and copy-address buttons.
 *
 * The live details come from pinging the server in the background (see ServerPinger), so the card
 * appears straight away and fills in a moment later.
 */
public class ServerCard extends HBox {

    private final ServerStore.Server server;
    private final StackPane iconBox = new StackPane();
    private final Label motd = new Label("Checking…");
    private final Label players = new Label("");
    private final Label ping = new Label("");

    /**
     * @param onJoin called with the server's address when Join is clicked
     */
    public ServerCard(ServerStore.Server server, ServerStore store, Consumer<ServerStore.Server> onJoin) {
        this.server = server;
        getStyleClass().add("mod-row");
        setSpacing(14);
        setAlignment(Pos.CENTER_LEFT);

        Region placeholder = new Region();
        placeholder.getStyleClass().add("head-placeholder");
        iconBox.getChildren().add(placeholder);
        iconBox.setMinSize(48, 48);
        iconBox.setMaxSize(48, 48);
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(48, 48);
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        iconBox.setClip(clip);

        Label name = new Label(server.name());
        name.getStyleClass().add("mod-title");
        Label address = new Label(server.address());
        address.getStyleClass().add("mod-author");
        HBox nameRow = new HBox(8, name, address);
        nameRow.setAlignment(Pos.BASELINE_LEFT);
        motd.getStyleClass().add("mod-description");
        motd.setWrapText(true);
        motd.setMaxHeight(36);
        HBox stats = new HBox(14, players, ping);
        players.getStyleClass().add("mod-meta");
        ping.getStyleClass().add("mod-meta");
        VBox text = new VBox(3, nameRow, motd, stats);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button favourite = new Button(store.isFavourite(server.address()) ? "★" : "☆");
        favourite.getStyleClass().add("secondary-button");
        favourite.setTooltip(new Tooltip("Favourite"));
        favourite.setOnAction(event -> {
            boolean now = !store.isFavourite(server.address());
            store.setFavourite(server, now);
            favourite.setText(now ? "★" : "☆");
            Toast.info(now ? server.name() + " added to favourites." : server.name() + " removed from favourites.");
        });

        Button copy = new Button("Copy IP");
        copy.getStyleClass().add("secondary-button");
        copy.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(server.address());
            Clipboard.getSystemClipboard().setContent(content);
            Toast.info("Copied " + server.address());
        });

        Button join = new Button("Join");
        join.getStyleClass().add("primary-button");
        join.setOnAction(event -> onJoin.accept(server));

        // Buttons keep their full size; a long message of the day wraps instead of squashing them.
        for (Button button : new Button[] {favourite, copy, join}) {
            button.setMinWidth(Region.USE_PREF_SIZE);
        }
        text.setMinWidth(0);
        getChildren().addAll(iconBox, text, favourite, copy, join);
        refresh();
    }

    /** Pings the server again and updates the details. */
    public void refresh() {
        Background.run("ping-" + server.address(), () -> ServerPinger.ping(server.address()), status -> {
            if (!status.online()) {
                motd.setText(status.motd());
                players.setText("Offline");
                ping.setText("");
                return;
            }
            motd.setText(status.motd().isEmpty() ? server.description() : status.motd().replace('\n', ' '));
            NumberFormat numbers = NumberFormat.getIntegerInstance();
            players.setText(numbers.format(status.players()) + (status.max() > 0 ? " / " + numbers.format(status.max()) : "")
                    + " playing");
            ping.setText(status.pingMs() >= 0 ? status.pingMs() + " ms" : "");
            ping.getStyleClass().removeAll("ping-good", "ping-ok", "ping-bad");
            ping.getStyleClass().add(status.pingMs() < 0 ? "ping-ok"
                    : status.pingMs() < 80 ? "ping-good" : status.pingMs() < 180 ? "ping-ok" : "ping-bad");
            if (status.icon() != null) {
                Image image = new Image(new ByteArrayInputStream(status.icon()), 48, 48, true, false);
                iconBox.getChildren().add(new ImageView(image));
            }
        }, error -> players.setText("Offline"));
    }
}
