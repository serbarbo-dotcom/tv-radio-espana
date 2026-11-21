package es.tvradio;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

public class PlaybackService extends MediaSessionService {

    private MediaSession mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();

        ExoPlayer player = new ExoPlayer.Builder(this).build();

        player.setAudioAttributes(
            AudioAttributes.DEFAULT, true
        );

        player.setWakeMode(C.WAKE_MODE_NETWORK);
        player.setHandleAudioBecomingNoisy(true);

        player.setTrackSelectionParameters(
            player.getTrackSelectionParameters()
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build()
        );

        mediaSession = new MediaSession.Builder(
            this, player
        ).build();
    }

    @Override
    public MediaSession onGetSession(
        MediaSession.ControllerInfo controllerInfo
    ) {
        return mediaSession;
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.getPlayer().release();
            mediaSession.release();
            mediaSession = null;
        }

        super.onDestroy();
    }
}
