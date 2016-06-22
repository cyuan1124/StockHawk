package com.sam_chordas.android.stockhawk.parcelable;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by chenyuan on 6/21/16.
 */
public class QuoteResult implements Parcelable {

    private Query query;

    public Query getQuery() {
        return query;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.query, flags);
    }

    public QuoteResult() {
    }

    protected QuoteResult(Parcel in) {
        this.query = in.readParcelable(Query.class.getClassLoader());
    }

    public static final Creator<QuoteResult> CREATOR = new Creator<QuoteResult>() {
        @Override
        public QuoteResult createFromParcel(Parcel source) {
            return new QuoteResult(source);
        }

        @Override
        public QuoteResult[] newArray(int size) {
            return new QuoteResult[size];
        }
    };
}
