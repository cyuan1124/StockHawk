package com.sam_chordas.android.stockhawk.endpoint;

import com.sam_chordas.android.stockhawk.parcelable.QuoteResult;

import retrofit2.http.GET;
import rx.Observable;

/**
 * Created by chenyuan on 6/21/16.
 */
public interface FetchHistoricalDataService {

    @GET("v1/public/yql?format=json&callback=&env=store%3A%2F%2Fdatatables.org%2Falltableswithkeys")
    Observable<QuoteResult> getQuoteResult(@retrofit2.http.Query("q") String yql);

}
