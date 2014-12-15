package com.grouptuity.model;

import com.grouptuity.Grouptuity.PaymentType;
import java.util.ArrayList;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import com.grouptuity.*;
import com.grouptuity.replacements.AsyncTaskReplacement;
import com.grouptuity.venmo.*;

public class Contact implements Parcelable
{
	final public boolean grouptuityOnly;
	public final String lookupKey, name;
	private String defaultEmailAddress;
	private boolean isEmailAddressChosen;
	public boolean isSelf;
	public final String phone;
	public String[] emailAddresses = new String[0], phoneNumbers = new String[0];
	public int[] emailAddressTypes = new int[0], phoneNumberTypes = new int[0];
	public Bitmap photo;
	public PaymentType defaultPaymentType;
	public double searchMatchScore;
	public AsyncTaskReplacement<?,?,?> loadingPhotoTask;
	public String venmoUsername;
	private int venmoLookupCount;

	public Contact(boolean only, String lookupkey, String nname, String eemail, String pphone, String venmo){this(null, only, lookupkey, nname, eemail, pphone, venmo);}
	public Contact(final Context context, boolean only, String lookupkey, String nname, String eemail, String pphone, String venmo)
	{
		grouptuityOnly = only;
		lookupKey = lookupkey;
		name = nname;
		defaultEmailAddress = eemail;
		isEmailAddressChosen = defaultEmailAddress!=null;
		phone = pphone;
		venmoUsername = venmo;
	}

	public static Contact createSelf(Context context)
	{
		Contact c = new Contact(true,"",Grouptuity.USER_NAME.getValue(),null,null,Grouptuity.VENMO_USER_NAME.getValue());
		c.isSelf = true;
		ArrayList<String> emails = new ArrayList<String>();
		for(Account account: AccountManager.get(context).getAccounts())
		{
			String str = Grouptuity.validateEmailFormat(account.name);
			if(str!=null && !emails.contains(str))
				emails.add(str);
		}
		c.emailAddresses = emails.toArray(new String[emails.size()]);
		if(c.emailAddresses.length>0)
		{
			c.defaultEmailAddress = c.emailAddresses[0];
			c.isEmailAddressChosen = true;
		}
		if(c.venmoUsername==null)
			c.refreshVenmoUsername();
		return c;
	}

	public String getDefaultEmailAddress(){return defaultEmailAddress;}
	public void setDefaultEmailAddress(String email){defaultEmailAddress = email;isEmailAddressChosen = true;}
	public boolean isDefaultEmailAddressSet(){return isEmailAddressChosen;}

	public void refreshVenmoUsername()
	{
		if(isSelf && Grouptuity.VENMO_USER_NAME.getValue()!=null)
		{
			venmoUsername = Grouptuity.VENMO_USER_NAME.getValue();
			return;
		}

		venmoUsername = Grouptuity.venmoUsernames.get(name);
		if(venmoUsername==null && !Grouptuity.venmoCurrentlyQuerying.containsKey(name) && Grouptuity.VENMO_ENABLED.getValue())
		{
			venmoLookupCount = 0;
			new VenmoLookupTask(this,0).execute();
		}
	}
	public void postVenmoUsernameLookup(String result)
	{
		if(result==null)
			return;
		else if(!result.equals(VenmoUtility.retryCode))
		{
			venmoUsername = result;
		}
		else if(venmoLookupCount<VenmoLookupTask.MAX_RETRIES)
		{
			venmoLookupCount++;
			new VenmoLookupTask(this,1+venmoLookupCount*venmoLookupCount).execute();
		}
	}


	public void writeToParcel(Parcel out, int flags)
	{
		out.writeInt(grouptuityOnly?1:0);
		out.writeString(lookupKey);
		out.writeString(name);
		out.writeString(defaultEmailAddress);
		out.writeString(phone);
		out.writeString(venmoUsername);

		out.writeInt((isSelf)?1:0);
		out.writeInt(isEmailAddressChosen?1:0);

		out.writeInt(emailAddresses.length);
		out.writeStringArray(emailAddresses);
		out.writeInt(phoneNumbers.length);
		out.writeStringArray(phoneNumbers);
		out.writeInt(emailAddressTypes.length);
		out.writeIntArray(emailAddressTypes);
		out.writeInt(phoneNumberTypes.length);
		out.writeIntArray(phoneNumberTypes);
	}
	private Contact(Parcel in)
	{
		this(in.readInt()==1,in.readString(),in.readString(),in.readString(),in.readString(),in.readString());

		isSelf = in.readInt()==1;
		isEmailAddressChosen = in.readInt()==1;

		emailAddresses = new String[in.readInt()];
		in.readStringArray(emailAddresses);
		phoneNumbers = new String[in.readInt()];
		in.readStringArray(phoneNumbers);
		emailAddressTypes = new int[in.readInt()];
		in.readIntArray(emailAddressTypes);
		phoneNumberTypes = new int[in.readInt()];
		in.readIntArray(phoneNumberTypes);
	}
	public int describeContents(){return 0;}
	public static final Parcelable.Creator<Contact> CREATOR = new Parcelable.Creator<Contact>(){public Contact createFromParcel(Parcel in){return new Contact(in);}public Contact[] newArray(int size){return new Contact[size];}};
}