package com.sam_chordas.android.stockhawk.ui;

import android.app.LoaderManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.melnykov.fab.FloatingActionButton;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.QuoteCursorAdapter;
import com.sam_chordas.android.stockhawk.rest.RecyclerViewItemClickListener;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.sam_chordas.android.stockhawk.sync.StockHawkSyncAdapter;
import com.sam_chordas.android.stockhawk.touch_helper.SimpleItemTouchHelperCallback;

public class MyStocksActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor>,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String STOCK_CODE_PATTERN = "[a-zA-z]+";

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;
    private ItemTouchHelper mItemTouchHelper;
    private static final int CURSOR_LOADER_ID = 0;
    private QuoteCursorAdapter mCursorAdapter;
    private StockContentObserver contentObserver;
    private Context mContext;
    private Cursor mCursor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        setContentView(R.layout.activity_my_stocks);
        // The intent service is for executing immediate pulls from the Yahoo API
        // GCMTaskService can only schedule tasks, they cannot execute immediately
        contentObserver = new StockContentObserver();
        getContentResolver().registerContentObserver(QuoteProvider.BASE_CONTENT_URI, false, contentObserver);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        mCursorAdapter = new QuoteCursorAdapter(this, null);
        mCursorAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                updateEmptyView();
            }
        });
        recyclerView.addOnItemTouchListener(new RecyclerViewItemClickListener(this,
                new RecyclerViewItemClickListener.OnItemClickListener() {
                    @Override
                    public void onItemClick(View v, int position) {
                        //TODO:
                        // do something on item click
                        String symbol = mCursorAdapter.getSymbol(position);
                        if (symbol != null) {
                            Intent intent = new Intent(MyStocksActivity.this, QuoteDetailActivity.class);
                            intent.putExtra("symbol", symbol);
                            startActivity(intent);
                        }
                    }
                }));
        recyclerView.setAdapter(mCursorAdapter);


        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.attachToRecyclerView(recyclerView);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Utils.checkNetwork(MyStocksActivity.this)) {
                    new MaterialDialog.Builder(mContext).title(R.string.symbol_search)
                            .content(R.string.content_test)
                            .inputType(InputType.TYPE_CLASS_TEXT)
                            .input(R.string.input_hint, R.string.input_prefill, new MaterialDialog.InputCallback() {
                                @Override
                                public void onInput(MaterialDialog dialog, CharSequence input) {
                                    // On FAB click, receive user input. Make sure the stock doesn't already exist
                                    // in the DB and proceed accordingly
                                    Cursor c = getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                                            new String[]{QuoteColumns.SYMBOL}, QuoteColumns.SYMBOL + "= ?",
                                            new String[]{input.toString()}, null);
                                    if (c.getCount() != 0) {
                                        toast("This stock is already saved!");
                                    } else {
                                        if (!input.toString().matches(STOCK_CODE_PATTERN)) {
                                            toast("Invalid stock code!");
                                            return;
                                        }
                                        // Add the stock to DB
                                        StockHawkSyncAdapter.initializeSyncAdapter(MyStocksActivity.this, StockHawkSyncAdapter.ADD, input.toString());
                                    }
                                }
                            })
                            .show();
                } else {
                    networkToast();
                }

            }
        });

        ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(mCursorAdapter);
        mItemTouchHelper = new ItemTouchHelper(callback);
        mItemTouchHelper.attachToRecyclerView(recyclerView);

        mTitle = getTitle();
        // Run the initialize task service so that some stocks appear upon an empty database
        StockHawkSyncAdapter.initializeSyncAdapter(this, StockHawkSyncAdapter.INIT, null);
        if (Utils.checkNetwork(this)) {
            // create a periodic task to pull stocks once every hour after the app has been opened. This
            // is so Widget data stays up to date.
            StockHawkSyncAdapter.configurePeriodicSync(this);
        } else {
            Utils.setStockStatus(this, Utils.NO_INTERNET);
            updateEmptyView();
            networkToast();
        }
    }

    private void toast(String message) {
        Toast toast =
                Toast.makeText(MyStocksActivity.this, message,
                        Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, Gravity.CENTER, 0);
        toast.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (contentObserver != null) {
            getContentResolver().unregisterContentObserver(contentObserver);
        }
        StockHawkSyncAdapter.removePeriodicSync(this);
    }

    public void networkToast() {
        Toast.makeText(mContext, getString(R.string.network_toast), Toast.LENGTH_SHORT).show();
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.my_stocks, menu);
        restoreActionBar();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_change_units) {
            // this is for changing stock changes from percent value to dollar value
            Utils.showPercent = !Utils.showPercent;
            this.getContentResolver().notifyChange(QuoteProvider.Quotes.CONTENT_URI, null);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // This narrows the return to only the stocks that are most current.
        return new CursorLoader(this, QuoteProvider.Quotes.CONTENT_URI,
                new String[]{QuoteColumns._ID, QuoteColumns.SYMBOL, QuoteColumns.BIDPRICE,
                        QuoteColumns.PERCENT_CHANGE, QuoteColumns.CHANGE, QuoteColumns.ISUP},
                QuoteColumns.ISCURRENT + " = ?",
                new String[]{"1"},
                null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mCursorAdapter.swapCursor(data);
        mCursor = data;
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mCursorAdapter.swapCursor(null);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_stock_status_key))) {
            updateEmptyView();
        }
    }

    private void updateEmptyView() {
        TextView emptyView = (TextView) findViewById(R.id.empty_view);
        emptyView.setVisibility(mCursorAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);

        @Utils.StockStatus int status = Utils.getStockStatus(this);
        int msgId = R.string.empty_message_no_symbol;
        switch (status) {
            case Utils.STOCK_STATUS_OK:
                msgId = R.string.empty_message_no_symbol;
                break;
            case Utils.STOCK_STATUS_SERVER_DOWN:
            case Utils.STOCK_STATUS_SERVER_INVALID:
                msgId = R.string.error_message_server_down;
                break;
            case Utils.NO_INTERNET:

                msgId = R.string.error_message_no_internet;
                break;
            case Utils.STOCK_STATUS_INVALID:
                toast(getString(R.string.message_invalid_stock));
                msgId = R.string.empty_message_no_symbol;
                break;
        }

        emptyView.setText(msgId);
    }

    class StockContentObserver extends ContentObserver {

        public StockContentObserver() {
            super(new Handler(MyStocksActivity.this.getMainLooper()));
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            getLoaderManager().restartLoader(CURSOR_LOADER_ID, null, MyStocksActivity.this);
        }
    }
}
