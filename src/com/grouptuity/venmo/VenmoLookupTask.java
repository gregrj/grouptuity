package com.grouptuity.venmo;

import com.grouptuity.Grouptuity;
import com.grouptuity.model.Contact;
import com.grouptuity.replacements.AsyncTaskReplacement;

public class VenmoLookupTask extends AsyncTaskReplacement<Void, Integer, String>
{
	final public static int MAX_RETRIES = 2;
	final private Contact contact;
	final private int waitSeconds;

	static
	{
		CORE_POOL_SIZE = 2;
		MAXIMUM_POOL_SIZE = 2;
	}

	public VenmoLookupTask(Contact c, int wait)
	{
		contact = c;
		waitSeconds = wait;
	}

	protected void onPreExecute(){Grouptuity.venmoCurrentlyQuerying.put(contact,true);}
	protected String doInBackground(Void... params)
	{
		if(waitSeconds!=0)
			try{Thread.sleep(1000*waitSeconds);}
			catch(Exception e){Grouptuity.log(e);}
		return VenmoUtility.getVenmoUsername(contact);
	}
	protected void onProgressUpdate(Integer... progress){}
	protected void onPostExecute(String result)
	{
		if(result!=null)
		{
			contact.postVenmoUsernameLookup(result);
			if(result!=VenmoUtility.retryCode)
				Grouptuity.venmoUsernames.put(contact, contact.venmoUsername);
		}
		Grouptuity.venmoCurrentlyQuerying.remove(contact);
	}
}