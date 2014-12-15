package com.grouptuity.database;

import java.util.ArrayList;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import com.grouptuity.database.TableColumn.ColumnConstraint;
import com.grouptuity.database.TableColumn.ColumnType;

public class DinersTable
{
	final static String tableName = "Diners";
	final static ArrayList<TableColumn> tableColumns = new ArrayList<TableColumn>();
	final static TableColumn ID = SQLTableHelper.generateIDColumn(tableName,tableColumns);
	final static TableColumn TOTAL = new TableColumn(tableName, "total", ColumnType.REAL, ColumnConstraint.NONE, false);
	final static TableColumn BILL = new TableColumn(tableName, "bill",	BillsTable.ID);
	final static TableColumn CONTACT = new TableColumn(tableName, "contact", ContactsTable.ID);

	static{tableColumns.add(TOTAL);tableColumns.add(BILL);tableColumns.add(CONTACT);}

	static String getCreationSQL() {return SQLTableHelper.getCreationSQL(tableName, tableColumns);}
	static String getDropSQL() {return SQLTableHelper.getDropSQL(tableName);}
	static long add(Database database, double total, long billID, long contactID)
	{
		ContentValues values = new ContentValues();
		values.put(TOTAL.columnName,total);
		values.put(BILL.columnName,billID);
		values.put(CONTACT.columnName,contactID);
		return database.insert(tableName,values);
	}

	static boolean update(SQLiteDatabase database, long dinerID, double total)
	{
		ContentValues values = new ContentValues();
		values.put(TOTAL.columnName,total);
		return (0<database.update(tableName, values, ID.columnName + " = ?", new String[]{String.valueOf(dinerID)}));
	}
}