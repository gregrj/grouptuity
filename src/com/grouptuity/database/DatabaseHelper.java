package com.grouptuity.database;

import java.sql.SQLException;
import java.util.List;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.grouptuity.*;
import com.grouptuity.model.Bill;
import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.RuntimeExceptionDao;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper 
{
	final private static String DATABASE_NAME = "grouptuity_database";
	final private static int DATABASE_VERSION = 1;

	public RuntimeExceptionDao<SavedBill, Integer> billDao;

	public DatabaseHelper(Context context)
	{
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		try{billDao = RuntimeExceptionDao.createDao(connectionSource, SavedBill.class);}
		catch(SQLException e){Grouptuity.log(DatabaseHelper.class.getName() + ": Can't create database");}
	}

	public void saveBill(Bill b){deleteAll();SavedBill saved = new SavedBill(b);billDao.createOrUpdate(saved);}
	public Bill loadBill()
	{
		try
		{
			billDao.queryRaw("VACUUM");
			List<SavedBill> query = billDao.query(billDao.queryBuilder().orderBy("id", false).limit(1L).prepare());
			if(query!=null && query.size() != 0)
				return query.get(0).getBill();
		}
		catch(Exception e){Grouptuity.log("Error getting the bill from database!!");}
		return null;
	}
	public void deleteAll()
	{
		//TODO although this code should delete the database, it continues to grow
		try
		{
//			Grouptuity.log("DELETING ALL IN DB ("+billDao.queryRaw("SELECT COUNT(*) FROM savedBill").getResults().get(0)[0]+")");
			billDao.queryRaw("DELETE FROM savedBill");
//			Grouptuity.log("Remaining ("+billDao.queryRaw("SELECT COUNT(*) FROM savedBill").getResults().get(0)[0]+")");
//			TableUtils.dropTable(connectionSource, SavedBill.class, true);
//			TableUtils.createTable(connectionSource, SavedBill.class);
		}catch(Exception e){Grouptuity.log(DatabaseHelper.class.getName() + ": Can't drop and remake database");}
	}

	public void onCreate(SQLiteDatabase database, ConnectionSource arg1)
	{
		try{TableUtils.createTable(connectionSource, SavedBill.class);}
		catch(SQLException e){Grouptuity.log(DatabaseHelper.class.getName() + ": Can't create database");}
	}
	public void onUpgrade(SQLiteDatabase database, ConnectionSource arg1, int oldVersion, int newVersion)
	{
		try{TableUtils.dropTable(connectionSource, SavedBill.class, true);onCreate(database, connectionSource);}
		catch(SQLException e){Grouptuity.log(DatabaseHelper.class.getName()+": Can't drop databases");}
	}
	public void close(){super.close();billDao = null;}
}