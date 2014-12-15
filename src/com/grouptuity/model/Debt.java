package com.grouptuity.model;

import com.grouptuity.Grouptuity.BillableType;
import android.os.Parcel;
import android.os.Parcelable;

public class Debt extends Billable implements Parcelable
{
	public Discount linkedDiscount;

	public Debt(double v){super(v,BillableType.DEBT);}

	public void update()
	{
		value = linkedDiscount.cost;
		
		for(Diner d: payers)
			d.removeDebt(this);
		for(Diner d: payees)
			d.removeDebt(this);
		clearPayees();
		clearPayers();
		for(Diner d: linkedDiscount.payers)
		{
			addPayee(d, linkedDiscount.getWeightForPayer(d));
			d.addDebt(this);
		}
		for(Diner d: linkedDiscount.payees)
		{
			addPayer(d, linkedDiscount.getWeightForPayee(d));
			d.addDebt(this);
		}
	}

	public void writeToParcel(Parcel out, int flags){out.writeDouble(value);}
	private Debt(Parcel in){this(in.readDouble());}
	public int describeContents(){return 0;}
	public static final Parcelable.Creator<Debt> CREATOR = new Parcelable.Creator<Debt>(){public Debt createFromParcel(Parcel in){return new Debt(in);}public Debt[] newArray(int size){return new Debt[size];}};
}