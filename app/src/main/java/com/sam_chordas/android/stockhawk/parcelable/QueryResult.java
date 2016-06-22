package com.sam_chordas.android.stockhawk.parcelable;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;

/**
 * Created by chenyuan on 6/21/16.
 */
public class QueryResult implements Parcelable {

    @SerializedName("quote")
    private ArrayList<Quote> quotes;

    public ArrayList<Quote> getQuotes() {
        return quotes;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(this.quotes);
    }

    public QueryResult() {
    }

    protected QueryResult(Parcel in) {
        this.quotes = in.createTypedArrayList(Quote.CREATOR);
    }

    public static final Creator<QueryResult> CREATOR = new Creator<QueryResult>() {
        @Override
        public QueryResult createFromParcel(Parcel source) {
            return new QueryResult(source);
        }

        @Override
        public QueryResult[] newArray(int size) {
            return new QueryResult[size];
        }
    };
}
