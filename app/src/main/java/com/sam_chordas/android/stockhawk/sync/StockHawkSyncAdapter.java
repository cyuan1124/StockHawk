package com.sam_chordas.android.stockhawk.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.appwidget.AppWidgetManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.IntDef;
import android.util.Log;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.sam_chordas.android.stockhawk.widget.DetailWidgetProvider;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URLEncoder;
import java.util.ArrayList;

public class StockHawkSyncAdapter extends AbstractThreadedSyncAdapter {
    public final String LOG_TAG = "StockHawk.SyncAdapter";
    public static final String ACTION_DATA_UPDATED =
            "com.sam)chordas.android.stockhawk.ACTION_DATA_UPDATED";

    private OkHttpClient client = new OkHttpClient();
    private boolean isUpdate;

    public StockHawkSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
    }

    String fetchData(String url) throws IOException {
        Log.i(LOG_TAG, "Url-> [" + url + "]");
        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response = client.newCall(request).execute();
        return response.body().string();
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        if (!Utils.checkNetwork(getContext())) {
            Utils.setStockStatus(getContext(), Utils.NO_INTERNET);
            Log.i("StockHawk", "Set to " + Utils.NO_INTERNET);
            return;
        }
        Cursor initQueryCursor;
        StringBuilder urlStringBuilder = new StringBuilder();
        StringBuilder mStoredSymbols = new StringBuilder();
        try {
            // Base URL for the Yahoo query
            urlStringBuilder.append("https://query.yahooapis.com/v1/public/yql?q=");
            urlStringBuilder.append(URLEncoder.encode("select * from yahoo.finance.quotes where symbol "
                    + "in (", "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        int launchMode = extras.getInt(LAUNCH_MODE_KEY);
        if (launchMode == INIT || launchMode == PERIODIC) {
            isUpdate = true;
            initQueryCursor = getContext().getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                    new String[]{"Distinct " + QuoteColumns.SYMBOL}, null,
                    null, null);
            if (initQueryCursor == null || initQueryCursor.getCount() == 0) {
                // Init task. Populates DB with quotes for the symbols seen below
                try {
                    urlStringBuilder.append(
                            URLEncoder.encode("\"YHOO\",\"AAPL\",\"GOOG\",\"MSFT\")", "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            } else {
                DatabaseUtils.dumpCursor(initQueryCursor);
                initQueryCursor.moveToFirst();
                for (int i = 0; i < initQueryCursor.getCount(); i++) {
                    mStoredSymbols.append("\"" +
                            initQueryCursor.getString(initQueryCursor.getColumnIndex("symbol")) + "\",");
                    initQueryCursor.moveToNext();
                }
                mStoredSymbols.replace(mStoredSymbols.length() - 1, mStoredSymbols.length(), ")");
                try {
                    urlStringBuilder.append(URLEncoder.encode(mStoredSymbols.toString(), "UTF-8"));
                    Log.i("StockHawk", urlStringBuilder.toString());
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        } else if (launchMode == ADD) {
            isUpdate = false;
            // get symbol from params.getExtra and build query
            String stockInput = extras.getString("symbol");
            try {
                urlStringBuilder.append(URLEncoder.encode("\"" + stockInput + "\")", "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        // finalize the URL for the API query.
        urlStringBuilder.append("&format=json&diagnostics=true&env=store%3A%2F%2Fdatatables."
                + "org%2Falltableswithkeys&callback=");

        String urlString;
        String getResponse;
        if (urlStringBuilder != null) {
            if (!Utils.checkNetwork(getContext())) {
                Utils.setStockStatus(getContext(), Utils.NO_INTERNET);
                return;
            }
            urlString = urlStringBuilder.toString();
            try {
                getResponse = fetchData(urlString);
                try {
                    ContentValues contentValues = new ContentValues();
                    // update ISCURRENT to 0 (false) so new data is current
                    if (isUpdate) {
                        contentValues.put(QuoteColumns.ISCURRENT, 0);
                        getContext().getContentResolver().update(QuoteProvider.Quotes.CONTENT_URI, contentValues,
                                null, null);
                    }
                    ArrayList<ContentProviderOperation> operations = Utils.quoteJsonToContentVals(getContext(), getResponse);
                    if (operations != null) {
                        getContext().getContentResolver().applyBatch(QuoteProvider.AUTHORITY, operations);
                        Utils.setStockStatus(getContext(), Utils.STOCK_STATUS_OK);
                        getContext().getContentResolver().notifyChange(QuoteProvider.BASE_CONTENT_URI, null);
                    }
                } catch (RemoteException | OperationApplicationException e) {
                    Utils.setStockStatus(getContext(), Utils.STOCK_STATUS_INVALID);
                    Log.e(LOG_TAG, "Error applying batch insert", e);
                }
            } catch (IOException e) {
                e.printStackTrace();
                Utils.setStockStatus(getContext(), Utils.STOCK_STATUS_SERVER_DOWN);
            }
        }
        Intent intent = new Intent(getContext(), DetailWidgetProvider.class);
        intent.setAction(StockHawkSyncAdapter.ACTION_DATA_UPDATED);
        int ids[] = AppWidgetManager.getInstance(getContext())
                .getAppWidgetIds(new ComponentName(getContext().getApplicationContext(), DetailWidgetProvider.class));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        getContext().sendBroadcast(intent);
    }

    /**
     * Helper method to get the fake account to be used with SyncAdapter, or make a new one
     * if the fake account doesn't exist yet.  If we make a new account, we call the
     * onAccountCreated method so we can initialize things.
     *
     * @param context The context used to access the account service
     * @return a fake account.
     */
    public static Account getSyncAccount(Context context) {
        // Get an instance of the Android account manager
        AccountManager accountManager =
                (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);

        // Create the account type and default account
        Account newAccount = new Account(
                context.getString(R.string.app_name), context.getString(R.string.sync_account_type));

        // If the password doesn't exist, the account doesn't exist
        if (null == accountManager.getPassword(newAccount)) {

        /*
         * Add the account and account type, no password or user data
         * If successful, return the Account object, otherwise report an error.
         */
            if (!accountManager.addAccountExplicitly(newAccount, "", null)) {
                return null;
            }
            /*
             * If you don't set android:syncable="true" in
             * in your <provider> element in the manifest,
             * then call ContentResolver.setIsSyncable(account, AUTHORITY, 1)
             * here.
             */

        }
        return newAccount;
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({INIT, PERIODIC, ADD})
    public @interface SyncAdapterLaunchMode {
    }

    public static final int INIT = 0;
    public static final int PERIODIC = 1;
    public static final int ADD = 2;
    public static final String LAUNCH_MODE_KEY = "launchmode";
    public static final String SYMBOL = "symbol";
    private static final long SYNC_INTERVAL = 3600L;
    public static final long SYNC_FLEXTIME = SYNC_INTERVAL/3;

    public static void initializeSyncAdapter(Context context, @SyncAdapterLaunchMode int launchMode, String symbol) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        bundle.putInt(LAUNCH_MODE_KEY, launchMode);
        bundle.putString(SYMBOL, symbol);
        ContentResolver.requestSync(getSyncAccount(context),
                context.getString(R.string.content_authority), bundle);
    }

    public static void configurePeriodicSync(Context context) {
        Account account = getSyncAccount(context);
        String authority = context.getString(R.string.content_authority);
        Bundle bundle = new Bundle();
        bundle.putInt(LAUNCH_MODE_KEY, PERIODIC);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // we can enable inexact timers in our periodic sync
            SyncRequest request = new SyncRequest.Builder().
                    syncPeriodic(SYNC_INTERVAL, SYNC_FLEXTIME).
                    setSyncAdapter(account, authority).
                    setExtras(bundle).build();
            ContentResolver.requestSync(request);
        } else {
            ContentResolver.addPeriodicSync(account,
                    authority, bundle, SYNC_INTERVAL);
        }
    }

    public static void removePeriodicSync(Context context) {
        ContentResolver.removePeriodicSync(getSyncAccount(context),
                context.getString(R.string.content_authority), Bundle.EMPTY);
    }

}