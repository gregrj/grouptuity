package com.grouptuity.database;

import java.util.ArrayList;

import android.content.ContentValues;
import com.grouptuity.database.TableColumn.ColumnConstraint;
import com.grouptuity.database.TableColumn.ColumnType;

public class BillsTable
{
	final static String tableName = "Bills";
	final static ArrayList<TableColumn> tableColumns = new ArrayList<TableColumn>();
	final static TableColumn ID = SQLTableHelper.generateIDColumn(tableName,tableColumns);
	final static TableColumn DATE = new TableColumn(tableName, "date", ColumnType.INTEGER, ColumnConstraint.UNIQUE, false);
	final static TableColumn NUMDINERS = new TableColumn(tableName, "numdiners", ColumnType.INTEGER, ColumnConstraint.NONE, false);
	final static TableColumn TOTAL = new TableColumn(tableName, "total", ColumnType.REAL, ColumnConstraint.NONE, false);
	final static TableColumn LOCATION = new TableColumn(tableName, "location", ColumnType.TEXT, ColumnConstraint.NONE, false);

	static{tableColumns.add(DATE);tableColumns.add(NUMDINERS);tableColumns.add(TOTAL);tableColumns.add(LOCATION);}

	static String getCreationSQL() {return SQLTableHelper.getCreationSQL(tableName, tableColumns);}
	static String getDropSQL() {return SQLTableHelper.getDropSQL(tableName);}
	static long add(Database database, long date, long numdiners, double total, String location)
	{
		ContentValues values = new ContentValues();
		values.put(DATE.columnName,date);
		values.put(NUMDINERS.columnName,numdiners);
		values.put(TOTAL.columnName,total);
		values.put(LOCATION.columnName,location);
		return database.insert(tableName,values);
	}
}