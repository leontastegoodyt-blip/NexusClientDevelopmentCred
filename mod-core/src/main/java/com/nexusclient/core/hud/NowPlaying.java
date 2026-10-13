package com.nexusclient.core.hud;

import com.nexusclient.core.platform.GameInfo;
import com.nexusclient.core.platform.Renderer;
import com.nexusclient.core.settings.BooleanSetting;
import com.nexusclient.core.ui.UiKit;

/**
 * Now Playing – a small card with the song playing on the computer (Spotify, YouTube, Apple Music…):
 * a music-note tile, the title (it scrolls slowly if it's too long), the artist, and a progress bar
 * with the time. The song comes from MediaWatcher, which runs only while this mod is on.
 */
public class NowPlaying extends HudModule {

    public final BooleanSetting showProgress = add(new BooleanSetting("progress", "Show progress bar", true));
    public final BooleanSetting hideWhenPaused = add(new BooleanSetting("hidePaused", "Hide when paused", false));

    private static final int WIDTH = 150;

    public NowPlaying() {
        super("nowPlaying", "Now Playing", "Shows the song playing on your computer.", false, 0.81, 0.56);
    }

    @Override
    public void onTick(GameInfo game) {
        MediaWatcher.start(); // (only does something the first time)
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            MediaWatcher.stop();
        }
    }

    @Override
    protected int[] draw(Renderer r, GameInfo game, boolean preview) {
        MediaWatcher.Song song = MediaWatcher.current();
        String title;
        String artist;
        int position;
        int length;
        boolean playing;
        if (preview || (song == null && game.menuOpen())) { // (sample in the HUD editor, so it can be moved)
            title = "Neon Nights (Extended Mix)";
            artist = "Nexus Records";
            position = 83;
            length = 214;
            playing = true;
        } else if (song == null || (hideWhenPaused.isOn() && !song.playing)) {
            return new int[] {0, 0}; // nothing playing – take no space
        } else {
            title = song.title;
            artist = song.artist.isEmpty() ? (song.playing ? "Playing" : "Paused") : song.artist;
            position = song.positionNow();
            length = song.length;
            playing = song.playing;
        }
        int height = showProgress.isOn() && length > 0 ? 38 : 30;
        if (background.isOn()) {
            UiKit.round(r, 0, 0, WIDTH, height, 5, backgroundColor.get());
        }
        UiKit.gradientLine(r, 4, 0, WIDTH - 4, 0xB0);

        // The music-note tile.
        int accent = UiKit.accent() | 0xFF000000;
        UiKit.round(r, 5, 5, 25, 25, 4, UiKit.accent(0x30));
        drawNote(r, 10, 8, playing ? accent : 0xFF7C7C96);

        // Title (scrolls if too long) and artist.
        int textX = 30;
        int textW = WIDTH - textX - 5;
        int colour = textColor.get();
        int titleW = r.textWidth(title);
        if (titleW <= textW) {
            r.text(title, textX, 5, colour, textShadow.isOn());
        } else if (com.nexusclient.core.ui.Accessibility.reducedMotion()) {
            r.text(UiKit.fit(r, title, textW), textX, 5, colour, textShadow.isOn()); // reduced motion: no scrolling
        } else {
            // Slide left and back again, pausing at each end (a "marquee").
            int travel = titleW - textW;
            long cycle = 4000 + travel * 60L;
            long t = System.currentTimeMillis() % (cycle * 2);
            double phase = t < cycle ? t / (double) cycle : 1 - (t - cycle) / (double) cycle;
            double eased = Math.max(0, Math.min(1, (phase - 0.15) / 0.7));
            r.enableClip(textX, 3, textX + textW, 15);
            r.text(title, textX - (int) Math.round(travel * eased), 5, colour, textShadow.isOn());
            r.disableClip();
        }
        r.text(UiKit.fit(r, artist, textW), textX, 16, UiKit.TEXT_DIM, textShadow.isOn());

        // Progress bar and time.
        if (showProgress.isOn() && length > 0) {
            int barY = 30;
            String times = clock(position) + " / " + clock(length);
            int barEnd = WIDTH - 8 - r.textWidth(times);
            UiKit.round(r, 6, barY, barEnd, barY + 2, 1, 0x30FFFFFF);
            int filled = 6 + (int) Math.round((barEnd - 6) * Math.min(1, position / (double) length));
            if (filled > 6) {
                UiKit.round(r, 6, barY, filled, barY + 2, 1, accent);
            }
            r.text(times, barEnd + 4, barY - 3, UiKit.TEXT_DIM, false);
        }
        return new int[] {WIDTH, height};
    }

    /** An eighth note (♪) made of rectangles – fonts don't all have the symbol. */
    private static void drawNote(Renderer r, int x, int y, int colour) {
        r.rect(x + 6, y, x + 7, y + 11, colour);         // the stem
        r.rect(x + 7, y, x + 10, y + 1, colour);         // the flag
        r.rect(x + 9, y + 1, x + 10, y + 4, colour);
        r.rect(x + 2, y + 10, x + 6, y + 13, colour);    // the head
        r.rect(x + 1, y + 11, x + 2, y + 12, colour);
        r.rect(x + 3, y + 9, x + 6, y + 10, colour);
    }

    private static String clock(int seconds) {
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }
}
