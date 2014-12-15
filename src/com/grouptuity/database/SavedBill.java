package com.grouptuity.database;

import android.os.Parcel;

import com.grouptuity.Grouptuity;
import com.grouptuity.model.Bill;
import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;

public class SavedBill
{
	@DatabaseField(generatedId = true)
    private int id;

	@DatabaseField(dataType = DataType.BYTE_ARRAY)
	public byte[] billdata;
	private Bill bill;

	@SuppressWarnings("unused")
	private SavedBill(){}
	public SavedBill(Bill b){Parcel p = Parcel.obtain();b.writeToParcel(p,0);billdata = p.marshall();}
	public Bill getBill()
	{
		if(bill==null)
		{
			Parcel p = Parcel.obtain();
			p.unmarshall(billdata, 0, billdata.length);
			p.setDataPosition(0);
			try{bill = new Bill(p);}
			catch(Exception e){Grouptuity.log(e);return null;}
		}
		return bill;
	}
}