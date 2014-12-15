package com.grouptuity;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;

public class BackupAgent extends BackupAgentHelper
{
	@Override
	public void onCreate(){addHelper("prefs", new SharedPreferencesBackupHelper(this, "user_preferences"));}
}