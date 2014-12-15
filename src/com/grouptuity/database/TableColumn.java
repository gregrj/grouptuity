package com.grouptuity.database;

public class TableColumn
{
	final String tableName, columnName;
	final ColumnType type;
	final ColumnConstraint constraint;
	final TableColumn foreignKeyConstraint;
	final boolean isNull;

	static enum ColumnType{INTEGER,TEXT,REAL;}
	static enum ColumnConstraint
	{
		PRIMARY_KEY(" PRIMARY KEY"),
		PRIMARY_KEY_AI(" PRIMARY KEY AUTOINCREMENT"),
		UNIQUE(" UNIQUE"),
		UNIQUE_AI(" UNIQUE AUTOINCREMENT"),
		NONE("");

		final private String name;
		private ColumnConstraint(String fName){name = fName;}
	}

	public TableColumn(String tName, String fName, ColumnType t, ColumnConstraint constr, TableColumn fkey, boolean isnull)
	{
		tableName = tName;
		columnName = fName;
		type = t;
		constraint = constr;
		foreignKeyConstraint = fkey;
		isNull = isnull;
	}
	public TableColumn(String tName, String fName, ColumnType t, ColumnConstraint constr, boolean isnull){this(tName, fName, t, constr, null, isnull);}
	public TableColumn(String tName, String fName, TableColumn fkey){this(tName, fName, fkey.type, ColumnConstraint.NONE, fkey, fkey.isNull);}

	String getCreateSQL()
	{
		String createString =	columnName+" "+type.name()+constraint.name;
		if(!isNull)
			createString += " NOT NULL";
		if(foreignKeyConstraint != null)
			createString += " REFERENCES "+foreignKeyConstraint.tableName+" ("+foreignKeyConstraint.columnName+")";
		return createString;
	}
	String getFullName(){return tableName+"."+columnName;}
}