package com.mvt.derpcast.activities;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import com.mvt.derpcast.R;
import com.mvt.derpcast.castservice.CastService;
import com.mvt.derpcast.castservice.CastServiceBinder;
import com.mvt.derpcast.device.DeviceAdapter;
import com.mvt.derpcast.device.DeviceAddedListener;
import com.mvt.derpcast.media.MediaAdapter;
import com.mvt.derpcast.media.MediaInfo;
import com.mvt.derpcast.media.MediaScraper;
import com.mvt.derpcast.media.MediaScraperListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity implements
        View.OnClickListener, SwipeRefreshLayout.OnRefreshListener {

    private ServiceConnection mServiceConnection;
    private CastService mCastService;
    private ConnectableDevice mDevice;
    private DeviceAdapter mDeviceAdapter;
    private BroadcastReceiver mBroadcastReceiver;
    private Timer mTimer;

    private long mMediaDuration;
    private boolean mPlayRequested;

    private ImageView mCastImageView;
    private SeekBar mSeekBar;
    private View mSeekBarLayout;
    private View mMediaController;
    private TextView mHeaderTextView;
    private TextView mCurrentTimeTextView;
    private TextView mDurationTextView;
    private SwipeRefreshLayout mRefreshContainer;
    private ListView mDeviceListView;
    private ListView mMediaListView;

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
        if (mCastService != null) {
            mCastService = null;
            unbindService(mServiceConnection);
        }

        LocalBroadcastManager
                .getInstance(MainActivity.this)
                .unregisterReceiver(mBroadcastReceiver);

        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            setIntent(intent);

            if (mServiceConnection != null) {
                loadMedia();
            }
            else {
                bindCastService();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mDeviceListView.getVisibility() == View.VISIBLE) {
            toggleDeviceMenu(false);
        }
        else {
            super.onBackPressed();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.title_text_view:
            case R.id.cast_image_view:
                toggleDeviceMenu(false);
                break;
            case R.id.play_button:
                mCastService.play();
                break;
            case R.id.pause_button:
                mCastService.pause();
                break;
            case R.id.stop_button:
                stop();
                break;
        }
    }

    @Override
    public void onRefresh() {
        loadMedia();
    }

    @Override
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (mDevice != null && mDevice.isConnected() && mDevice.hasCapability(VolumeControl.Volume_Up_Down)) {
                VolumeControl volumeControl = mDevice.getVolumeControl();
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

    private void setupViews() {
        setContentView(R.layout.activity_main);

        // Toolbar
        View titleTextView = findViewById(R.id.title_text_view);
        titleTextView.setOnClickListener(this);

        mCastImageView = (ImageView) findViewById(R.id.cast_image_view);
        mCastImageView.setOnClickListener(this);

        // Device list
        mDeviceListView = (ListView) findViewById(R.id.device_list_view);
        mDeviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ConnectableDevice device = (ConnectableDevice) parent.getItemAtPosition(position);
                boolean sameDeviceClicked = mDevice != null && mDevice.getId().equals(device.getId());

                disconnectDevice();
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();

                if (sameDeviceClicked) {
                    editor.remove("lastDevice").apply();
                } else {
                    connectDevice(device);
                    editor.putString("lastDevice", mDevice.getId()).apply();
                }

                toggleDeviceMenu(false);
            }
        });

        // Media lists
        mRefreshContainer = (SwipeRefreshLayout) findViewById(R.id.refresh_container);
        mRefreshContainer.setOnRefreshListener(this);

        AdapterView.OnItemClickListener mediaClickListener = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    mSeekBarLayout.setVisibility(View.GONE);

                    MediaAdapter mediaAdapter = mCastService.getMediaAdapter();
                    MediaInfo mediaInfo = mediaAdapter.getMediaInfo(position - 1); // -1 for header
                    if (mediaInfo.equals(mediaAdapter.getPlayingMedia())) {
                        if (mDevice != null) {
                            stop();
                        }
                    } else {
                        play(mediaInfo);
                    }
                }
            }
        };

        AdapterView.OnItemLongClickListener mediaLongClickListener = new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                MediaAdapter mediaAdapter = mCastService.getMediaAdapter();
                MediaInfo mediaInfo = mediaAdapter.getMediaInfo(position - 1); // -1 for header

                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("url", mediaInfo.getUrl());
                clipboard.setPrimaryClip(clip);

                Toast.makeText(MainActivity.this, "URL copied to clipboard", Toast.LENGTH_SHORT).show();
                return true;
            }
        };

        View resultsHeaderView = getLayoutInflater().inflate(R.layout.view_results_header, null);
        mHeaderTextView = (TextView) resultsHeaderView.findViewById(R.id.header_text_view);

        mMediaListView = (ListView) findViewById(R.id.media_list_view);
        mMediaListView.setOnItemClickListener(mediaClickListener);
        mMediaListView.setOnItemLongClickListener(mediaLongClickListener);
        mMediaListView.addHeaderView(resultsHeaderView);

        // Media control
        mMediaController = findViewById(R.id.media_controller);
        mCurrentTimeTextView = (TextView) findViewById(R.id.current_time_text_view);
        mDurationTextView = (TextView) findViewById(R.id.duration_text_view);

        ImageButton playButton = (ImageButton) findViewById(R.id.play_button);
        playButton.setOnClickListener(this);

        ImageButton pauseButton = (ImageButton) findViewById(R.id.pause_button);
        pauseButton.setOnClickListener(this);

        ImageButton stopButton = (ImageButton) findViewById(R.id.stop_button);
        stopButton.setOnClickListener(this);

        // Seek bar
        mSeekBarLayout = findViewById(R.id.seek_bar_layout);
        mSeekBar = (SeekBar) findViewById(R.id.seek_bar);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean byUser) {
                long newPosition = (mMediaDuration * progress) / 1000;
                mCurrentTimeTextView.setText(stringForTime(newPosition));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                long newPosition = (mMediaDuration * progress) / 1000L;
                MediaControl mediaControl = mDevice.getMediaControl();
                if (mediaControl != null) {
                    mediaControl.seek(newPosition, null);
                }
            }
        });
    }

    private void bindCastService() {
        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder binder) {
                CastServiceBinder castServiceBinder = (CastServiceBinder) binder;
                mCastService = castServiceBinder.getCastService();
                mCastService.setLaunchListener(new MediaPlayer.LaunchListener() {
                    @Override
                    public void onSuccess(final MediaPlayer.MediaLaunchObject mediaLaunchObject) {
                        mPlayRequested = false;

                        if (mCastService != null) {
                            MediaInfo mediaInfo = mCastService.getPlayingMediaInfo();
                            setMediaIndicator(mediaInfo);
                            showMediaControls(mediaLaunchObject.mediaControl);
                        }
                    }

                    @Override
                    public void onError(ServiceCommandError error) {
                        mPlayRequested = false;
                        error.printStackTrace();
                    }
                });

                mDeviceAdapter = mCastService.getDeviceAdapter();
                mDeviceAdapter.setDeviceAddedListener(new DeviceAddedListener() {
                    @Override
                    public void onDeviceAdded(final ConnectableDevice device) {
                        if (mDevice == null) {
                            String lastDevice = PreferenceManager
                                    .getDefaultSharedPreferences(MainActivity.this)
                                    .getString("lastDevice", null);

                            if (device.getId().equals(lastDevice)) {
                                connectDevice(device);
                            }
                        }
                    }
                });

                mDeviceListView.setAdapter(mDeviceAdapter);
                mMediaListView.setAdapter(mCastService.getMediaAdapter());

                ConnectableDevice playingDevice = mCastService.getPlayingDevice();
                if (playingDevice != null) {
                    connectDevice(playingDevice);
                    updateConnectItem();
                    showMediaControls(mDevice.getMediaControl());

                    String pageTitle = mCastService.getTitle();
                    boolean showHeader = pageTitle != null && !pageTitle.equals("");
                    mHeaderTextView.setText(pageTitle);
                    mHeaderTextView.setVisibility(showHeader ? View.VISIBLE : View.GONE);

                    MediaInfo mediaInfo = mCastService.getPlayingMediaInfo();
                    setMediaIndicator(mediaInfo);
                }
                else {
                    loadMedia();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                mCastService = null;
            }
        };

        Intent startIntent = new Intent(CastService.ACTION_START, null, getApplicationContext(), CastService.class);
        startService(startIntent);

        Intent bindIntent = new Intent(MainActivity.this, CastService.class);
        bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setupBroadcastReceiver() {
        mBroadcastReceiver = new BroadcastReceiver() {
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
                .registerReceiver(mBroadcastReceiver, new IntentFilter(CastService.ACTION_NOTIFICATION_STOP));
    }

    private void loadMedia() {
        Intent intent = getIntent();
        final String pageTitle = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        final String pageUrl = intent.getStringExtra(Intent.EXTRA_TEXT);

        if (pageUrl == null) {
            mHeaderTextView.setText(R.string.usage);
            mHeaderTextView.setVisibility(View.VISIBLE);
            mRefreshContainer.setEnabled(false);
        }
        else {
            mCastService.getMediaAdapter().clear();
            mRefreshContainer.setEnabled(true);
            mRefreshContainer.setRefreshing(true);
            mHeaderTextView.setVisibility(View.GONE);
            setMediaIndicator(null);

            Map<String, String> mediaFormats = new HashMap<>();
            mediaFormats.put("mp4", "video/mp4");
            mediaFormats.put("webm", "video/webm");

            MediaScraper mediaScraper = new MediaScraper(mediaFormats);
            mediaScraper.scrape(MainActivity.this, pageUrl, 2, new MediaScraperListener() {
                @Override
                public void mediaFound(MediaInfo mediaInfo) {
                    mCastService.getMediaAdapter().addMediaInfo(mediaInfo);

                    mHeaderTextView.setText(pageTitle);
                    mHeaderTextView.setVisibility(pageTitle != null && pageTitle.length() > 0 ?
                            View.VISIBLE : View.GONE);
                }

                @Override
                public void finished(final int mediaFound) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mRefreshContainer.setRefreshing(false);

                            if (mediaFound == 0) {
                                mHeaderTextView.setText(R.string.no_video);
                                mHeaderTextView.setVisibility(View.VISIBLE);
                            }
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

        if (mDevice == null) {
            toggleDeviceMenu(true);
            return;
        }

        if (mediaInfo != null) {
            mPlayRequested = true;
            String pageTitle = mHeaderTextView.getText().toString();
            mCastService.play(mDevice, mediaInfo, pageTitle);
            showMediaControls(mDevice.getMediaControl());
        }
    }

    private void stop() {
        if (!mPlayRequested) {
            mSeekBarLayout.setVisibility(View.GONE);
            mMediaController.setVisibility(View.GONE);
            setMediaIndicator(null);

            if (mTimer != null) {
                mTimer.cancel();
            }

            if (mCastService != null) {
                mCastService.stop();
            }
        }
    }

    private void setMediaIndicator(MediaInfo mediaInfo) {
        if (mCastService != null) {
            mCastService.getMediaAdapter().setPlayingMediaInfo(mediaInfo);
        }
    }

    private void updateConnectItem() {
        mCastImageView.setImageResource(mDevice != null ?
                R.drawable.ic_cast_on :
                R.drawable.ic_cast_off);
    }

    private void toggleDeviceMenu(boolean keepOpen) {
        mDeviceListView.setVisibility(mDeviceListView.getVisibility() == View.VISIBLE &&
                !keepOpen ? View.GONE : View.VISIBLE);
    }

    private void showMediaControls(final MediaControl mediaControl) {
        mSeekBarLayout.setVisibility(View.GONE);
        mMediaController.setVisibility(View.VISIBLE);
        mCurrentTimeTextView.setText(R.string.zero_time);
        mDurationTextView.setText(R.string.zero_time);
        mSeekBar.setProgress(0);

        if (mDevice.hasCapability(MediaControl.Position)) {
            mTimer = new Timer();
            mTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    getPlayerPosition(mediaControl);
                }
            }, 1000, 1000);

            getMediaDuration(mediaControl);
        }
    }

    private void connectDevice(ConnectableDevice device) {
        mDevice = device;

        if (device.hasCapability(MediaControl.PlayState_Subscribe)) {
            final MediaControl mediaControl = device.getMediaControl();
            mediaControl.subscribePlayState(new MediaControl.PlayStateListener() {
                @Override
                public void onSuccess(final MediaControl.PlayStateStatus playState) {
                    if (playState == MediaControl.PlayStateStatus.Finished ||
                            playState == MediaControl.PlayStateStatus.Idle ||
                            playState == MediaControl.PlayStateStatus.Unknown) {
                        stop();
                    } else if (!mMediaController.isShown()) {
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
                mDeviceAdapter.notifyDataSetChanged();

                if (mCastService != null) {
                    MediaInfo mediaInfo = mCastService.getPlayingMediaInfo();
                    if (mediaInfo != null) {
                        mPlayRequested = false;
                        showMediaControls(device.getMediaControl());
                        setMediaIndicator(mediaInfo);
                    } else {
                        // Play media that may be queued
                        play(mCastService.getMediaAdapter().getPlayingMedia());
                    }
                }
            }

            @Override
            public void onDeviceDisconnected(ConnectableDevice device) {
                disconnectDevice();
            }

            @Override
            public void onPairingRequired(ConnectableDevice device, DeviceService service, DeviceService.PairingType pairingType) {
                if (DeviceService.PairingType.PIN_CODE.equals(pairingType)) {
                    PairingDialog dialog = new PairingDialog(MainActivity.this, mDevice);
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
            mDevice.connect();
        }
    }

    private void disconnectDevice() {
        if (mDevice != null) {
            stop();

            mDevice.getListeners().clear();
            mDevice.disconnect();
            mDevice = null;

            updateConnectItem();
            mDeviceAdapter.notifyDataSetChanged();
        }
    }
    private void getMediaDuration(MediaControl mediaControl) {
        mediaControl.getDuration(new MediaControl.DurationListener() {
            @Override
            public void onSuccess(Long duration) {
                if (duration > 0) {
                    mMediaDuration = duration;
                    mCurrentTimeTextView.setText(stringForTime(0));
                    mDurationTextView.setText(stringForTime(duration));
                    mSeekBarLayout.setVisibility(View.VISIBLE);
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
                if (mMediaDuration > 0) {
                    double progress = (position / (double) mMediaDuration) * 1000;
                    mCurrentTimeTextView.setText(stringForTime(position));
                    mSeekBar.setProgress((int) progress);
                }
            }

            @Override
            public void onError(ServiceCommandError error) {
                error.printStackTrace();
            }
        });
    }
}
