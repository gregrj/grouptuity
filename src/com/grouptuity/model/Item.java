package com.grouptuity.model;

import com.grouptuity.Grouptuity.BillableType;
import android.os.Parcel;
import android.os.Parcelable;

public class Item extends Billable implements Parcelable
{
	public Item(double v, BillableType type)
	{
		super(v, type);
		if(billableType==null || !billableType.isItem)
			billableType = BillableType.ENTREE;
	}

	public void writeToParcel(Parcel out, int flags){out.writeDouble(value);out.writeInt(billableType.ordinal());}
	private Item(Parcel in){this(in.readDouble(),BillableType.values()[in.readInt()]);}
	public int describeContents(){return 0;}
	public static final Parcelable.Creator<Item> CREATOR = new Parcelable.Creator<Item>(){public Item createFromParcel(Parcel in){return new Item(in);}public Item[] newArray(int size){return new Item[size];}};
}