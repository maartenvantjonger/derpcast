package com.mvt.derpcast;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TabHost;
import android.widget.TextView;

import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.device.ConnectableDeviceListener;
import com.connectsdk.device.PairingDialog;
import com.connectsdk.discovery.DiscoveryManager;
import com.connectsdk.service.DeviceService;
import com.connectsdk.service.capability.MediaControl;
import com.connectsdk.service.capability.MediaPlayer;
import com.connectsdk.service.capability.VolumeControl;
import com.connectsdk.service.command.ServiceCommandError;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends ActionBarActivity implements ConnectableDeviceListener {

    private ConnectableDevice _device;
    private MenuItem _connectItem;
    private MenuItem _refreshItem;
    private DeviceAdapter _deviceAdapter;
    private MediaAdapter _videoAdapter;
    private MediaAdapter _audioAdapter;
    private long _mediaDuration;
    private boolean _playRequested;
    private BroadcastReceiver _broadcastReceiver;
    private static final String MEDIA_LOGO_URL = "https://googledrive.com/host/0BzRo13oMy82cbEJRSHM3VEVyUWc/app_logo.png";
    private static final String MEDIA_VIDEO_ART_URL = "https://googledrive.com/host/0BzRo13oMy82cbEJRSHM3VEVyUWc/video_art.png";
    private static final int PLAY_NOTIFICATION = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        setupTabs();

        ImageButton playButton = (ImageButton)findViewById(R.id.play_button);
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                play();
            }
        });

        ImageButton pauseButton = (ImageButton)findViewById(R.id.pause_button);
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pause();
            }
        });

        ImageButton stopButton = (ImageButton)findViewById(R.id.stop_button);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stop();
            }
        });

        final TextView currentTime = (TextView)findViewById(R.id.time_current);
        final SeekBar seekBar = (SeekBar)findViewById(R.id.seek_bar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean byUser) {
                final long newPosition = (_mediaDuration * progress) / 1000;
                currentTime.setText(stringForTime(newPosition));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                long newPosition = (_mediaDuration * progress) / 1000L;
                MediaControl mediaControl = _device.getMediaControl();
                if (mediaControl != null) {
                    mediaControl.seek(newPosition, null);
                }
            }
        });

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (seekBar.isShown()) {
                    getPlayerPosition();
                }
            }
        }, 1000, 1000);

        _deviceAdapter = new DeviceAdapter(MainActivity.this);
        _deviceAdapter.setDeviceAddedListener(new DeviceAdapter.DeviceAddedListener() {
            @Override
            public void onDeviceAdded(final ConnectableDevice device) {
                if (_device == null) {
                    String lastDevice = PreferenceManager
                            .getDefaultSharedPreferences(MainActivity.this)
                            .getString("lastDevice", null);
                    if (device.getId().equals(lastDevice)) {
                        connectDevice(device);
                    }
                }
            }
        });

        ListView deviceListView = (ListView)findViewById(R.id.device_list_view);
        deviceListView.setAdapter(_deviceAdapter);
        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                ConnectableDevice device = (ConnectableDevice) parent.getItemAtPosition(position);
                boolean sameDeviceClicked = _device != null && _device.getId().equals(device.getId());

                disconnectDevice();
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();

                if (sameDeviceClicked) {
                    editor.remove("lastDevice").apply();
                } else {
                    connectDevice(device);
                    editor.putString("lastDevice", _device.getId()).apply();
                }

                toggleDeviceMenu(false);
            }
        });

        AdapterView.OnItemClickListener mediaClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                findViewById(R.id.seek_bar_layout).setVisibility(View.GONE);

                MediaAdapter mediaAdapter = (MediaAdapter)parent.getAdapter();
                MediaInfo mediaInfo = mediaAdapter.getMediaInfo(position);
                if (mediaInfo.equals(mediaAdapter.getPlayingMedia())) {
                    if (_device != null) {
                        stop();
                    }
                } else {
                    _videoAdapter.setPlayingMediaInfo(mediaInfo);
                    playMedia();
                }
            }
        };

        _videoAdapter = new MediaAdapter();
        ListView videoListView = (ListView)findViewById(R.id.video_list_view);
        videoListView.setAdapter(_videoAdapter);
        videoListView.setOnItemClickListener(mediaClickListener);

        _audioAdapter = new MediaAdapter();
        ListView audioListView = (ListView)findViewById(R.id.audio_list_view);
        audioListView.setAdapter(_audioAdapter);
        audioListView.setOnItemClickListener(mediaClickListener);

        _broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                KeyEvent keyEvent = (KeyEvent)intent.getExtras().get(Intent.EXTRA_KEY_EVENT);
                if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (keyEvent.getKeyCode()) {
                        case KeyEvent.KEYCODE_MEDIA_PLAY:
                            play();
                            break;
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                            pause();
                            break;
                    }
                }
            }
        };

        LocalBroadcastManager.getInstance(MainActivity.this)
            .registerReceiver(_broadcastReceiver, new IntentFilter(Intent.ACTION_MEDIA_BUTTON));

        loadMedia();
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(MainActivity.this).unregisterReceiver(_broadcastReceiver);
        DiscoveryManager.destroy();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        View deviceLayout = findViewById(R.id.device_layout);
        if (deviceLayout.isShown()) {
            toggleDeviceMenu(false);
        }
        else {
            super.onBackPressed();
        }
    }

    private void setupTabs() {
        TabHost tabHost = (TabHost)findViewById(android.R.id.tabhost);
        tabHost.setup();

        View videoTabIndicator = getLayoutInflater().inflate(R.layout.orangeholo_tab_indicator_holo, tabHost.getTabWidget(), false);
        TextView videoTitle = (TextView)videoTabIndicator.findViewById(android.R.id.title);
        videoTitle.setText("VIDEO");

        TabHost.TabSpec videoTab = tabHost.newTabSpec("Video").setContent(R.id.video_scroll_view);
        videoTab.setIndicator(videoTabIndicator);
        tabHost.addTab(videoTab);

        View audioTabIndicator = getLayoutInflater().inflate(R.layout.orangeholo_tab_indicator_holo, tabHost.getTabWidget(), false);
        TextView audioTitle = (TextView) audioTabIndicator.findViewById(android.R.id.title);
        audioTitle.setText("AUDIO");

        TabHost.TabSpec audioTab = tabHost.newTabSpec("Audio").setContent(R.id.audio_scroll_view);
        audioTab.setIndicator(audioTabIndicator);
        tabHost.addTab(audioTab);
    }

    private void loadMedia() {
        if (_refreshItem != null) _refreshItem.setVisible(false);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        String pageUrl = preferences.getString("pageUrl", null);

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            pageUrl = intent.getStringExtra(Intent.EXTRA_TEXT);
        }

        if (pageUrl != null) {
            _videoAdapter.clear();
            _audioAdapter.clear();

            findViewById(android.R.id.tabhost).setVisibility(View.GONE);
            findViewById(R.id.media_progress_bar).setVisibility(View.VISIBLE);
            preferences.edit().putString("pageUrl", pageUrl).apply();

            Map<String, String> mediaFormats = new HashMap<String, String>();
            mediaFormats.put("aac", "audio/aac");
            mediaFormats.put("ogg", "audio/ogg");
            mediaFormats.put("mp3", "audio/mpeg");
            mediaFormats.put("mp4", "video/mp4");
            mediaFormats.put("webm", "video/webm");

            MediaScraper mediaScraper = new MediaScraper(mediaFormats);
            mediaScraper.scrape(MainActivity.this, pageUrl, 2, new MediaScraperListener() {
                @Override
                public void mediaFound(MediaInfo mediaInfo) {
                    if (mediaInfo.format.startsWith("video/")) {
                        _videoAdapter.addMediaInfo(mediaInfo);
                    }
                    else if (mediaInfo.format.startsWith("audio/")) {
                        _audioAdapter.addMediaInfo(mediaInfo);
                    }
                }

                @Override
                public void pageTitleFound(final String pageTitle) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView titleTextView = (TextView) findViewById(R.id.title_text_view);
                            if (!titleTextView.isShown()) {
                                titleTextView.setText(Html.fromHtml(pageTitle).toString());
                                titleTextView.setVisibility(View.VISIBLE);
                            }
                        }
                    });
                }

                @Override
                public void finished(final int mediaFound) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (_refreshItem != null)  _refreshItem.setVisible(true);

                            findViewById(R.id.media_progress_bar).setVisibility(View.GONE);
                            findViewById(android.R.id.tabhost).setVisibility(View.VISIBLE);

                            if (mediaFound == 0) {
                                TextView errorTextView = (TextView) findViewById(R.id.error_text_view);
                                errorTextView.setText(R.string.no_media);
                                errorTextView.setVisibility(View.VISIBLE);
                            }
                        }
                    });
                }
            });
        }
        else {
            TextView errorTextView = (TextView) findViewById(R.id.error_text_view);
            errorTextView.setText(R.string.usage);
            errorTextView.setVisibility(View.VISIBLE);
        }
    }

    private String stringForTime(long timeMs) {
        long totalSeconds = timeMs / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;

        String timeString = hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);

        return timeString;
    }

    private void playMedia() {
        if (_device == null) {
            toggleDeviceMenu(true);
            return;
        }

        final MediaInfo mediaInfo = _videoAdapter.getPlayingMedia();
        if (mediaInfo != null) {
            if (_device instanceof LocalDevice) {
                Intent intent = new Intent(MainActivity.this, MediaPlayerActivity.class);
                intent.putExtra("mediaUrl", mediaInfo.url);
                startActivity(intent);

                _videoAdapter.setPlayingMediaInfo(null);
            }
            else {
                playMedia(_device.getMediaPlayer(), mediaInfo);
            }
        }
    }

    private void playMedia(MediaPlayer mediaPlayer, MediaInfo mediaInfo) {
        _playRequested = true;

        findViewById(R.id.media_controller).setVisibility(View.VISIBLE);
        setupNotification();

        String pageTitle = ((TextView)findViewById(R.id.title_text_view)).getText().toString();
        String imageUrl = mediaInfo.format.startsWith("video/") ? MEDIA_VIDEO_ART_URL : MEDIA_LOGO_URL;

        mediaPlayer.playMedia(mediaInfo.url, mediaInfo.format, pageTitle, mediaInfo.title, imageUrl, false, new MediaPlayer.LaunchListener() {
            @Override
            public void onSuccess(MediaPlayer.MediaLaunchObject object) {
                _playRequested = false;
                setupMediaController();
            }

            @Override
            public void onError(ServiceCommandError error) {
                _playRequested = false;
                error.printStackTrace();
            }
        });
    }

    private void play() {
        if (_device != null) {
            MediaControl mediaControl = _device.getMediaControl();
            if (mediaControl != null) {
                mediaControl.play(null);
            }
        }

        startService(new Intent(RemoteControlService.ACTION_PLAY, null, MainActivity.this, RemoteControlService.class));
    }

    private void pause() {
        if (_device != null) {
            MediaControl mediaControl = _device.getMediaControl();
            if (mediaControl != null) {
                mediaControl.pause(null);
            }
        }

        startService(new Intent(RemoteControlService.ACTION_PAUSE, null, MainActivity.this, RemoteControlService.class));
    }

    private void stop() {
        if (_device != null) {
            MediaControl mediaControl = _device.getMediaControl();
            if (mediaControl != null) {
                mediaControl.stop(null);
            }
        }

        removeNotification();

        if (!_playRequested) {
            _videoAdapter.setPlayingMediaInfo(null);
        }

        findViewById(R.id.seek_bar_layout).setVisibility(View.GONE);
        findViewById(R.id.media_controller).setVisibility(View.GONE);
    }

    private void setupMediaController() {
        if (_device != null &&
                _device.hasCapability(MediaControl.Seek) &&
                _device.hasCapability(MediaControl.Duration)) {
            MediaControl mediaControl = _device.getMediaControl();
            if (mediaControl != null) {
                mediaControl.getDuration(new MediaControl.DurationListener() {
                    @Override
                    public void onSuccess(Long duration) {
                        _mediaDuration = duration;

                        TextView currentTime = (TextView) findViewById(R.id.time_current);
                        currentTime.setText(stringForTime(0));

                        TextView time = (TextView) findViewById(R.id.time);
                        time.setText(stringForTime(_mediaDuration));

                        getPlayerPosition();
                    }

                    @Override
                    public void onError(ServiceCommandError error) {
                        error.printStackTrace();
                    }
                });
            }
        }
    }

    private void setupNotification() {
        MediaInfo mediaInfo = _videoAdapter.getPlayingMedia();
        if (mediaInfo != null) {
            Context applicationContext = getApplicationContext();
            String pageTitle = ((TextView) findViewById(R.id.title_text_view)).getText().toString();
            Intent intent = new Intent(RemoteControlService.ACTION_PLAY, null, applicationContext, RemoteControlService.class);
            intent.putExtra("title", pageTitle);
            intent.putExtra("description", mediaInfo.title);
            startService(intent);

            Intent mainActivityIntent = new Intent(applicationContext, MainActivity.class);
            mainActivityIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(applicationContext, 0, mainActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            Notification notification = new Notification.Builder(applicationContext)
                .setContentTitle(pageTitle)
                .setContentText(mediaInfo.title)
                .setSmallIcon(R.drawable.main_icon)
                .setContentIntent(contentIntent)
                .build();

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(PLAY_NOTIFICATION, notification);
        }
    }

    private void removeNotification() {
        stopService(new Intent(MainActivity.this, RemoteControlService.class));

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(PLAY_NOTIFICATION);
    }

    private void getPlayerPosition() {
        if (_device != null ) {
            MediaControl mediaControl = _device.getMediaControl();
            if (mediaControl != null) {
                mediaControl.getPosition(new MediaControl.PositionListener() {
                    @Override
                    public void onSuccess(Long position) {
                        if (_mediaDuration > 0) {
                            double progress = (position / (double) _mediaDuration) * 1000;

                            TextView currentTime = (TextView) findViewById(R.id.time_current);
                            currentTime.setText(stringForTime(position));

                            SeekBar seekBar = (SeekBar) findViewById(R.id.seek_bar);
                            seekBar.setProgress((int) progress);

                            findViewById(R.id.seek_bar_layout).setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onError(ServiceCommandError error) {
                        error.printStackTrace();
                    }
                });
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        _connectItem = menu.findItem(R.id.action_connect);
        _refreshItem = menu.findItem(R.id.action_refresh);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_connect:
                toggleDeviceMenu(false);
                return true;
            case R.id.action_refresh:
                loadMedia();
                return true;
            case R.id.action_about:
                Intent intent = new Intent(MainActivity.this, AboutActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void toggleDeviceMenu(boolean keepOpen) {
        View deviceLayout = findViewById(R.id.device_layout);
        deviceLayout.setVisibility(deviceLayout.isShown() && !keepOpen ? View.GONE : View.VISIBLE);
    }

    private void connectDevice(ConnectableDevice device) {
        _device = device;
        _device.addListener(MainActivity.this);
        _device.connect();
    }

    private void disconnectDevice() {
        if (_device != null) {
            stop();

            _device.removeListener(MainActivity.this);
            _device.disconnect();
            _device = null;

            _connectItem.setIcon(R.drawable.ic_media_route_off_holo_light);
            _connectItem.setTitle(R.string.cast);
            _deviceAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (_device != null && _device.isConnected() && _device.hasCapability(VolumeControl.Volume_Up_Down)) {
                VolumeControl volumeControl = _device.getVolumeControl();
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    volumeControl.volumeUp(null);
                }
                else {
                    volumeControl.volumeDown(null);
                }

                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onPairingRequired(ConnectableDevice device, DeviceService service, DeviceService.PairingType pairingType) {
        switch (pairingType) {
            case PIN_CODE:
                PairingDialog dialog = new PairingDialog(MainActivity.this, _device);
                dialog.getPairingDialog("Enter pairing code").show();
                break;
            default:
                break;
        }
    }

    @Override
    public void onConnectionFailed(ConnectableDevice device, ServiceCommandError error) {
        disconnectDevice();
    }

    @Override
    public void onDeviceReady(final ConnectableDevice device) {
        if (!(device instanceof LocalDevice)) {
            _connectItem.setIcon(R.drawable.ic_media_route_on_holo_light);
            _connectItem.setTitle(device.getModelName());
        }

        if (device.hasCapability(MediaControl.PlayState_Subscribe)) {
            MediaControl mediaControl = device.getMediaControl();
            if (mediaControl != null) {
                mediaControl.subscribePlayState(new MediaControl.PlayStateListener() {
                    @Override
                    public void onSuccess(final MediaControl.PlayStateStatus playState) {
                        if (playState == MediaControl.PlayStateStatus.Finished ||
                                playState == MediaControl.PlayStateStatus.Idle ||
                                playState == MediaControl.PlayStateStatus.Unknown) {

                            stop();
                        }
                        else if (!findViewById(R.id.media_controller).isShown()) {
                            findViewById(R.id.media_controller).setVisibility(View.VISIBLE);

                            setupMediaController();
                        }
                    }

                    @Override
                    public void onError(ServiceCommandError error) {
                        error.printStackTrace();
                    }
                });
            }
        }

        _deviceAdapter.notifyDataSetChanged();
        playMedia();
    }

    @Override
    public void onDeviceDisconnected(ConnectableDevice device) {
        disconnectDevice();
    }

    @Override
    public void onCapabilityUpdated(ConnectableDevice device, List<String> added, List<String> removed) {}
}