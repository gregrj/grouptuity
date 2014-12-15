package com.grouptuity.database;

import java.util.ArrayList;
import android.content.ContentValues;
import android.database.Cursor;
import com.grouptuity.database.TableColumn.ColumnConstraint;
import com.grouptuity.database.TableColumn.ColumnType;

public class ContactsTable
{
	final static String tableName = "Contacts";
	final static ArrayList<TableColumn> tableColumns = new ArrayList<TableColumn>();
	final static TableColumn ID = SQLTableHelper.generateIDColumn(tableName,tableColumns);
	final static TableColumn NAME = new TableColumn(tableName, "name", ColumnType.TEXT, ColumnConstraint.NONE, false);
	final static TableColumn LOOKUPKEY = new TableColumn(tableName, "contactref", ColumnType.TEXT, ColumnConstraint.UNIQUE, true);
	final static TableColumn EMAIL = new TableColumn(tableName, "email", ColumnType.TEXT, ColumnConstraint.NONE, true);
	final static TableColumn PHONE = new TableColumn(tableName, "phone", ColumnType.TEXT, ColumnConstraint.NONE, true);

	static{tableColumns.add(NAME);tableColumns.add(LOOKUPKEY);tableColumns.add(EMAIL);tableColumns.add(PHONE);}

	static String getCreationSQL() {return SQLTableHelper.getCreationSQL(tableName, tableColumns);}
	static String getDropSQL() {return SQLTableHelper.getDropSQL(tableName);}
	static long add(Database database, String name, String lookupKey, String email, String phone)
	{
		ContentValues values = new ContentValues();
		values.put(NAME.columnName,name);
		values.put(LOOKUPKEY.columnName,lookupKey);
		values.put(EMAIL.columnName,email);
		values.put(PHONE.columnName,phone);
		return database.insert(tableName,values);
	}

	//checks whether the given lookupKey is already in the database, and returns that ID. Returns -1 otherwise.
	static long getID(Database database, String lookupKey)
	{
		Cursor cur = database.query(tableName,new String[]{ID.columnName},LOOKUPKEY.columnName+" = ?",new String[] {lookupKey}, null,null,null);
		if(cur.moveToFirst())
			return cur.getLong(0);
		else
			return -1;
	}
}