package sample.app.simplehlsexo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoListener;

import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements VideoListener {

    private String TAG = "asdf";

    private boolean playWhenReady = true;
    private int currentWindow = 0;
    private long playbackPosition = 0;
    PlayerView playerView;
    private ProgressBar loading;
    private SimpleExoPlayer player=null;
    private Context mContext;
    private String STREAM_HLS = "https://www.sample-videos.com/video123/3gp/144/big_buck_bunny_144p_1mb.3gp";
    private Button mask;
    private Button reset;
    private EditText urlEditText;

    private com.google.android.exoplayer2.upstream.DataSource.Factory mediaDataSourceFactory;
    private DefaultTrackSelector trackSelector;
    private BandwidthMeter bandwidthMeter;
    //private MediaCodecVideoRenderer videoRenderer;
    //LoopingMediaSource loopingSource;
    private boolean autoPlay = true;

    SharedPreferences pref = null;
    SharedPreferences.Editor editor = null;

    Timer watchTimer = null;

    String MODE_WATCH = "watch";
    String MODE_REST = "rest";
    long REST_TIME = 60 * 10; // sec
    long WATCH_TIME = 60 * 30; // sec

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pref = this.getPreferences(Context.MODE_PRIVATE);
        //Remove title bar
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        try {
            getSupportActionBar().hide(); //hide the title bar
        } catch (Exception e) {
            e.printStackTrace();
        }

        //Remove notification bar
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);


        setContentView(R.layout.activity_main);
        mContext = this;
        playerView = findViewById(R.id.video_view);
        loading = findViewById(R.id.loading);

        try {
            requestPermissionForReadExtertalStorage();
        } catch (Exception e) {
            e.printStackTrace();
        }


        bandwidthMeter = new DefaultBandwidthMeter();
        mediaDataSourceFactory = new DefaultDataSourceFactory(this, Util.getUserAgent(this, "simpleexoplayer"),
                (TransferListener<? super DataSource>) bandwidthMeter);

    }

    @Override
    public void onStart() {
        super.onStart();
        Long tsLong = System.currentTimeMillis() / 1000;

        mask = findViewById(R.id.mask);
        reset = findViewById(R.id.reset);

        String mode = pref.getString("mode", "");
        int playCount = pref.getInt("playCount", 0);
        Log.d(TAG, String.format("%d, %s, %d, %d", playCount, mode, pref.getLong("tsWatch", 0), pref.getLong("tsRest", 0)));

        final MediaPlayer mp = MediaPlayer.create(this, R.raw.aaa);

        mask.setOnClickListener(view -> {
            if(hasRestEnough()) {
                putValue("tsWatch", System.currentTimeMillis() / 1000);
                putValue("mode", MODE_WATCH);
                putValue("playCount", 0);
                hideBanner();
                continuePlay();
                startCheckWatchTime();
            } else {
                mp.start();
            }
        });

        if (mode.equals("rest")) {
            if(!hasRestEnough()) {
                showBanner("not rest enough");
                return;
            } else {
                putValue("tsWatch", tsLong);
                putValue("mode", MODE_WATCH);
            }
        }

        putValue("playCount", playCount + 1);
        hideBanner();
        startPlay();
    }

    void startCheckWatchTime() {
        if(watchTimer != null){
            watchTimer.cancel();
        }
        watchTimer = new Timer();
        // https://stackoverflow.com/questions/4817933/what-is-the-equivalent-to-a-javascript-setinterval-settimeout-in-android-java
        watchTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // https://stackoverflow.com/questions/18656813/android-only-the-original-thread-that-created-a-view-hierarchy-can-touch-its-vi
                runOnUiThread(() -> {
                    Log.i(TAG, "A Kiss every 5 seconds");
                    Long tsLong = System.currentTimeMillis() / 1000;
                    long lastTs = pref.getLong("tsWatch", 0);
                    if (tsLong - lastTs >= WATCH_TIME) {
                        Log.d(TAG, tsLong - lastTs + "");
                        // need rest!!!
                        putValue("tsRest", tsLong);
                        putValue("mode", MODE_REST);

                        pausePlay();
                        showBanner("need rest!");
                        watchTimer.cancel();
                    }
                });
            }
        }, 0, 1000);
    }

    void showBanner(String reason) {
        Log.d(TAG, "showBanner: "+reason);
        mask.setVisibility(View.VISIBLE);
        // reset.setVisibility(View.VISIBLE);

        mask.setFocusable(true);
        mask.setFocusableInTouchMode(true);///add this line
        mask.requestFocus();
    }

    void hideBanner(){
        mask.setVisibility(View.GONE);
         reset.setVisibility(View.GONE);
    }

    boolean hasRestEnough() {
        Long tsLong = System.currentTimeMillis() / 1000;
        long lastTs = pref.getLong("tsRest", 0);
        return tsLong - lastTs >= REST_TIME;
    }

    void startPlay() {
        //        String url11 = Environment.getExternalStorageDirectory().getAbsolutePath()+"/DCIM/SampleVideo_176x144_1mb.3gp";
        String url11 = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/tp_merge_1544452560721-v1.mp4";

        try {
            intializePlayer11(url11);
            startCheckWatchTime();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public void requestPermissionForReadExtertalStorage() throws Exception {
        try {
            ActivityCompat.requestPermissions((Activity) mContext, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    10);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    private void intializePlayer(String url) {
        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveTrackSelection.Factory(bandwidthMeter);

        trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

        TrackSelection.Factory adaptiveTrackSelection = new AdaptiveTrackSelection.Factory(new DefaultBandwidthMeter());

        player = ExoPlayerFactory.newSimpleInstance(new DefaultRenderersFactory(mContext),
                new DefaultTrackSelector(adaptiveTrackSelection),
                new DefaultLoadControl());
        //player.setPlayWhenReady(true);
        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();

        MediaSource mediaSource = new ExtractorMediaSource(Uri.parse("http://neonplayer.api.dev.naver.com/file/pdtest/720p_sample.mp4"),
                mediaDataSourceFactory, extractorsFactory, null, null);

        player.prepare(mediaSource);
        player.setPlayWhenReady(true);

        player.addVideoListener(this);

        playerView.setPlayer(player);

    }

    private void intializePlayer11(String hls_url) throws URISyntaxException {
        //--------------------------------------
        //Creating default track selector
        //and init the player
        TrackSelection.Factory adaptiveTrackSelection = new AdaptiveTrackSelection.Factory(new DefaultBandwidthMeter());
        player = ExoPlayerFactory.newSimpleInstance(
                new DefaultRenderersFactory(mContext),
                new DefaultTrackSelector(adaptiveTrackSelection),
                new DefaultLoadControl());

        //init the player
        playerView.setPlayer(player);

        //-------------------------------------------------
        DefaultBandwidthMeter defaultBandwidthMeter = new DefaultBandwidthMeter();
        // Produces DataSource instances through which media data is loaded.
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(mContext, Util.getUserAgent(mContext, "Exo2"), defaultBandwidthMeter);

        //-----------------------------------------------
        //Create media source
        Uri uri = Uri.parse(hls_url);
        Handler mainHandler = new Handler();
//        MediaSource mediaSource = new HlsMediaSource(uri, dataSourceFactory, mainHandler, null);


        String url = null;
        try {
            url = getFilePath(this, getIntent().getData());
        } catch (Exception e) {
            url = hls_url;
        }
        if (url == null) {
            url = hls_url;
        }

        MainActivity self = this;

        DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
//        MediaSource mediaSource = new ExtractorMediaSource(Uri.parse(url),  dataSourceFactory, extractorsFactory, null, null);
        MediaSource mediaSource = new ExtractorMediaSource.Factory(dataSourceFactory).setExtractorsFactory(extractorsFactory).createMediaSource(Uri.parse(url));
        player.setRepeatMode(Player.REPEAT_MODE_OFF);
        player.prepare(mediaSource);

        player.setPlayWhenReady(playWhenReady);

        player.addListener(new Player.EventListener() {
            @Override
            public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {

            }

            @Override
            public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

            }

            @Override
            public void onLoadingChanged(boolean isLoading) {

            }

            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                Log.d(TAG, playbackState + "");
                switch (playbackState) {
                    case Player.STATE_READY:
                        loading.setVisibility(View.GONE);
                        break;
                    case Player.STATE_BUFFERING:
                        loading.setVisibility(View.VISIBLE);
                        break;
                    case Player.STATE_ENDED:
                        self.closeApplication();
                        break;
                }
            }

            @Override
            public void onRepeatModeChanged(int repeatMode) {

            }

            @Override
            public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                Toast.makeText(mContext, error.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPositionDiscontinuity(int reason) {

            }

            @Override
            public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

            }

            @Override
            public void onSeekProcessed() {

            }
        });
        player.seekTo(currentWindow, playbackPosition);
        player.prepare(mediaSource, true, false);
    }

    public void closeApplication() {
        releasePlayer();
        finish();
        moveTaskToBack(true);
    }

    private void releasePlayer() {
        if (player != null) {
            playbackPosition = player.getCurrentPosition();
            currentWindow = player.getCurrentWindowIndex();
            playWhenReady = player.getPlayWhenReady();
            player.release();
            player = null;
        }
    }

    void putValue(String key, int value) {
        SharedPreferences.Editor editor = pref.edit();
        editor.putInt(key, value);
        editor.apply();
    }

    void putValue(String key, long value) {
        SharedPreferences.Editor editor = pref.edit();
        editor.putLong(key, value);
        editor.apply();
    }

    void putValue(String key, String value) {
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(key, value);
        editor.apply();
    }

    void continuePlay() {
        if(player!=null) {
            player.setPlayWhenReady(true);
        }
    }

    void pausePlay() {
        if(player!=null) {
            player.setPlayWhenReady(false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Util.SDK_INT <= 23) {
            releasePlayer();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            releasePlayer();
        }
    }


    @SuppressLint("NewApi")
    public static String getFilePath(Context context, Uri uri) throws URISyntaxException {
        String selection = null;
        String[] selectionArgs = null;
        // Uri is different in versions after KITKAT (Android 4.4), we need to
        if (Build.VERSION.SDK_INT >= 19 && DocumentsContract.isDocumentUri(context.getApplicationContext(), uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                return Environment.getExternalStorageDirectory() + "/" + split[1];
            } else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                uri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
            } else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                if ("image".equals(type)) {
                    uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                selection = "_id=?";
                selectionArgs = new String[]{
                        split[1]
                };
            }
        }
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] projection = {
                    MediaStore.Images.Media.DATA
            };
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver()
                        .query(uri, projection, selection, selectionArgs, null);
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                if (cursor.moveToFirst()) {
                    return cursor.getString(column_index);
                }
            } catch (Exception e) {
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }


    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }


    //////////////////////////////////////////////////////////////////////////
    // SimpleExoPlayer.VideoListener

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        Log.d(TAG, "width = " + width + " height = " + height + " unappliedRotationDegrees = " + unappliedRotationDegrees + " pixelWidthHeightRatio = " + pixelWidthHeightRatio);
        float videoAspect = ((float) width / height) * pixelWidthHeightRatio;
        Log.d(TAG, "videoAspect = " + videoAspect);
    }

    @Override
    public void onRenderedFirstFrame() {
        // do nothing
        Log.d(TAG, "first Frame!!!!");
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        // Set the content to appear under the system bars so that the
                        // content doesn't resize when the system bars hide and show.
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // Hide the nav bar and status bar
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    // Shows the system bars by removing all the flags
// except for the ones that make the content appear under the system bars.
    private void showSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }
}
