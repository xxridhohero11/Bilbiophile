package com.example.bilbiophile.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.Toolbar;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.bilbiophile.R;
import com.example.bilbiophile.fragment.ProgressBarFragment;
import com.example.bilbiophile.fragment.RetryFragment;
import com.example.bilbiophile.helper.Helper;
import com.example.bilbiophile.model.data.AudioInfo;
import com.example.bilbiophile.model.data.Book;
import com.example.bilbiophile.model.data.Chapter;
import com.example.bilbiophile.model.data.RSSBook;
import com.example.bilbiophile.model.data.db.DatabaseHelper;
import com.example.bilbiophile.model.data.db.FvBookHelper;
import com.example.bilbiophile.provider.BookDetailProvider;
import com.example.bilbiophile.provider.CoverProvider;
import com.example.bilbiophile.service.MediaPlayerService;
import com.example.bilbiophile.task.DownloadTask;
import com.example.bilbiophile.task.JsonParseTask;

import static com.example.bilbiophile.model.data.db.FvDatabaseContract.TABLE_FV;

public class BookDetailActivity extends AppCompatActivity implements
        DownloadTask.OnDownloadListener,
        JsonParseTask.OnJsonParseListener,
        AdapterView.OnItemClickListener,
        MediaPlayerService.OnMediaPlayerServiceListener,
        CoverProvider.OnCoverListener {
    private static final String TAG = "___BookDetailActivity";
    private static final int DARK_BRIGHTNESS = 128;

    private Book mActiveBook;

    private BookDetailProvider mProvider;

    LinearLayout mllBook;

    private ImageView mivCover;
    private TextView mtvPubdate;
    private TextView mtvTitle;
    private TextView mtvDescription;
    //helper
    private DatabaseHelper helper;
    private FvBookHelper fvBookHelper;


    private ListView mlvChapters;
    private ChapterListAdapter mAdapter;

    private ProgressBarFragment mProgressBar;
    private RetryFragment mRetryFragment;

    //boolean fav
    private Boolean isFavorite = false;

    // edia player service
    private MediaPlayerService mPlayer;
    private boolean bServiceBound = false;

    private Chapter mActiveChapter;
    private Chapter mPendingActive;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.book_detail_in, android.R.anim.fade_in);
        setContentView(R.layout.activity_book_detail);

        fvBookHelper = new FvBookHelper(this);
        fvBookHelper.open();
        helper = new DatabaseHelper(this);


        handleIntent();
        setupContent();
        startLoading();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void setupContent() {
        mProvider = new BookDetailProvider(this);

        mllBook = findViewById(R.id.llBookLayout);

        mivCover = findViewById(R.id.ivdCover);
        mtvPubdate = findViewById(R.id.tvdPubdate);
        mtvTitle = findViewById(R.id.tvdTitle);
        mtvDescription = findViewById(R.id.tvdDescription);
        mtvDescription.setMovementMethod(new ScrollingMovementMethod());

        mlvChapters = findViewById(R.id.lvdChapters);
        mAdapter = new ChapterListAdapter(this, R.id.lvdChapters, mActiveBook);
        mlvChapters.setAdapter(mAdapter);
        mlvChapters.setOnItemClickListener(this);

        isFavorite = checkFav();

        CoverProvider coverProvider = new CoverProvider(this);
        coverProvider.setListener(this);
        Bitmap bitmap = coverProvider.getCoverBitmap(mActiveBook, mivCover);
        mivCover.setImageBitmap(bitmap);
        if (bitmap != null) {
            Palette.from(bitmap).generate(new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(Palette palette) {
                }
            });
        }

        String pubDate = Helper.milliToString(mActiveBook.pubDate, RSSBook.TIME_FORMAT_PUBDATE_COMPACT);
        mtvTitle.setText(mActiveBook.title);
        mtvPubdate.setText(pubDate);
    }

    private void applyBitmapPalette(Palette p) {
        Palette.Swatch dominant = p.getDominantSwatch();
        if (dominant == null) dominant = p.getVibrantSwatch();
        if (dominant == null) dominant = p.getMutedSwatch();
        if (dominant == null) return;

        int dominantColor = dominant.getRgb();
        int brightness = Helper.getColorBrightness(dominantColor);
        mllBook.setBackgroundColor(dominantColor);

        Palette.Swatch swatch = null;
        if (brightness < DARK_BRIGHTNESS) {
            // dark
            swatch = p.getVibrantSwatch();
            if (swatch == null || swatch.getRgb() == dominantColor || Helper.getColorBrightness(swatch.getRgb()) < DARK_BRIGHTNESS) {
                swatch = p.getMutedSwatch();
                if (swatch == null || swatch.getRgb() == dominantColor || Helper.getColorBrightness(swatch.getRgb()) < DARK_BRIGHTNESS) {
                    swatch = p.getLightVibrantSwatch();
                    if (swatch == null || swatch.getRgb() == dominantColor || Helper.getColorBrightness(swatch.getRgb()) < DARK_BRIGHTNESS) {
                        swatch = p.getLightMutedSwatch();
                        if (swatch == null || Helper.getColorBrightness(swatch.getRgb()) < DARK_BRIGHTNESS) {
                            swatch = dominant;
                        }
                    }
                }
            }

        } else {
            // light
            swatch = p.getVibrantSwatch();
            if (swatch == null || swatch.getRgb() == dominantColor || Helper.getColorBrightness(swatch.getRgb()) > DARK_BRIGHTNESS) {
                swatch = p.getMutedSwatch();
                if (swatch == null || swatch.getRgb() == dominantColor || Helper.getColorBrightness(swatch.getRgb()) > DARK_BRIGHTNESS) {
                    swatch = p.getDarkVibrantSwatch();
                    if (swatch == null || swatch.getRgb() == dominantColor || Helper.getColorBrightness(swatch.getRgb()) > DARK_BRIGHTNESS) {
                        swatch = p.getDarkMutedSwatch();
                        if (swatch == null || Helper.getColorBrightness(swatch.getRgb()) > DARK_BRIGHTNESS) {
                            swatch = dominant;
                        }
                    }
                }
            }
        }

        int textColor = Color.BLACK;
        if (swatch == dominant) {
            textColor = swatch.getBodyTextColor();
        } else {
            textColor = swatch.getRgb();
        }

        mivCover.setBackgroundColor(textColor);

        mtvPubdate.setTextColor(dominant.getTitleTextColor());
        mtvTitle.setTextColor(textColor);
        mtvDescription.setTextColor(dominant.getBodyTextColor());
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_book, menu);
        return super.onCreateOptionsMenu(menu);
    }
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        if (isFavorite) {
            menu.findItem(R.id.btn_fav).setIcon(R.drawable.ic_favorite_black_24dp);
        } else {
            menu.findItem(R.id.btn_fav).setIcon(R.drawable.ic_favorite_border_black_24dp);
        }


        return super.onPrepareOptionsMenu(menu);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_done:
                finish();
                return true;

            case R.id.btn_fav:
                invalidateOptionsMenu();
                toggleFavorite(item);
                return true;
        }
        return false;
    }
    private boolean checkFav(){
        SQLiteDatabase sqLiteDatabase= helper.getWritableDatabase();
        String query = "SELECT * FROM " + TABLE_FV + " WHERE title = " +"'"+mActiveBook.title+"'";
        Cursor c = sqLiteDatabase.rawQuery(query,null);
        if(c.getCount()<=0){
            c.close();
            return false;
        }
        c.close();
        return true;

    }
    private void toggleFavorite(MenuItem item) {
        if (isFavorite) {
            fvBookHelper.delete(mActiveBook.title);
            Toast.makeText(this,R.string.remove_fav, Toast.LENGTH_SHORT).show();
            isFavorite = false;
        } else {
            fvBookHelper.insert(mActiveBook);
            Toast.makeText(this,R.string.add_fav, Toast.LENGTH_SHORT).show();
            item.setIcon(R.drawable.ic_favorite_border_black_24dp);
            isFavorite = true;
        }
    }

    private void handleIntent() {
        Intent intent = getIntent();
        mActiveBook = intent.getExtras().getParcelable("book");
    }

    private void startLoading() {
        mProvider.setDownloadListener(this);
        mProvider.setParseListener(this);
        Book book = mProvider.getBookDetail(mActiveBook);
        if (book != null) {
            onPostJsonParse(0, book);
            if (mActiveChapter != null && !bServiceBound) {
                Intent intent = new Intent(this, MediaPlayerService.class);
                bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
            }
        }
    }

    @Override
    public void onMediaStartNew() {
        if (mActiveChapter != null) {
            mActiveChapter.playbackState = AudioInfo.AI_IDLE;
            mActiveChapter = null;
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onMediaPlay() {

    }

    @Override
    public void onMediaSkipToPrevious() {
        if (mActiveChapter != null) {
            int position = mAdapter.mBook.chapters.indexOf(mActiveChapter);

            if (position > 0) {
                onItemClick(null, null, position - 1, 0);
            }
        }
    }

    @Override
    public void onMediaSkipToNext() {
        if (mActiveChapter != null) {
            int position = mAdapter.mBook.chapters.indexOf(mActiveChapter);

            if (position >= 0 && position < mAdapter.getCount() - 1) {
                onItemClick(null, null, position + 1, 0);
            }
        }
    }

    @Override
    public void onMediaPrepared() {
        if (mPendingActive != null) {
            mActiveChapter = mPendingActive;
            mPendingActive = null;
        }

    }

    @Override
    public void onMediaPause() {
        if (mActiveChapter != null) {
            mActiveChapter.playbackState = AudioInfo.AI_PAUSE;
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onMediaResume() {
        if (mActiveChapter != null) {
            mActiveChapter.playbackState = AudioInfo.AI_PLAYING;
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onMediaComplete() {
        if (mActiveChapter != null) {
            mActiveChapter.playbackState = AudioInfo.AI_IDLE;
            mActiveChapter = null;
            mPendingActive = null;
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onMediaStop() {
        if (mActiveChapter != null) {
            mActiveChapter.playbackState = AudioInfo.AI_IDLE;
            mActiveChapter = null;
            mPendingActive = null;
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onMediaBuffering(int percent) {

    }

    @Override
    public void onMediaError(int what, int extra) {
        if (mActiveChapter != null) {
            mActiveChapter.playbackState = AudioInfo.AI_IDLE;
            mActiveChapter = null;
            mPendingActive = null;
            mAdapter.notifyDataSetChanged();
        }
    }


    @Override
    public void onPreDownload(int taskId) {
        mProgressBar = new ProgressBarFragment();
        getSupportFragmentManager().beginTransaction().add(R.id.activity_book_detail_progress, mProgressBar).commitAllowingStateLoss();
    }

    @Override
    public void onPostDownload(int taskId, DownloadTask.DownloadResult result) {

    }

    @Override
    public void onNetworkError(int taskId, String message) {
        if (mProgressBar != null) {
            getSupportFragmentManager().beginTransaction().remove(mProgressBar).commitAllowingStateLoss();
        }

        mRetryFragment = new RetryFragment();
        mRetryFragment.setContent(R.drawable.spacebook, getString(R.string.msg_network_error), true);
        getSupportFragmentManager().beginTransaction().add(R.id.activity_book_detail_progress, mRetryFragment).commitAllowingStateLoss();
        //mRetryFragment.setRetryListener(this);

    }

    @Override
    public void onPreJsonParse(int taskId) {

    }

    @Override
    public void onPostJsonParse(int taskId, Book result) {
        if (mProgressBar != null) {
            getSupportFragmentManager().beginTransaction().remove(mProgressBar).commitAllowingStateLoss();
        }
        mActiveBook = result;

        mtvDescription.setText(Helper.fromHtml(mActiveBook.description));

        if (mAdapter != null) {
            mAdapter.setBook(mActiveBook);
        }
    }

    @Override
    public void onJsonParseError(int taskId, String message) {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (bServiceBound) {
            mPlayer.getMediaListeners().remove(this);
            unbindService(mServiceConnection);
        }
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MediaPlayerService.LocalServiceBinder binder = (MediaPlayerService.LocalServiceBinder) service;
            mPlayer = binder.getService();
            bServiceBound = true;
            mPlayer.addListener(BookDetailActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bServiceBound = false;
        }
    };

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Chapter chapter = mAdapter.getItem(position);

        switch (chapter.playbackState) {
            case AudioInfo.AI_IDLE:
                Intent intent = new Intent(this, MediaPlayerService.class);
                AudioInfo audioInfo = chapter;
                intent.putExtra("audio", audioInfo);
                startService(intent);
                if (!bServiceBound) {
                    bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
                }

                if (mPendingActive != null) {
                    mPendingActive.playbackState = AudioInfo.AI_IDLE;
                }
                mPendingActive = chapter;
                chapter.playbackState = AudioInfo.AI_PLAYING;
                break;

            case AudioInfo.AI_PLAYING:
                if (mPlayer != null) {
                    mPlayer.pauseMedia();
                    chapter.playbackState = AudioInfo.AI_PAUSE;
                }
                break;

            case AudioInfo.AI_PAUSE:
                if (mPlayer != null) {
                    mPlayer.resumeMedia();
                    chapter.playbackState = AudioInfo.AI_PLAYING;
                }
                break;
        }

        mAdapter.notifyDataSetChanged();

    }

    @Override
    public void onCoverLoaded(String guid, Bitmap cover, ImageView imageView) {
        if (guid.equals(mActiveBook.guid)) {
            TransitionDrawable transitionDrawable = new TransitionDrawable(new Drawable[]{
                    new ColorDrawable(Color.TRANSPARENT),
                    new BitmapDrawable(getResources(), cover)
            });

            mivCover.setImageDrawable(transitionDrawable);
            transitionDrawable.startTransition(200);

            Palette.from(cover).generate(new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(Palette palette) {
                    applyBitmapPalette(palette);
                }
            });
        }
    }

    class ChapterListAdapter extends ArrayAdapter<Chapter> {
        private Context context;
        private LayoutInflater inflater;

        private Book mBook;

        public ChapterListAdapter(@NonNull Context context, int resource, Book book) {
            super(context, resource);

            this.context = context;
            this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            this.mBook = book;
        }

        @Override
        public int getCount() {
            return mBook.chapters.size();
        }

        @Override
        public Chapter getItem(int position) {
            return mBook.chapters.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;

            if (view == null) {
                view = inflater.inflate(R.layout.chapter_item, viewGroup, false);

                viewHolder = new ViewHolder();
                viewHolder.tvTitle = view.findViewById(R.id.tvcTitle);
                viewHolder.tvDuration = view.findViewById(R.id.tvcDuration);
                viewHolder.tvSize = view.findViewById(R.id.tvcSize);
                viewHolder.ivPlayPause = view.findViewById(R.id.ivcPlayPause);

                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            Chapter chapter = mBook.chapters.get(position);
            viewHolder.tvTitle.setText(chapter.title);
            viewHolder.tvDuration.setText(Helper.timeFromSeconds(chapter.trackLength));
            viewHolder.tvSize.setText(Helper.sizeFromBytes(chapter.trackSize));

            switch (chapter.playbackState) {
                case AudioInfo.AI_IDLE:
                    viewHolder.ivPlayPause.setImageResource(R.mipmap.ic_track);
                    break;

                case AudioInfo.AI_PLAYING:
                    viewHolder.ivPlayPause.setImageResource(R.mipmap.ic_pause);
                    break;

                case AudioInfo.AI_PAUSE:
                    viewHolder.ivPlayPause.setImageResource(R.mipmap.ic_play);
                    break;

            }

            return view;
        }

        public void setBook(Book book) {
            this.mBook = book;
            this.notifyDataSetChanged();
            rescanActiveChapter();
        }

        private void rescanActiveChapter() {
            for (Chapter chapter: mBook.chapters) {
                if (chapter.playbackState != AudioInfo.AI_IDLE) {
                    mActiveChapter = chapter;
                    mPendingActive = chapter;
                    break;
                }
            }
        }
    }

    static class ViewHolder {
        TextView tvTitle;
        TextView tvDuration;
        TextView tvSize;
        ImageView ivPlayPause;
    }


}
