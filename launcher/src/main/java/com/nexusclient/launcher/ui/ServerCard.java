package com.nexusclient.launcher.ui;

import com.nexusclient.launcher.servers.ServerPinger;
import com.nexusclient.launcher.servers.ServerStore;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
    private final HBox signal = new HBox(2);
    private final StackPane playersBar = new StackPane();
    private final Region playersFill = new Region();

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
        HBox stats = new HBox(10, signal, playersBar, players, ping);
        stats.setAlignment(Pos.CENTER_LEFT);
        players.getStyleClass().add("mod-meta");
        ping.getStyleClass().add("mod-meta");
        VBox text = new VBox(3, nameRow, motd, stats);
        HBox.setHgrow(text, Priority.ALWAYS);

        // Signal bars (like a phone): more lit bars = lower ping. Filled in by refresh().
        for (int i = 0; i < 4; i++) {
            Region bar = new Region();
            bar.getStyleClass().add("signal-bar");
            bar.setMinSize(3, 4 + i * 3);
            bar.setMaxSize(3, 4 + i * 3);
            signal.getChildren().add(bar);
        }
        signal.setAlignment(Pos.BOTTOM_LEFT);
        signal.setMinHeight(13);
        // A thin bar showing how full the server is.
        playersFill.getStyleClass().add("players-fill");
        playersFill.setMaxWidth(0);
        playersBar.getChildren().add(playersFill);
        playersBar.getStyleClass().add("players-track");
        playersBar.setAlignment(Pos.CENTER_LEFT);
        playersBar.setMinSize(70, 5);
        playersBar.setMaxSize(70, 5);
        playersBar.setVisible(false);
        playersBar.setManaged(false);

        Button favourite = People.iconButton(store.isFavourite(server.address()) ? Icons.STAR : Icons.STAR_OUTLINE,
                "Favourite", false, () -> { });
        favourite.setOnAction(event -> {
            boolean now = !store.isFavourite(server.address());
            store.setFavourite(server, now);
            favourite.setGraphic(Icons.create(now ? Icons.STAR : Icons.STAR_OUTLINE, 16));
            Toast.info(now ? server.name() + " added to favourites." : server.name() + " removed from favourites.");
        });
        favourite.getStyleClass().add("favourite-button");

        Button copy = People.iconButton(Icons.COPY, "Copy IP", false, () -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(server.address());
            Clipboard.getSystemClipboard().setContent(content);
            Toast.info("Copied " + server.address());
        });

        Button join = new Button("Join", Icons.create(Icons.PLAY, 13));
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
            String message = status.motd().isEmpty() ? server.description() : status.motd().replace('\n', ' ');
            motd.setText(withoutPrivateSymbols(message));
            NumberFormat numbers = NumberFormat.getIntegerInstance();
            players.setText(numbers.format(status.players()) + (status.max() > 0 ? " / " + numbers.format(status.max()) : "")
                    + " playing");
            ping.setText(status.pingMs() >= 0 ? status.pingMs() + " ms" : "");
            String quality = status.pingMs() < 0 ? "ping-ok"
                    : status.pingMs() < 80 ? "ping-good" : status.pingMs() < 180 ? "ping-ok" : "ping-bad";
            ping.getStyleClass().removeAll("ping-good", "ping-ok", "ping-bad");
            ping.getStyleClass().add(quality);
            // Light up the signal bars: 4 = great, 1 = laggy.
            int lit = status.pingMs() < 0 ? 0 : status.pingMs() < 60 ? 4 : status.pingMs() < 120 ? 3
                    : status.pingMs() < 200 ? 2 : 1;
            for (int i = 0; i < signal.getChildren().size(); i++) {
                Node bar = signal.getChildren().get(i);
                bar.getStyleClass().removeAll("lit", "ping-good", "ping-ok", "ping-bad");
                if (i < lit) {
                    bar.getStyleClass().addAll("lit", quality);
                }
            }
            // Grow the "how full" bar from empty to its value.
            if (status.max() > 0) {
                playersBar.setVisible(true);
                playersBar.setManaged(true);
                double full = Math.min(1, status.players() / (double) status.max());
                javafx.animation.Timeline grow = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                        javafx.util.Duration.millis(Animations.enabled ? 700 : 1),
                        new javafx.animation.KeyValue(playersFill.maxWidthProperty(), Math.max(3, 70 * full),
                                javafx.animation.Interpolator.EASE_OUT)));
                grow.play();
            }
            if (status.icon() != null) {
                Image image = new Image(new ByteArrayInputStream(status.icon()), 48, 48, true, false);
                iconBox.getChildren().add(new ImageView(image));
            }
        }, error -> players.setText("Offline"));
    }

    /**
     * Removes "private use" characters from a server message. Servers like MCC Island draw their
     * logo with special symbols that only exist in their own resource pack – no normal font has
     * them, so they would show up as empty boxes (■■■) here.
     */
    static String withoutPrivateSymbols(String text) {
        StringBuilder clean = new StringBuilder();
        text.codePoints()
                .filter(c -> Character.getType(c) != Character.PRIVATE_USE)
                .forEach(clean::appendCodePoint);
        return clean.toString().replaceAll(" {2,}", " ").trim();
    }
}
