package com.grouptuity.database;

import java.util.ArrayList;

import com.grouptuity.database.TableColumn.ColumnConstraint;
import com.grouptuity.database.TableColumn.ColumnType;

public class SQLTableHelper
{
	static TableColumn generateIDColumn(String tableName, ArrayList<TableColumn> tableColumns)
	{
		TableColumn ID = new TableColumn(tableName, "_id", ColumnType.INTEGER, ColumnConstraint.PRIMARY_KEY_AI, false);
		tableColumns.add(ID);
		return ID;
	}

	static String getCreationSQL(String tableName, ArrayList<TableColumn> tableColumns)
	{
		String createString = "CREATE TABLE "+tableName+" (";
		for(TableColumn column:tableColumns)
			createString += column.getCreateSQL()+", ";
		createString = createString.substring(0, createString.length()-2)+");"; //remove last comma
		return createString;
	}
	static String getDropSQL(String tableName){return "DROP TABLE IF EXISTS "+tableName;}
}
