package com.example.xyzreader.ui;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;
import com.example.xyzreader.data.UpdaterService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * An activity representing a list of Articles. This activity has different presentations for
 * handset and tablet-size devices. On handsets, the activity presents a list of items, which when
 * touched, lead to a {@link ArticleDetailActivity} representing item details. On tablets, the
 * activity presents a grid of items as cards.
 */
public class ArticleListActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor>,
        SwipeRefreshLayout.OnRefreshListener{

    private static final String TAG = ArticleListActivity.class.toString();
    private Toolbar mToolbar;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss");
    // Use default locale format
    private SimpleDateFormat outputFormat = new SimpleDateFormat();
    // Most time functions can only handle 1902 - 2037
    private GregorianCalendar START_OF_EPOCH = new GregorianCalendar(2,1,1);

    private CoordinatorLayout coordinatorLayout;
    private Snackbar snackbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_list);

        mToolbar = (Toolbar) findViewById(R.id.toolbar);

        /*
		 * Faz com que uma toolbar seja a actionbar da activity
	    */
        setSupportActionBar(mToolbar);

      /* Remove o nome do app da toolbar */
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        coordinatorLayout = (CoordinatorLayout) findViewById(R.id.coordinatorLayout);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        getLoaderManager().initLoader(0, null, this);

        mSwipeRefreshLayout.setOnRefreshListener(this);

        if (savedInstanceState == null) {
            refresh();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(UpdaterService.BROADCAST_ACTION_STATE_CHANGE));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mRefreshingReceiver);
    }

    private boolean mIsRefreshing = true;
    private boolean mNoInternet = false;

    private BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UpdaterService.BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mNoInternet = intent.getBooleanExtra(UpdaterService.EXTRA_NO_INTERNET, false);
                mIsRefreshing = intent.getBooleanExtra(UpdaterService.EXTRA_REFRESHING, false);
                updateRefreshingUI();
                showNoInternetMessage();
            }
        }
    };

    private void updateRefreshingUI()
    {
        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
    }

    @Override
    public void onRefresh()
    {
        refresh();
    }

    private void refresh() {
        startService(new Intent(this, UpdaterService.class));
    }

    private void showNoInternetMessage()
    {
        if(mNoInternet)
        {
            snackbar = Snackbar.make(coordinatorLayout, getResources().getString(R.string.no_internet_connection), Snackbar.LENGTH_LONG).setAction(getResources().getString(R.string.retry), new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    snackbar.dismiss();

                    refresh();
                }
            });

            snackbar.setDuration(Snackbar.LENGTH_INDEFINITE);
            snackbar.show();
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        Adapter adapter = new Adapter(cursor);
        adapter.setHasStableIds(true);
        mRecyclerView.setAdapter(adapter);
        int columnCount = getResources().getInteger(R.integer.list_column_count);
        StaggeredGridLayoutManager sglm =
                new StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(sglm);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mRecyclerView.setAdapter(null);
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private Cursor mCursor;

        public Adapter(Cursor cursor) {
            mCursor = cursor;
        }

        @Override
        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getLong(ArticleLoader.Query._ID);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.list_item_article, parent, false);
            final ViewHolder vh = new ViewHolder(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            ItemsContract.Items.buildItemUri(getItemId(vh.getAdapterPosition()))));
                }
            });
            return vh;
        }

        private Date parsePublishedDate() {
            try {
                String date = mCursor.getString(ArticleLoader.Query.PUBLISHED_DATE);
                return dateFormat.parse(date);
            } catch (ParseException ex) {
                Log.e(TAG, ex.getMessage());
                Log.i(TAG, "passing today's date");
                return new Date();
            }
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            mCursor.moveToPosition(position);
            holder.titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            Date publishedDate = parsePublishedDate();
            if (!publishedDate.before(START_OF_EPOCH.getTime())) {

                holder.subtitleView.setText(Html.fromHtml(
                        DateUtils.getRelativeTimeSpanString(
                                publishedDate.getTime(),
                                System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_ALL).toString()
                                + "<br/>" + " by "
                                + mCursor.getString(ArticleLoader.Query.AUTHOR)));
            } else {
                holder.subtitleView.setText(Html.fromHtml(
                        outputFormat.format(publishedDate)
                        + "<br/>" + " by "
                        + mCursor.getString(ArticleLoader.Query.AUTHOR)));
            }
            holder.thumbnailView.setImageUrl(
                    mCursor.getString(ArticleLoader.Query.THUMB_URL),
                    ImageLoaderHelper.getInstance(ArticleListActivity.this).getImageLoader());
            holder.thumbnailView.setAspectRatio(mCursor.getFloat(ArticleLoader.Query.ASPECT_RATIO));

            ImageLoaderHelper.getInstance(ArticleListActivity.this).getImageLoader().get(mCursor.getString(ArticleLoader.Query
                    .THUMB_URL), new ImageLoader.ImageListener()
            {
                @Override
                public void onResponse(ImageLoader.ImageContainer imageContainer, boolean b)
                {
                    final Bitmap bitmap = imageContainer.getBitmap();
                    if(bitmap != null)
                    {
                        Palette.from(bitmap).generate(new Palette.PaletteAsyncListener()
                        {
                            @Override
                            public void onGenerated(Palette palette)
                            {
                                holder.thumbnailView.setImageBitmap(bitmap);

                                ColorDrawable[] colorBackground = {(ColorDrawable) ContextCompat.getDrawable(ArticleListActivity.this, R.color.article_title),
                                        new ColorDrawable(palette.getDarkMutedColor(ContextCompat.getColor(ArticleListActivity.this, R.color.article_title)))};
                                TransitionDrawable transitionBackground = new TransitionDrawable(colorBackground);

                                holder.contentBackground.setBackground(transitionBackground);

                                transitionBackground.startTransition(DynamicHeightNetworkImageView.FADE_IN_TIME_MS);

                                ObjectAnimator.ofObject(holder.titleView, // Object to animating
                                        "textColor", // Property to animate
                                        new ArgbEvaluator(), // Interpolation function
                                        ContextCompat.getColor(ArticleListActivity.this, R.color.abc_primary_text_material_light), // Start color
                                        ContextCompat.getColor(ArticleListActivity.this, R.color.article_title) // End color
                                ).setDuration(DynamicHeightNetworkImageView.FADE_IN_TIME_MS) // Duration in milliseconds
                                        .start();


                                ObjectAnimator.ofObject(holder.subtitleView, // Object to animating
                                        "textColor", // Property to animate
                                        new ArgbEvaluator(), // Interpolation function
                                        ContextCompat.getColor(ArticleListActivity.this, R.color.abc_secondary_text_material_dark), // Start color
                                        ContextCompat.getColor(ArticleListActivity.this, R.color.article_byline) // End color
                                ).setDuration(DynamicHeightNetworkImageView.FADE_IN_TIME_MS) // Duration in milliseconds
                                        .start();
                            }
                        });
                    }
                }

                @Override
                public void onErrorResponse(VolleyError volleyError)
                {

                }
            });
        }

        @Override
        public int getItemCount() {
            return mCursor.getCount();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public DynamicHeightNetworkImageView thumbnailView;
        public TextView titleView;
        public TextView subtitleView;
        public LinearLayout contentBackground;

        public ViewHolder(View view) {
            super(view);
            thumbnailView = (DynamicHeightNetworkImageView) view.findViewById(R.id.thumbnail);
            titleView = (TextView) view.findViewById(R.id.article_title);
            subtitleView = (TextView) view.findViewById(R.id.article_subtitle);
            contentBackground = (LinearLayout) view.findViewById(R.id.contentBackground);
        }
    }
}
