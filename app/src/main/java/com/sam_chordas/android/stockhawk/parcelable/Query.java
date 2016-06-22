package com.sam_chordas.android.stockhawk.parcelable;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by chenyuan on 6/21/16.
 */
public class Query implements Parcelable {

    private final Integer count;
    private final String created;
    private final String lang;
    private final QueryResult results;

    public Integer getCount() {
        return count;
    }

    public String getCreated() {
        return created;
    }

    public String getLang() {
        return lang;
    }

    public QueryResult getResults() {
        return results;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(this.count);
        dest.writeString(this.created);
        dest.writeString(this.lang);
        dest.writeParcelable(this.results, flags);
    }

    protected Query(Parcel in) {
        this.count = (Integer) in.readValue(Integer.class.getClassLoader());
        this.created = in.readString();
        this.lang = in.readString();
        this.results = in.readParcelable(QueryResult.class.getClassLoader());
    }

    public static final Creator<Query> CREATOR = new Creator<Query>() {
        @Override
        public Query createFromParcel(Parcel source) {
            return new Query(source);
        }

        @Override
        public Query[] newArray(int size) {
            return new Query[size];
        }
    };
}
