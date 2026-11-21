package es.tvradio;

import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

public class PlaybackService extends MediaSessionService {

    private MediaSession mediaSession;
    private ExoPlayer player;
    private int reintentos = 0;

    private final Handler handler =
        new Handler(Looper.getMainLooper());

    private final Runnable temporizador = new Runnable() {
        @Override
        public void run() {
            SharedPreferences prefs =
                getSharedPreferences("radio", MODE_PRIVATE);

            long limite = prefs.getLong("sleepUntil", 0);

            if (limite > 0 &&
                System.currentTimeMillis() >= limite) {

                if (player != null) {
                    player.pause();
                }

                prefs.edit()
                    .putLong("sleepUntil", 0)
                    .apply();
            }

            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        player = new ExoPlayer.Builder(this).build();

        player.setAudioAttributes(
            AudioAttributes.DEFAULT, true
        );

        player.setWakeMode(C.WAKE_MODE_NETWORK);
        player.setHandleAudioBecomingNoisy(true);

        player.setTrackSelectionParameters(
            player.getTrackSelectionParameters()
                .buildUpon()
                .setTrackTypeDisabled(
                    C.TRACK_TYPE_VIDEO, true)
                .build()
        );

        player.addListener(new Player.Listener() {

            @Override
            public void onPlayerError(
                PlaybackException error) {

                if (reintentos < 5 &&
                    player.getPlayWhenReady()) {

                    reintentos++;

                    handler.postDelayed(() -> {
                        if (player != null &&
                            player.getPlayWhenReady() &&
                            player.getPlayerError() != null) {
                            player.prepare();
                        }
                    }, reintentos * 3000L);
                }
            }

            @Override
            public void onIsPlayingChanged(
                boolean reproduciendo) {

                if (reproduciendo) {
                    reintentos = 0;
                }
            }

            @Override
            public void onMediaItemTransition(
                androidx.media3.common.MediaItem item,
                int reason) {

                reintentos = 0;
            }
        });

        mediaSession = new MediaSession.Builder(
            this, player
        ).build();

        handler.post(temporizador);
    }

    @Override
    public MediaSession onGetSession(
        MediaSession.ControllerInfo controllerInfo
    ) {
        return mediaSession;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);

        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }

        if (player != null) {
            player.release();
            player = null;
        }

        super.onDestroy();
    }
}
