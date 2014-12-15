package com.grouptuity.database;

import java.util.ArrayList;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import com.grouptuity.Grouptuity;
import com.grouptuity.Grouptuity.ContactsLoadTask;
import com.grouptuity.model.*;

public class Database
{
	final private static String DATABASE_NAME = "grouptuity_database";
	final private static int DATABASE_VERSION = 1;
	final private DatabaseOpenHelper dbHelper;
	final private SQLiteDatabase database;

	public Database(Context context){dbHelper = new DatabaseOpenHelper(context);database = dbHelper.getWritableDatabase();}

	public void close(){dbHelper.close();}

	long insert(String tableName, ContentValues values){return database.insert(tableName, null, values);}
	Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy){return database.query(table,columns,selection,selectionArgs,groupBy,having,orderBy);}

	public static void refreshPhoneNumbers(Contact contact)
	{
		if(contact.grouptuityOnly)
			return; //TODO need to lookup in saved contacts

		Uri lookupUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, contact.lookupKey);
		ContentResolver contentResolver = Grouptuity.context.getContentResolver();
		Cursor contactCursor = contentResolver.query(lookupUri, new String[]{Contacts._ID},null,null,null), phoneCursor = null;
		if(contactCursor.moveToFirst())
			try
			{
				phoneCursor = contentResolver.query(Phone.CONTENT_URI,new String[]{Phone.NUMBER,Phone.TYPE},Phone.CONTACT_ID+" = "+contactCursor.getString(0),null,null);
				String[] phoneNumbers = new String[phoneCursor.getCount()];
				int[] phoneNumberTypes = new int[phoneCursor.getCount()];
				int i = 0;
				while(phoneCursor.moveToNext())
				{
					phoneNumbers[i] = phoneCursor.getString(0);
					phoneNumberTypes[i] = phoneCursor.getInt(1);
					i++;
				}
				contact.phoneNumbers = phoneNumbers;
				contact.phoneNumberTypes = phoneNumberTypes;
			}
			finally{if(contactCursor!=null)contactCursor.close();if(phoneCursor!=null)phoneCursor.close();}
	}

	public static void refreshEmailAddresses(Contact contact)
	{
		if(contact.grouptuityOnly)
			return; //TODO need to lookup in saved contacts

		Uri lookupUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, contact.lookupKey);
		ContentResolver contentResolver = Grouptuity.context.getContentResolver();
		Cursor contactCursor = contentResolver.query(lookupUri, new String[]{Contacts._ID},null,null,null), emailCursor = null;
		if(contactCursor.moveToFirst())
			try
			{
				emailCursor = contentResolver.query(Email.CONTENT_URI,new String[]{Email.ADDRESS,Email.TYPE},Email.CONTACT_ID+" = "+contactCursor.getString(0),null,null);
				String[] emailAddresses = new String[emailCursor.getCount()];
				int[] emailAddressTypes = new int[emailCursor.getCount()];
				int i = 0;
				while(emailCursor.moveToNext())
				{
					//useless "MobileLife Contacts" entry appears as an email for newer TMobile phones, breaks some functionality
					if( ! emailCursor.getString(0).equals("MobileLife Contacts") ) {
						emailAddresses[i] = emailCursor.getString(0);
						emailAddressTypes[i] = emailCursor.getInt(1);
						i++;
					}
				}
				contact.emailAddresses = emailAddresses;
				contact.emailAddressTypes = emailAddressTypes;
			}
			finally{if(contactCursor!=null)contactCursor.close();if(emailCursor!=null)emailCursor.close();}
	}

	public static ArrayList<Contact> getAllContacts(ContactsLoadTask task)
	{
		ArrayList<Contact> allContacts = new ArrayList<Contact>();

		ContentResolver contentResolver = Grouptuity.context.getContentResolver();
		Cursor contactsCursor = contentResolver.query(Contacts.CONTENT_URI,new String[]{Contacts._ID,Contacts.LOOKUP_KEY,Contacts.DISPLAY_NAME},null,null,null);
		int numContacts = contactsCursor.getCount();
		int counter = 0;

		while(contactsCursor.moveToNext())
		{
			String name = contactsCursor.getString(2);
			if(name==null || name.trim().equals(""))
				continue;
			String lookupKey = contactsCursor.getString(1);

			//If the lookupKey points to an old contact, reuse that contact
			if(lookupKey!=null)
			{
				Contact oldContact = Grouptuity.contactsByLookupKey.get(lookupKey);
				if(oldContact!=null)
				{
					allContacts.add(oldContact);
					if(task!=null)
						task.reportProgress((100*counter++)/numContacts);
					continue;
				}
			}

			allContacts.add(new Contact(false,lookupKey,name.trim(),null,null,null));
			if(task!=null)
				task.reportProgress((100*counter++)/numContacts);
		}
		contactsCursor.deactivate();
		return allContacts;
	}
	
	private class DatabaseOpenHelper extends SQLiteOpenHelper
	{
		public DatabaseOpenHelper(Context context){super(context, DATABASE_NAME, null, DATABASE_VERSION);}

		public void onCreate(SQLiteDatabase database)
		{
			//order is important here, anything referencing another table must come after that table
			Grouptuity.log(BillsTable.getCreationSQL());
			Grouptuity.log(ContactsTable.getCreationSQL());
			Grouptuity.log(DinersTable.getCreationSQL());
			database.execSQL(BillsTable.getCreationSQL());
			database.execSQL(ContactsTable.getCreationSQL());
			database.execSQL(DinersTable.getCreationSQL());
		}
		public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion)
		{
			database.execSQL(BillsTable.getDropSQL());
			database.execSQL(DinersTable.getDropSQL());
			database.execSQL(ContactsTable.getDropSQL());
			onCreate(database);
		}
	}
}