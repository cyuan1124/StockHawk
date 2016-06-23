package com.sam_chordas.android.stockhawk.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.IntDef;
import android.support.design.widget.TabLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseArray;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.StockHawkApplication;
import com.sam_chordas.android.stockhawk.endpoint.FetchHistoricalDataService;
import com.sam_chordas.android.stockhawk.parcelable.Query;
import com.sam_chordas.android.stockhawk.parcelable.Quote;
import com.sam_chordas.android.stockhawk.parcelable.QuoteResult;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class QuoteDetailActivity extends AppCompatActivity implements TabLayout.OnTabSelectedListener,
        OnChartValueSelectedListener {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({WEEK, MONTH, THREE_MONTHS, YEAR})
    public @interface QuoteResultIndex {
    }

    public static final int WEEK = 0;
    public static final int MONTH = 1;
    public static final int THREE_MONTHS = 2;
    public static final int YEAR = 3;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @BindView(R.id.tablayout)
    TabLayout tabLayout;
    @BindView(R.id.chart)
    LineChart chart;
    @BindView(R.id.open)
    TextView open;
    @BindView(R.id.close)
    TextView close;
    @BindView(R.id.high)
    TextView high;
    @BindView(R.id.low)
    TextView low;
    @BindView(R.id.date)
    TextView date;

    private SparseArray<Subscription> subscriptions;
    private SparseArray<Query> results;
    private List<Entry> entries;
    private String symbol;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            results = savedInstanceState.getSparseParcelableArray("results");
        }
        setContentView(R.layout.activity_quote_detail);
        ButterKnife.bind(this);
        results = new SparseArray<>(4);
        subscriptions = new SparseArray<>(4);
        symbol = getIntent().getStringExtra("symbol");
        getSupportActionBar().setTitle(symbol.toUpperCase());
        tabLayout.addTab(tabLayout.newTab().setText(R.string.quote_duration_week));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.quote_duration_month));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.quote_duration_3_month));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.quote_duration_year));
        tabLayout.setOnTabSelectedListener(this);

        tabLayout.getTabAt(WEEK).setContentDescription(R.string.duration_fab_week);
        tabLayout.getTabAt(MONTH).setContentDescription(R.string.duration_fab_month);
        tabLayout.getTabAt(THREE_MONTHS).setContentDescription(R.string.duration_fab_3_month);
        tabLayout.getTabAt(YEAR).setContentDescription(R.string.duration_fab_year);

        chart.setBorderColor(Color.WHITE);
        chart.setDrawBorders(true);
        chart.setDrawGridBackground(false);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setDescription("");
        chart.getLegend().setEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.setOnChartValueSelectedListener(this);

        XAxis xAxis = chart.getXAxis();
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        YAxis yAxis = chart.getAxisLeft();
        yAxis.setTextColor(Color.WHITE);

        onTabSelected(tabLayout.getTabAt(WEEK));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unsubscribe(subscriptions.get(WEEK));
        unsubscribe(subscriptions.get(MONTH));
        unsubscribe(subscriptions.get(THREE_MONTHS));
        unsubscribe(subscriptions.get(YEAR));
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        int index = tab.getPosition();
        if (results.get(index) != null) {
            populateQuoteResult(results.get(index));
        } else {
            loadResult(index);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSparseParcelableArray("results", results);
    }

    private void loadResult(final int index) {
        FetchHistoricalDataService service = StockHawkApplication.retrofit.create(FetchHistoricalDataService.class);
        Observable<QuoteResult> call = service.getQuoteResult(formatYQL(index));
        Subscription subscription = call.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<QuoteResult>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(QuoteDetailActivity.this, "Error", Toast.LENGTH_SHORT).show();
                        Log.i("StockHawk", e.getMessage());
                    }

                    @Override
                    public void onNext(QuoteResult result) {
                        results.put(index, result.getQuery());
                        populateQuoteResult(result.getQuery());
                    }
                });
        subscriptions.put(index, subscription);
    }

    private String formatYQL(int index) {
        StringBuffer yqlBuilder = new StringBuffer();
        yqlBuilder.append("select * from yahoo.finance.historicaldata ")
                .append("where symbol=\"").append(symbol).append("\" ")
                .append("and startDate=\"").append(formatDate(index)).append("\" ")
                .append("and endDate=\"").append(dateFormat.format(new Date(System.currentTimeMillis()))).append("\"");

        return yqlBuilder.toString();
    }

    private String formatDate(int index) {
        Calendar calendar = Calendar.getInstance();
        switch (index) {
            case WEEK:
                calendar.add(Calendar.WEEK_OF_YEAR, -1);
                break;
            case MONTH:
                calendar.add(Calendar.MONTH, -1);
                break;
            case THREE_MONTHS:
                calendar.add(Calendar.MONTH, -3);
                break;
            case YEAR:
                calendar.add(Calendar.YEAR, -1);
                break;
            default:
                throw new IllegalArgumentException("Incorrect index " + index);
        }
        return dateFormat.format(new Date(calendar.getTimeInMillis()));
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
    }

    private void populateQuoteResult(Query query) {
        if (query == null || query.getCount() == 0) {
            resetView();
            return;
        }
        List<String> labels = new ArrayList<>(query.getCount());
        entries = new ArrayList<>(query.getCount());
        for (int i = query.getCount() - 1; i >= 0; i--) {
            Quote quote = query.getResults().getQuotes().get(i);
            entries.add(new Entry(quote.getClose().floatValue(), query.getCount() - 1 - i));
            labels.add(query.getCount() - 1 - i, quote.getDate().substring(5));
        }
        LineDataSet lineDataSet = new LineDataSet(entries, "data");
        LineData lineData = new LineData(labels, lineDataSet);
        lineData.setValueTextColor(Color.WHITE);
        chart.setData(lineData);
        chart.invalidate();
        onQuoteSelect(query.getResults().getQuotes().get(0));
    }

    @Override
    public void onValueSelected(Entry e, int dataSetIndex, Highlight h) {
        int tabIndex = tabLayout.getSelectedTabPosition();
        int quoteIndex = entries.indexOf(e);
        Query query = results.get(tabIndex);
        onQuoteSelect(query.getResults().getQuotes().get(query.getCount() - quoteIndex - 1));
    }

    @Override
    public void onNothingSelected() {
    }

    private void resetView() {
        chart.clear();
        date.setText("");
        open.setText("");
        close.setText("");
        high.setText("");
        low.setText("");
    }

    private void onQuoteSelect(Quote quote) {
        date.setText(quote.getDate());
        setPrice(open, quote.getOpen());
        setPrice(close, quote.getClose());
        setPrice(high, quote.getHigh());
        setPrice(low, quote.getLow());
        open.setContentDescription(getString(R.string.content_description_open, quote.getDate()));
        close.setContentDescription(getString(R.string.content_description_close, quote.getDate()));
        high.setContentDescription(getString(R.string.content_description_high, quote.getDate()));
        low.setContentDescription(getString(R.string.content_description_low, quote.getDate()));
    }

    private void setPrice(TextView textView, Double value) {
        if (value != null) {
            textView.setText(String.format(Locale.getDefault(), "%.2f", value));
        } else {
            textView.setText("--");
        }
    }

    private void unsubscribe(Subscription subscription) {
        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
        }
    }
}
