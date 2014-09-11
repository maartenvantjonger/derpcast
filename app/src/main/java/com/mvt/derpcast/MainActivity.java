package com.mvt.derpcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ExpandableListView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

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
    private DeviceAdapter _deviceAdapter;
    private MediaAdapter _mediaAdapter;
    private long _mediaDuration;
    private boolean _playRequested;
    private BroadcastReceiver _broadcastReceiver;
    private static final String MEDIA_LOGO_URL = "https://googledrive.com/host/0BzRo13oMy82cbEJRSHM3VEVyUWc/app_logo.png";
    private static final String MEDIA_VIDEO_ART_URL = "https://googledrive.com/host/0BzRo13oMy82cbEJRSHM3VEVyUWc/video_art.png";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        ImageButton playButton = (ImageButton)findViewById(R.id.play_button);
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (_device != null) {
                    _device.getMediaControl().play(null);
                }
            }
        });

        ImageButton pauseButton = (ImageButton)findViewById(R.id.pause_button);
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (_device != null) {
                    _device.getMediaControl().pause(null);
                }
            }
        });

        ImageButton stopButton = (ImageButton)findViewById(R.id.stop_button);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (_device != null) {
                    _device.getMediaControl().stop(null);
                    stopPlaying();
                }
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
                _device.getMediaControl().seek(newPosition, null);
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

        final ListView deviceListView = (ListView)findViewById(R.id.device_list_view);
        deviceListView.setAdapter(_deviceAdapter);
        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                ConnectableDevice device = (ConnectableDevice)parent.getItemAtPosition(position);
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

        _mediaAdapter = new MediaAdapter();

        final ExpandableListView mediaListView = (ExpandableListView)findViewById(R.id.media_list_view);
        mediaListView.setAdapter(_mediaAdapter);
        mediaListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {

                findViewById(R.id.seek_bar_layout).setVisibility(View.GONE);

                MediaInfo mediaInfo = _mediaAdapter.getMediaInfo(groupPosition, childPosition);
                if (mediaInfo.equals(_mediaAdapter.getPlayingMedia())) {
                    if (_device != null) {
                        _device.getMediaControl().stop(null);
                        stopPlaying();
                    }
                } else {
                    _mediaAdapter.setPlayingMediaInfo(mediaInfo);
                    playMedia();
                }

                return false;
            }
        });

        _broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Toast.makeText(MainActivity.this, "MainActivity action: " + intent.getAction(), Toast.LENGTH_LONG).show();
            }
        };
        registerReceiver(_broadcastReceiver, new IntentFilter("com.mvt.derpcast.action.test"));

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        String pageUrl = preferences.getString("pageUrl", null);

        Intent intent = getIntent();
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) {
            pageUrl = intent.getStringExtra(Intent.EXTRA_TEXT);
        }

        if (pageUrl != null) {
            findViewById(R.id.loader_progress_bar).setVisibility(View.VISIBLE);
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
                    _mediaAdapter.addMediaInfo(mediaInfo);
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
                            findViewById(R.id.loader_progress_bar).setVisibility(View.GONE);

                            if (mediaFound == 0) {
                                TextView errorTextView = (TextView) findViewById(R.id.error_text_view);
                                errorTextView.setText(R.string.no_media);
                                errorTextView.setVisibility(View.VISIBLE);
                            }
                            else {
                                int groupCount = _mediaAdapter.getGroupCount();
                                for (int i = 0; i < groupCount; i++) {
                                    mediaListView.expandGroup(i);
                                }
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

    @Override
    protected void onDestroy() {
        unregisterReceiver(_broadcastReceiver);
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        _connectItem = menu.getItem(0);
        return true;
    }

    private void playMedia() {
        if (_device == null) {
            toggleDeviceMenu(true);
            return;
        }

        final MediaInfo mediaInfo = _mediaAdapter.getPlayingMedia();
        if (mediaInfo != null) {
            if (_device instanceof LocalDevice) {
                Intent intent = new Intent(MainActivity.this, MediaPlayerActivity.class);
                intent.putExtra("mediaUrl", mediaInfo.url);
                startActivity(intent);

                _mediaAdapter.setPlayingMediaInfo(null);
            }
            else {
                playMedia(_device.getMediaPlayer(), mediaInfo);
            }
        }
    }

    private void playMedia(MediaPlayer mediaPlayer, MediaInfo mediaInfo) {
        _playRequested = true;

        initializeMediaController();

        String pageTitle = ((TextView)findViewById(R.id.title_text_view)).getText().toString();
        String imageUrl = mediaInfo.format.startsWith("video/") ? MEDIA_VIDEO_ART_URL : MEDIA_LOGO_URL;

        mediaPlayer.playMedia(mediaInfo.url, mediaInfo.format, pageTitle, mediaInfo.title, imageUrl, false, new MediaPlayer.LaunchListener() {
            @Override
            public void onSuccess(MediaPlayer.MediaLaunchObject object) {
                _playRequested = false;
            }

            @Override
            public void onError(ServiceCommandError error) {
                _playRequested = false;
                error.printStackTrace();
            }
        });
    }

    private void stopPlaying() {
        if (!_playRequested) {
            _mediaAdapter.setPlayingMediaInfo(null);
        }

        findViewById(R.id.media_controller).setVisibility(View.GONE);

        stopService(new Intent(MainActivity.this, RemoteControlService.class));
    }

    private void initializeMediaController() {
        if (_device != null) {
            findViewById(R.id.media_controller).setVisibility(View.VISIBLE);

            if (_device.hasCapability(MediaControl.Seek) &&
                _device.hasCapability(MediaControl.Duration)) {

                MediaControl mediaControl = _device.getMediaControl();
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

            MediaInfo mediaInfo = _mediaAdapter.getPlayingMedia();
            if (mediaInfo != null && _device.hasCapability(MediaControl.PlayState_Subscribe)) {
                String pageTitle = ((TextView) findViewById(R.id.title_text_view)).getText().toString();
                Intent intent = new Intent(RemoteControlService.ACTION_PLAY);
                intent.putExtra("title", pageTitle);
                intent.putExtra("description", mediaInfo.title);
                startService(intent);
            }
        }
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
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_connect:
                toggleDeviceMenu(false);
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
            _device.removeListener(MainActivity.this);
            _device.disconnect();
            _device = null;

            stopPlaying();

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
            mediaControl.subscribePlayState(new MediaControl.PlayStateListener() {
                @Override
                public void onSuccess(final MediaControl.PlayStateStatus playState) {
                    if (playState == MediaControl.PlayStateStatus.Finished ||
                        playState == MediaControl.PlayStateStatus.Idle ||
                        playState == MediaControl.PlayStateStatus.Unknown) {

                        stopPlaying();
                    }
                    else if (!findViewById(R.id.media_controller).isShown()) {
                        initializeMediaController();
                    }
                }

                @Override
                public void onError(ServiceCommandError error) {
                    error.printStackTrace();
                }
            });
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