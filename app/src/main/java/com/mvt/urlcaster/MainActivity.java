package com.mvt.urlcaster;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.device.ConnectableDeviceListener;
import com.connectsdk.device.PairingDialog;
import com.connectsdk.discovery.CapabilityFilter;
import com.connectsdk.discovery.DiscoveryManager;
import com.connectsdk.discovery.DiscoveryManagerListener;
import com.connectsdk.service.DeviceService;
import com.connectsdk.service.capability.MediaControl;
import com.connectsdk.service.capability.MediaPlayer;
import com.connectsdk.service.capability.VolumeControl;
import com.connectsdk.service.capability.listeners.ResponseListener;
import com.connectsdk.service.command.ServiceCommandError;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends ActionBarActivity implements ConnectableDeviceListener, DiscoveryManagerListener {

    private static final String TAG = "MainActivity";
    private ConnectableDevice _device;
    private AlertDialog _pairingDialog;
    private MenuItem _connectItem;
    private long _videoDuration;
    private MediaControl.PlayStateStatus _playState;
    private DeviceAdapter _deviceAdapter;
    private VideoAdapter _videoAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        final View mediaController = findViewById(R.id.media_controller);
        mediaController.setVisibility(View.GONE);

        //final ProgressBar playProgressBar = (ProgressBar)findViewById(R.id.play_progess_bar);
        final ImageButton playButton = (ImageButton)findViewById(R.id.play_button);
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (_device != null) {
                    if (_playState == MediaControl.PlayStateStatus.Paused || _playState == MediaControl.PlayStateStatus.Idle) {
                        //playButton.setVisibility(View.GONE);
                        //playProgressBar.setVisibility(View.VISIBLE);

                        _device.getMediaControl().play(new ResponseListener<Object>() {
                            @Override
                            public void onSuccess(Object object) {
                                //playProgressBar.setVisibility(View.GONE);
                                //playButton.setVisibility(View.VISIBLE);
                                playButton.setBackgroundResource(android.R.drawable.ic_media_pause);
                                _playState = MediaControl.PlayStateStatus.Playing;
                            }

                            @Override
                            public void onError(ServiceCommandError error) {
                                //playProgressBar.setVisibility(View.GONE);
                                //playButton.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                    else if (_playState == MediaControl.PlayStateStatus.Playing){
                        //playButton.setVisibility(View.GONE);
                        //playProgressBar.setVisibility(View.VISIBLE);

                        _device.getMediaControl().pause(new ResponseListener<Object>() {
                            @Override
                            public void onSuccess(Object object) {
                                //playProgressBar.setVisibility(View.GONE);
                                //playButton.setVisibility(View.VISIBLE);
                                playButton.setBackgroundResource(android.R.drawable.ic_media_play);
                                _playState = MediaControl.PlayStateStatus.Paused;
                            }

                            @Override
                            public void onError(ServiceCommandError error) {
                                //playProgressBar.setVisibility(View.GONE);
                                //playButton.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                }
            }
        });

        playButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (_device != null) {
                    _device.getMediaControl().stop(null);
                    mediaController.setVisibility(View.GONE);
                }

                return true;
            }
        });

        final TextView currentTime = (TextView)findViewById(R.id.time_current);
        final SeekBar seekBar = (SeekBar)findViewById(R.id.seek_bar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean byUser) {
                final long newPosition = (_videoDuration * progress) / 1000;
                currentTime.setText(stringForTime(newPosition));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                long newPosition = (_videoDuration * progress) / 1000L;
                _device.getMediaControl().seek(newPosition, null);
            }
        });

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (_device != null && _playState == MediaControl.PlayStateStatus.Playing) {
                    _device.getMediaControl().getPosition(new MediaControl.PositionListener() {
                        @Override
                        public void onSuccess(Long position) {
                            if (_videoDuration > 0) {
                                double progress = (position / (double) _videoDuration) * 1000;
                                currentTime.setText(stringForTime(position));
                                seekBar.setProgress((int)progress);
                            }
                        }

                        @Override
                        public void onError(ServiceCommandError error) {}
                    });
                }
            }
        }, 1000, 1000);

        DiscoveryManager.init(getApplicationContext());
        DiscoveryManager discoveryManager = DiscoveryManager.getInstance();
        discoveryManager.setCapabilityFilters(new CapabilityFilter(MediaPlayer.Display_Video));
        discoveryManager.setPairingLevel(DiscoveryManager.PairingLevel.ON);
        discoveryManager.addListener(MainActivity.this);
        discoveryManager.start();

        /*ConnectivityManager connManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if (wifiInfo.isConnected()) {
            discoveryManager.start();
        }*/

        _deviceAdapter = new DeviceAdapter(MainActivity.this);

        final ListView deviceListView = (ListView)findViewById(R.id.device_list_view);
        deviceListView.setAdapter(_deviceAdapter);
        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                ConnectableDevice device = (ConnectableDevice)parent.getItemAtPosition(position);
                boolean sameDeviceClicked = _device != null && _device.getId().equals(device.getId());

                disconnectDevice();
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();
                editor.remove("lastDevice").commit();

                if (!sameDeviceClicked) {
                    if (device.getId().equals("local")) {
                        _device = device;
                        playQueuedVideo();
                    }
                    else {
                        connectDevice(device);
                        editor.putString("lastDevice", _device.getId()).commit();
                    }
                }

                toggleDeviceMenu(false);
            }
        });

        _pairingDialog = new AlertDialog.Builder(this)
            .setTitle("Pairing with TV")
            .setMessage("Please confirm the connection on your TV")
            .setPositiveButton("OK", null)
            .setNegativeButton("Cancel", null)
            .create();

        _videoAdapter = new VideoAdapter();

        ListView videoListView = (ListView)findViewById(R.id.video_list_view);
        videoListView.setAdapter(_videoAdapter);
        videoListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                _videoAdapter.setPlayingVideoInfo(i);
                playQueuedVideo();
            }
        });

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        String pageUrl = preferences.getString("pageUrl", null);

        Intent intent = getIntent();
        if (intent.getAction().equals(Intent.ACTION_SEND)) {
            pageUrl = intent.getStringExtra(Intent.EXTRA_TEXT);
        }

        if (pageUrl != null) {
            findViewById(R.id.loader_progress_bar).setVisibility(View.VISIBLE);
            preferences.edit().putString("pageUrl", pageUrl).commit();

            VideoScraper videoScraper = new VideoScraper();
            videoScraper.scrape(MainActivity.this, pageUrl, 2, new VideoScraperListener() {
                @Override
                public void videoFound(VideoInfo videoInfo) {
                    _videoAdapter.addVideoInfo(videoInfo);
                }

                @Override
                public void pageTitleFound(final String pageTitle) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView titleTextView = (TextView) findViewById(R.id.title_text_view);
                            if (titleTextView.getText().equals("")) {
                                titleTextView.setText(Html.fromHtml(pageTitle).toString());
                            }
                        }
                    });
                }

                @Override
                public void finished(final int videosFound) {
                    Log.i(TAG, "All done!");

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            findViewById(R.id.loader_progress_bar).setVisibility(View.GONE);

                            if (videosFound == 0) {
                                TextView titleTextView = (TextView) findViewById(R.id.title_text_view);
                                titleTextView.setText(R.string.no_videos);
                            }
                        }
                    });
                }
            });
        }
        else {
            TextView titleTextView = (TextView) findViewById(R.id.title_text_view);
            titleTextView.setText(R.string.usage);
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
    protected void onPause() {
        super.onPause();
        DiscoveryManager.getInstance().stop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        DiscoveryManager.getInstance().start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        _connectItem = menu.getItem(0);
        return true;
    }

    private void playQueuedVideo() {

        if (_device == null) {
            toggleDeviceMenu(true);
            return;
        }

        VideoInfo videoInfo = _videoAdapter.getPlayingVideo();
        if (videoInfo != null) {
            if (_device.getId().equals("local")) {
                Intent intent = new Intent();
                intent.setClass(MainActivity.this, VideoActivity.class);
                intent.putExtra("videoUrl", videoInfo.url);
                startActivity(intent);
            }
            else {
                //findViewById(R.id.play_button).setVisibility(View.GONE);
                findViewById(R.id.seek_bar_layout).setVisibility(View.GONE);
                //findViewById(R.id.play_progess_bar).setVisibility(View.VISIBLE);
                findViewById(R.id.media_controller).setVisibility(View.VISIBLE);

                final MediaControl mediaControl = _device.getMediaControl();
                MediaPlayer mediaPlayer = _device.getMediaPlayer();
                if (mediaPlayer != null) {
                    mediaPlayer.playMedia(videoInfo.url, videoInfo.format, videoInfo.title, null, null, false, new MediaPlayer.LaunchListener() {
                        public void onSuccess(MediaPlayer.MediaLaunchObject object) {
                            //findViewById(R.id.play_button).setVisibility(View.VISIBLE);
                            //findViewById(R.id.play_progess_bar).setVisibility(View.GONE);

                            mediaControl.subscribePlayState(new MediaControl.PlayStateListener() {
                                @Override
                                public void onSuccess(MediaControl.PlayStateStatus playState) {
                                    _playState = playState;

                                    if (_playState == MediaControl.PlayStateStatus.Finished) {
                                        findViewById(R.id.media_controller).setVisibility(View.GONE);
                                    }
                                }

                                @Override
                                public void onError(ServiceCommandError error) {
                                    _playState = MediaControl.PlayStateStatus.Playing;
                                }
                            });

                            if (_device.hasCapability(MediaControl.Seek) &&
                                    _device.hasCapability(MediaControl.Duration)) {
                                mediaControl.getDuration(new MediaControl.DurationListener() {
                                    @Override
                                    public void onSuccess(Long duration) {
                                        _videoDuration = duration;

                                        TextView currentTime = (TextView) findViewById(R.id.time_current);
                                        currentTime.setText(stringForTime(0));

                                        TextView time = (TextView) findViewById(R.id.time);
                                        time.setText(stringForTime(_videoDuration));

                                        findViewById(R.id.seek_bar_layout).setVisibility(View.VISIBLE);
                                    }

                                    @Override
                                    public void onError(ServiceCommandError error) {
                                    }
                                });
                            }
                        }

                        @Override
                        public void onError(ServiceCommandError error) {
                            findViewById(R.id.media_controller).setVisibility(View.GONE);
                        }
                    });
                }
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_connect:
                toggleDeviceMenu(false);
                return true;

            case R.id.action_credits:

                new AlertDialog.Builder(this)
                    .setTitle(R.string.credits)
                    .setMessage(R.string.credits_message)
                    .show();

                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void toggleDeviceMenu(boolean keepOpen) {
        final View deviceLayout = findViewById(R.id.device_layout);
        final boolean visible = deviceLayout.getVisibility() == View.VISIBLE;
        if (!visible || !keepOpen) {
            int animation = visible ? R.anim.slide_up : R.anim.slide_down;
            Animation slide = AnimationUtils.loadAnimation(MainActivity.this, animation);
            slide.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    if (!visible) {
                        deviceLayout.setVisibility(View.VISIBLE);
                    }
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    if (visible) {
                        deviceLayout.setVisibility(View.GONE);
                    }
                }
                @Override
                public void onAnimationRepeat(Animation animation) {}
            });

            findViewById(R.id.main_layout).startAnimation(slide);
        }
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
            _playState = null;

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    _connectItem.setIcon(R.drawable.ic_media_route_off_holo_light);
                    _connectItem.setTitle(R.string.cast);

                    _deviceAdapter.notifyDataSetChanged();

                    findViewById(R.id.media_controller).setVisibility(View.GONE);
                }
            });
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
    public void onDeviceAdded(DiscoveryManager manager, ConnectableDevice device) {
        Log.i(TAG, "onDeviceAdded: " + device.getFriendlyName());

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!_connectItem.isEnabled()) {
                    _connectItem.setIcon(R.drawable.ic_media_route_off_holo_light);
                }
            }
        });

        if (_device == null || !_device.isConnected()) {
            String lastDevice = PreferenceManager
                    .getDefaultSharedPreferences(MainActivity.this)
                    .getString("lastDevice", null);
            if (device.getId().equals(lastDevice)) {
                connectDevice(device);
            }
        }
    }

    @Override
    public void onDeviceRemoved(DiscoveryManager manager, ConnectableDevice device) {

    }

    @Override
    public void onDeviceUpdated(DiscoveryManager manager, ConnectableDevice device) {

    }

    @Override
    public void onDiscoveryFailed(DiscoveryManager manager, ServiceCommandError error) {

    }

    @Override
    public void onPairingRequired(ConnectableDevice device, DeviceService service, DeviceService.PairingType pairingType) {
        Log.d(TAG, "Connected to " + _device.getIpAddress());

        switch (pairingType) {
            case FIRST_SCREEN:
                Log.d(TAG, "First Screen");
                _pairingDialog.show();
                break;

            case PIN_CODE:
                Log.d(TAG, "Pin Code");
                PairingDialog dialog = new PairingDialog(MainActivity.this, _device);
                dialog.getPairingDialog("Enter Pairing Code on TV").show();
                break;

            case NONE:
            default:
                break;
        }
    }

    @Override
    public void onConnectionFailed(ConnectableDevice device, ServiceCommandError error) {
        Log.d(TAG, "Failed to connect to " + device.getIpAddress());
        disconnectDevice();
    }

    @Override
    public void onDeviceReady(final ConnectableDevice device) {
        if (_pairingDialog.isShowing() ) {
            Log.d(TAG, "onPairingSuccess");
            _pairingDialog.dismiss();
        }

        Log.d(TAG, "successful register");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                _connectItem.setIcon(R.drawable.ic_media_route_on_holo_light);
                _connectItem.setTitle(device.getModelName());

                _deviceAdapter.notifyDataSetChanged();


                playQueuedVideo();
            }
        });
    }

    @Override
    public void onDeviceDisconnected(ConnectableDevice device) {
        Log.d(TAG, "Device Disconnected");

        if (_pairingDialog.isShowing() ) {
            _pairingDialog.dismiss();
        }

        disconnectDevice();
    }

    @Override
    public void onCapabilityUpdated(ConnectableDevice device, List<String> added, List<String> removed) {}
}