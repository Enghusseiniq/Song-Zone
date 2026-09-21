package com.hussein.songzone;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.*;
import android.view.*;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements MusicService.Callback {
    private static final int REQ_PERMS = 1001;

    private MusicDatabase database;
    private MusicService service;
    private boolean bound = false;

    private ListView songList;
    private TextView emptyView;
    private EditText searchBox;
    private TextView nowTitle, nowArtist, currentTime, totalTime;
    private SeekBar progressBar;
    private Button btnPlay, btnPrev, btnNext, btnMode, btnSettings;

    private SongAdapter adapter;
    private final List<Song> displayedSongs = new ArrayList<>();
    private boolean userSeeking = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            service = ((MusicService.LocalBinder) b).getService();
            service.setCallback(MainActivity.this);
            bound = true;
            refreshHighlight();
        }
        @Override public void onServiceDisconnected(ComponentName n) { service = null; bound = false; }
    };

    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_main);
        database = new MusicDatabase(this);
        bindViews();
        setupList();
        setupSearch();
        setupControls();
        setupSettingsButton();

        Intent si = new Intent(this, MusicService.class);
        startService(si);
        bindService(si, connection, Context.BIND_AUTO_CREATE);

        checkPermissionsAndLoad();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (bound) {
            if (service != null) service.setCallback(null);
            unbindService(connection);
            bound = false;
        }
    }

    private void bindViews() {
        songList = findViewById(R.id.songList);
        emptyView = findViewById(R.id.emptyView);
        searchBox = findViewById(R.id.searchBox);
        nowTitle = findViewById(R.id.nowPlayingTitle);
        nowArtist = findViewById(R.id.nowPlayingArtist);
        currentTime = findViewById(R.id.currentTime);
        totalTime = findViewById(R.id.totalTime);
        progressBar = findViewById(R.id.progressBar);
        btnPlay = findViewById(R.id.btnPlay);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnMode = findViewById(R.id.btnMode);
        btnSettings = findViewById(R.id.btnSettings);
    }

    private void setupList() {
        adapter = new SongAdapter();
        songList.setAdapter(adapter);
        songList.setEmptyView(emptyView);
        songList.setOnItemClickListener((p, v, pos, id) -> {
            if (bound && service != null) service.playFromList(displayedSongs, pos);
        });
    }

    private void setupSearch() {
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                applyFilter(s.toString());
            }
        });
    }

    private void setupControls() {
        btnPlay.setOnClickListener(v -> { if (bound && service != null) service.toggle(); });
        btnNext.setOnClickListener(v -> { if (bound && service != null) service.next(); });
        btnPrev.setOnClickListener(v -> { if (bound && service != null) service.previous(); });
        btnMode.setOnClickListener(v -> onRepeatClicked());

        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) currentTime.setText(Song.formatTime(p));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { userSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                userSeeking = false;
                if (bound && service != null) service.seekTo(sb.getProgress());
            }
        });
        updateRepeatButton();
    }

    private void setupSettingsButton() {
        if (btnSettings != null)
            btnSettings.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
    }

    private void checkPermissionsAndLoad() {
        List<String> needed = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
            needed.add("android.permission.POST_NOTIFICATIONS");

        if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), REQ_PERMS);
        else loadSongs();
    }

    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        super.onRequestPermissionsResult(rc, p, g);
        if (rc == REQ_PERMS) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
                loadSongs();
            else Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show();
        }
    }

    private void loadSongs() {
        database.loadAll();
        applyFilter("");
        if (database.isEmpty())
            Toast.makeText(this, R.string.no_songs, Toast.LENGTH_LONG).show();
    }

    private void applyFilter(String q) {
        displayedSongs.clear();
        displayedSongs.addAll(database.search(q));
        adapter.notifyDataSetChanged();
    }

    private void onRepeatClicked() {
        if (!bound || service == null) return;
        MusicPlayerManager p = service.getPlayerManager();
        p.cycleRepeatMode();
        updateRepeatButton();
        String t;
        switch (p.getRepeatMode()) {
            case OFF: t = "تكرار: مغلق"; break;
            case ALL: t = "تكرار الكل"; break;
            default:  t = "تكرار واحدة"; break;
        }
        Toast.makeText(this, t, Toast.LENGTH_SHORT).show();
    }

    private void updateRepeatButton() {
        if (!bound || service == null) { btnMode.setText("🔁"); return; }
        switch (service.getPlayerManager().getRepeatMode()) {
            case OFF: btnMode.setText("🔁"); btnMode.setAlpha(0.4f); break;
            case ALL: btnMode.setText("🔁"); btnMode.setAlpha(1f);   break;
            case ONE: btnMode.setText("🔂"); btnMode.setAlpha(1f);   break;
        }
    }

    @Override public void onProgress(int pos, int dur) {
        if (userSeeking) return;
        if (dur > 0 && progressBar.getMax() != dur) progressBar.setMax(dur);
        progressBar.setProgress(pos);
        currentTime.setText(Song.formatTime(pos));
    }

    @Override public void onSongChanged(Song song) {
        if (song == null) return;
        nowTitle.setText(song.getTitle());
        nowArtist.setText(song.getArtist());
        totalTime.setText(song.getFormattedDuration());
        currentTime.setText("00:00");
        progressBar.setProgress(0);
        progressBar.setMax((int) song.getDurationMs());
        refreshHighlight();
        updateRepeatButton();
    }

    @Override public void onPlaybackStateChanged(boolean isPlaying) {
        btnPlay.setText(isPlaying ? "⏸" : "▶");
    }

    private void refreshHighlight() {
        adapter.notifyDataSetChanged();
        if (bound && service != null) {
            int idx = service.getCurrentIndex();
            if (idx >= 0 && idx < displayedSongs.size()) songList.smoothScrollToPosition(idx);
        }
    }

    private class SongAdapter extends BaseAdapter {
        @Override public int getCount() { return displayedSongs.size(); }
        @Override public Object getItem(int i) { return displayedSongs.get(i); }
        @Override public long getItemId(int i) { return displayedSongs.get(i).getId(); }

        @Override public View getView(int pos, View cv, ViewGroup parent) {
            ViewHolder h;
            if (cv == null) {
                cv = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_song, parent, false);
                h = new ViewHolder();
                h.index = cv.findViewById(R.id.itemIndex);
                h.title = cv.findViewById(R.id.itemTitle);
                h.artist = cv.findViewById(R.id.itemArtist);
                h.duration = cv.findViewById(R.id.itemDuration);
                cv.setTag(h);
            } else h = (ViewHolder) cv.getTag();

            Song s = displayedSongs.get(pos);
            h.index.setText(String.valueOf(pos + 1));
            h.title.setText(s.getTitle());
            h.artist.setText(s.getArtist());
            h.duration.setText(s.getFormattedDuration());

            boolean cur = bound && service != null && pos == service.getCurrentIndex();
            h.title.setTextColor(cur ? 0xFF3F51B5 : 0xFF212121);
            h.artist.setTextColor(cur ? 0xFF3F51B5 : 0xFF757575);
            return cv;
        }
        private class ViewHolder { TextView index, title, artist, duration; }
    }
}
