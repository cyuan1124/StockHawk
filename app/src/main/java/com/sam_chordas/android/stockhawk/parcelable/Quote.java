package com.sam_chordas.android.stockhawk.parcelable;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.SerializedName;

/**
 * Created by chenyuan on 6/21/16.
 */
public class Quote implements Parcelable {

    @SerializedName("Symbol")
    final String symbol;
    @SerializedName("Date")
    final String date;
    @SerializedName("Open")
    final Double open;
    @SerializedName("High")
    final Double high;
    @SerializedName("Low")
    final Double low;
    @SerializedName("Close")
    final Double close;
    @SerializedName("Volume")
    final Long volume;
    @SerializedName("Adj_Close")
    final Double adjClose;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.symbol);
        dest.writeString(this.date);
        dest.writeValue(this.open);
        dest.writeValue(this.high);
        dest.writeValue(this.low);
        dest.writeValue(this.close);
        dest.writeValue(this.volume);
        dest.writeValue(this.adjClose);
    }

    protected Quote(Parcel in) {
        this.symbol = in.readString();
        this.date = in.readString();
        this.open = (Double) in.readValue(Double.class.getClassLoader());
        this.high = (Double) in.readValue(Double.class.getClassLoader());
        this.low = (Double) in.readValue(Double.class.getClassLoader());
        this.close = (Double) in.readValue(Double.class.getClassLoader());
        this.volume = (Long) in.readValue(Long.class.getClassLoader());
        this.adjClose = (Double) in.readValue(Double.class.getClassLoader());
    }

    public String getSymbol() {
        return symbol;
    }

    public String getDate() {
        return date;
    }

    public Double getOpen() {
        return open;
    }

    public Double getHigh() {
        return high;
    }

    public Double getLow() {
        return low;
    }

    public Double getClose() {
        return close;
    }

    public Long getVolume() {
        return volume;
    }

    public Double getAdjClose() {
        return adjClose;
    }

    public static final Creator<Quote> CREATOR = new Creator<Quote>() {
        @Override
        public Quote createFromParcel(Parcel source) {
            return new Quote(source);
        }

        @Override
        public Quote[] newArray(int size) {
            return new Quote[size];
        }
    };
}
