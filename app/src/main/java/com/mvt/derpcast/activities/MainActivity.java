package com.mvt.derpcast.activities;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TabHost;
import android.widget.TabWidget;
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
import com.mvt.derpcast.R;
import com.mvt.derpcast.castservice.CastService;
import com.mvt.derpcast.castservice.CastServiceBinder;
import com.mvt.derpcast.device.DeviceAdapter;
import com.mvt.derpcast.device.DeviceAddedListener;
import com.mvt.derpcast.device.LocalDevice;
import com.mvt.derpcast.media.MediaAdapter;
import com.mvt.derpcast.media.MediaInfo;
import com.mvt.derpcast.media.MediaScraper;
import com.mvt.derpcast.media.MediaScraperListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends ActionBarActivity {

    private ServiceConnection _serviceConnection;
    private CastService _castService;
    private ConnectableDevice _device;
    private DeviceAdapter _deviceAdapter;
    private MediaAdapter _videoAdapter;
    private MediaAdapter _audioAdapter;
    private BroadcastReceiver _broadcastReceiver;
    private Timer _timer;

    private long _mediaDuration;
    private boolean _playRequested;

    private MenuItem _connectItem;
    private MenuItem _refreshItem;
    private TabHost _tabHost;
    private SeekBar _seekBar;
    private View _seekBarLayout;
    private View _mediaController;
    private TextView _titleTextView;
    private View _usageTextView;
    private View _deviceLayout;
    private TextView _currentTimeTextView;
    private TextView _durationTextView;
    private View _mediaProgressBar;
    private ListView _deviceListView;
    private ListView _videoListView;
    private ListView _audioListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupViews();
        bindCastService();
        setupBroadcastReceiver();
    }

    @Override
    protected void onStop() {
        super.onStop();

        DiscoveryManager.getInstance().stop();
    }

    @Override
    protected void onDestroy() {
        if (_castService != null) {
            _castService = null;
            unbindService(_serviceConnection);
        }

        LocalBroadcastManager
                .getInstance(MainActivity.this)
                .unregisterReceiver(_broadcastReceiver);

        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            setIntent(intent);
            loadMedia();
        }
    }

    @Override
    public void onBackPressed() {
        if (_deviceLayout.isShown()) {
            toggleDeviceMenu(false);
        }
        else {
            super.onBackPressed();
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        _connectItem = menu.findItem(R.id.action_connect);
        _refreshItem = menu.findItem(R.id.action_refresh);

        if (!_usageTextView.isShown()) {
            _refreshItem.setVisible(true);
        }

        updateConnectItem();

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

    private void setupViews() {
        setContentView(R.layout.activity_main);

        _seekBarLayout = findViewById(R.id.seek_bar_layout);
        _mediaController = findViewById(R.id.media_controller);
        _titleTextView = (TextView)findViewById(R.id.title_text_view);
        _usageTextView = findViewById(R.id.usage_text_view);
        _deviceLayout = findViewById(R.id.device_layout);
        _currentTimeTextView = (TextView)findViewById(R.id.current_time_text_view);
        _durationTextView = (TextView)findViewById(R.id.duration_text_view);
        _mediaProgressBar = findViewById(R.id.media_progress_bar);

        // Tabs
        _tabHost = (TabHost)findViewById(android.R.id.tabhost);
        _tabHost.setup();

        View videoTabIndicator = getLayoutInflater().inflate(R.layout.media_tab_indicator, _tabHost.getTabWidget(), false);
        TextView videoTitle = (TextView)videoTabIndicator.findViewById(android.R.id.title);
        videoTitle.setText("VIDEO");

        TabHost.TabSpec videoTab = _tabHost.newTabSpec("Video").setContent(R.id.video_scroll_view);
        videoTab.setIndicator(videoTabIndicator);
        _tabHost.addTab(videoTab);

        View audioTabIndicator = getLayoutInflater().inflate(R.layout.media_tab_indicator, _tabHost.getTabWidget(), false);
        TextView audioTitle = (TextView) audioTabIndicator.findViewById(android.R.id.title);
        audioTitle.setText("AUDIO");

        TabHost.TabSpec audioTab = _tabHost.newTabSpec("Audio").setContent(R.id.audio_scroll_view);
        audioTab.setIndicator(audioTabIndicator);
        _tabHost.addTab(audioTab);

        // Device list
        _deviceListView = (ListView)findViewById(R.id.device_list_view);
        _deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                ConnectableDevice device = (ConnectableDevice) parent.getItemAtPosition(position);
                boolean sameDeviceClicked = _device != null && _device.getId().equals(device.getId());

                disconnectDevice();
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();

                if (sameDeviceClicked) {
                    editor.remove("lastDevice").apply();
                }
                else {
                    connectDevice(device);
                    editor.putString("lastDevice", _device.getId()).apply();
                }

                toggleDeviceMenu(false);
            }
        });

        // Media lists
        AdapterView.OnItemClickListener mediaClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                _seekBarLayout.setVisibility(View.GONE);

                MediaAdapter mediaAdapter = (MediaAdapter)parent.getAdapter();
                MediaInfo mediaInfo = mediaAdapter.getMediaInfo(position);
                if (mediaInfo.equals(mediaAdapter.getPlayingMedia())) {
                    if (_device != null) {
                        stop();
                    }
                } else {
                    play(mediaInfo);
                }
            }
        };

        _videoListView = (ListView)findViewById(R.id.video_list_view);
        _videoListView.setOnItemClickListener(mediaClickListener);

        _audioListView = (ListView)findViewById(R.id.audio_list_view);
        _audioListView.setOnItemClickListener(mediaClickListener);

        // Media buttons
        ImageButton playButton = (ImageButton) findViewById(R.id.play_button);
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                _castService.play();
            }
        });

        ImageButton pauseButton = (ImageButton) findViewById(R.id.pause_button);
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                _castService.pause();
            }
        });

        ImageButton stopButton = (ImageButton) findViewById(R.id.stop_button);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stop();
            }
        });

        // Seek bar
        _seekBar = (SeekBar)findViewById(R.id.seek_bar);
        _seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean byUser) {
                long newPosition = (_mediaDuration * progress) / 1000;
                _currentTimeTextView.setText(stringForTime(newPosition));
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
    }

    private void bindCastService() {
        _serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder binder) {
                CastServiceBinder castServiceBinder = (CastServiceBinder) binder;
                _castService = castServiceBinder.getCastService();
                _castService.setLaunchListener(new MediaPlayer.LaunchListener() {
                    @Override
                    public void onSuccess(final MediaPlayer.MediaLaunchObject mediaLaunchObject) {
                        _playRequested = false;

                        if (_castService != null) {
                            MediaInfo mediaInfo = _castService.getPlayingMediaInfo();
                            setMediaIndicator(mediaInfo);
                            showMediaControls(mediaLaunchObject.mediaControl);
                        }
                    }

                    @Override
                    public void onError(ServiceCommandError error) {
                        _playRequested = false;
                        error.printStackTrace();
                    }
                });

                _deviceAdapter = _castService.getDeviceAdapter();
                _deviceAdapter.setDeviceAddedListener(new DeviceAddedListener() {
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

                _videoAdapter = _castService.getVideoAdapter();
                _audioAdapter = _castService.getAudioAdapter();

                _deviceListView.setAdapter(_deviceAdapter);
                _videoListView.setAdapter(_videoAdapter);
                _audioListView.setAdapter(_audioAdapter);

                ConnectableDevice playingDevice = _castService.getPlayingDevice();
                if (playingDevice != null) {
                    connectDevice(playingDevice);
                    updateConnectItem();
                    showMediaControls(_device.getMediaControl());
                    updateMediaTabs();

                    String title = _castService.getTitle();
                    _titleTextView.setText(title);
                    _titleTextView.setVisibility(View.VISIBLE);

                    MediaInfo mediaInfo = _castService.getPlayingMediaInfo();
                    setMediaIndicator(mediaInfo);
                }
                else {
                    loadMedia();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                _castService = null;
            }
        };

        Intent startIntent = new Intent(CastService.ACTION_START, null, getApplicationContext(), CastService.class);
        startService(startIntent);

        Intent bindIntent = new Intent(MainActivity.this, CastService.class);
        bindService(bindIntent, _serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setupBroadcastReceiver() {
        _broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (CastService.ACTION_NOTIFICATION_STOP.equals(action)) {
                    stop();
                }
            }
        };

        LocalBroadcastManager
                .getInstance(MainActivity.this)
                .registerReceiver(_broadcastReceiver, new IntentFilter(CastService.ACTION_NOTIFICATION_STOP));
    }

    private void loadMedia() {
        if (_refreshItem != null) {
            _refreshItem.setVisible(false);
        }

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        String pageTitle = preferences.getString("pageTitle", null);
        String pageUrl = preferences.getString("pageUrl", null);

        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            pageTitle = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            pageUrl = intent.getStringExtra(Intent.EXTRA_TEXT);
        }

        if (pageUrl == null) {
            _usageTextView.setVisibility(View.VISIBLE);
        }
        else {
            preferences.edit()
                    .putString("pageTitle", pageTitle)
                    .putString("pageUrl", pageUrl)
                    .apply();

            _videoAdapter.clear();
            _audioAdapter.clear();

            setMediaIndicator(null);

            _usageTextView.setVisibility(View.GONE);
            _tabHost.setVisibility(View.GONE);
            _mediaProgressBar.setVisibility(View.VISIBLE);

            _titleTextView.setText(pageTitle);
            _titleTextView.setVisibility(View.VISIBLE);

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
                    } else if (mediaInfo.format.startsWith("audio/")) {
                        _audioAdapter.addMediaInfo(mediaInfo);
                    }
                }

                @Override
                public void finished(final int mediaFound) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (_refreshItem != null) {
                                _refreshItem.setVisible(true);
                            }

                            _mediaProgressBar.setVisibility(View.GONE);
                            updateMediaTabs();
                        }
                    });
                }
            });
        }
    }

    private String stringForTime(long timeMs) {
        long totalSeconds = timeMs / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = totalSeconds / 3600;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%02d:%02d", minutes, seconds);
    }

    private void play(MediaInfo mediaInfo) {
        setMediaIndicator(mediaInfo);

        if (_device == null) {
            toggleDeviceMenu(true);
            return;
        }

        if (mediaInfo != null) {
            if (_device instanceof LocalDevice) {
                Intent intent = new Intent(MainActivity.this, MediaPlayerActivity.class);
                intent.putExtra("mediaUrl", mediaInfo.url);
                startActivity(intent);
                setMediaIndicator(null);
            }
            else {
                _playRequested = true;
                String pageTitle = _titleTextView.getText().toString();
                _castService.play(_device, mediaInfo, pageTitle);
                showMediaControls(_device.getMediaControl());
            }
        }
    }

    private void stop() {
        if (!_playRequested) {
            _seekBarLayout.setVisibility(View.GONE);
            _mediaController.setVisibility(View.GONE);
            setMediaIndicator(null);

            if (_timer != null) {
                _timer.cancel();
            }

            if (_castService != null) {
                _castService.stop();
            }
        }
    }

    private void setMediaIndicator(MediaInfo mediaInfo) {
        _videoAdapter.setPlayingMediaInfo(mediaInfo);
        _audioAdapter.setPlayingMediaInfo(mediaInfo);

        TabWidget tabWidget = _tabHost.getTabWidget();
        if (mediaInfo != null) {
            int tabIndex = mediaInfo.format.startsWith("video/") ? 0 : 1;
            View tabIndicator = tabWidget.getChildTabViewAt(tabIndex);
            tabIndicator.findViewById(R.id.playing_image_view).setVisibility(View.VISIBLE);
        }
        else {
            for (int tabIndex = 0; tabIndex < tabWidget.getTabCount(); tabIndex++ ) {
                View tabIndicator = tabWidget.getChildTabViewAt(tabIndex);
                tabIndicator.findViewById(R.id.playing_image_view).setVisibility(View.GONE);
            }
        }
    }

    private void updateConnectItem() {
        if (_connectItem != null) {
            if (_device == null || _device instanceof LocalDevice) {
                _connectItem.setIcon(R.drawable.ic_media_route_off_holo_light);
                _connectItem.setTitle(R.string.cast);
            }
            else {
                _connectItem.setIcon(R.drawable.ic_media_route_on_holo_light);
                _connectItem.setTitle(_device.getModelName());
            }
        }
    }

    private void updateMediaTabs() {
        boolean videoFound =  _videoAdapter.getCount() > 0;
        boolean audioFound =  _audioAdapter.getCount() > 0;

        findViewById(R.id.no_video_text_view).setVisibility(videoFound ? View.GONE : View.VISIBLE);
        findViewById(R.id.no_audio_text_view).setVisibility(audioFound ? View.GONE : View.VISIBLE);

        _tabHost.setCurrentTab(!videoFound && audioFound ? 1 : 0);
        _tabHost.setVisibility(View.VISIBLE);
    }

    private void toggleDeviceMenu(boolean keepOpen) {
        _deviceLayout.setVisibility(_deviceLayout.isShown() && !keepOpen ? View.GONE : View.VISIBLE);
    }

    private void showMediaControls(final MediaControl mediaControl) {
        _seekBarLayout.setVisibility(View.GONE);
        _mediaController.setVisibility(View.VISIBLE);
        _currentTimeTextView.setText(R.string.zero_time);
        _durationTextView.setText(R.string.zero_time);
        _seekBar.setProgress(0);

        if (_device.hasCapability(MediaControl.Position)) {
            _timer = new Timer();
            _timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    getPlayerPosition(mediaControl);
                }
            }, 1000, 1000);

            getMediaDuration(mediaControl);
        }
    }

    private void connectDevice(ConnectableDevice device) {
        _device = device;

        if (device.hasCapability(MediaControl.PlayState_Subscribe)) {
            final MediaControl mediaControl = device.getMediaControl();
            mediaControl.subscribePlayState(new MediaControl.PlayStateListener() {
                @Override
                public void onSuccess(final MediaControl.PlayStateStatus playState) {
                    if (playState == MediaControl.PlayStateStatus.Finished ||
                            playState == MediaControl.PlayStateStatus.Idle ||
                            playState == MediaControl.PlayStateStatus.Unknown) {
                        stop();
                    }
                    else if (!_mediaController.isShown()) {
                        showMediaControls(mediaControl);
                    }
                }

                @Override
                public void onError(ServiceCommandError error) {
                    error.printStackTrace();
                }
            });
        }

        device.getListeners().clear();
        device.addListener(new ConnectableDeviceListener() {
            @Override
            public void onDeviceReady(ConnectableDevice device) {

                updateConnectItem();
                _deviceAdapter.notifyDataSetChanged();

                MediaInfo mediaInfo = _castService.getPlayingMediaInfo();
                if (mediaInfo != null) {
                    _playRequested = false;
                    showMediaControls(device.getMediaControl());
                    setMediaIndicator(mediaInfo);
                } else {
                    // Play media that may be queued
                    play(_videoAdapter.getPlayingMedia());
                }
            }

            @Override
            public void onDeviceDisconnected(ConnectableDevice device) {
                disconnectDevice();
            }

            @Override
            public void onPairingRequired(ConnectableDevice device, DeviceService service, DeviceService.PairingType pairingType) {
                if (DeviceService.PairingType.PIN_CODE.equals(pairingType)) {
                    PairingDialog dialog = new PairingDialog(MainActivity.this, _device);
                    dialog.getPairingDialog("Enter pairing code").show();
                }
            }

            @Override
            public void onCapabilityUpdated(ConnectableDevice device, List<String> added, List<String> removed) {
            }

            @Override
            public void onConnectionFailed(ConnectableDevice device, ServiceCommandError error) {
                disconnectDevice();
            }
        });

        if (!device.isConnected()) {
            _device.connect();
        }
    }

    private void disconnectDevice() {
        if (_device != null) {
            stop();

            _device.getListeners().clear();
            _device.disconnect();
            _device = null;

            updateConnectItem();
            _deviceAdapter.notifyDataSetChanged();
        }
    }
    private void getMediaDuration(MediaControl mediaControl) {
        mediaControl.getDuration(new MediaControl.DurationListener() {
            @Override
            public void onSuccess(Long duration) {
                if (duration > 0) {
                    _mediaDuration = duration;
                    _currentTimeTextView.setText(stringForTime(0));
                    _durationTextView.setText(stringForTime(duration));
                    _seekBarLayout.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(ServiceCommandError error) {
                error.printStackTrace();
            }
        });
    }

    private void getPlayerPosition(MediaControl mediaControl) {
        mediaControl.getPosition(new MediaControl.PositionListener() {
            @Override
            public void onSuccess(Long position) {
                if (_mediaDuration > 0) {
                    double progress = (position / (double) _mediaDuration) * 1000;
                    _currentTimeTextView.setText(stringForTime(position));
                    _seekBar.setProgress((int) progress);
                }
            }

            @Override
            public void onError(ServiceCommandError error) {
                error.printStackTrace();
            }
        });
    }
}
