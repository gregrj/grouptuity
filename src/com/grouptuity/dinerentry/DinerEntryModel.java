package com.grouptuity.dinerentry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import android.os.Bundle;
import com.grouptuity.*;
import com.grouptuity.model.*;

public class DinerEntryModel extends BillModelTemplate
{
	//ACTIVITY STATES
	final static int DINER_ENTRY_DEFAULT = 0;
	final static int DINER_ENTRY_TABLE_PLACEMENT = 1;
	final static int DINER_ENTRY_REVIEW_DINERS = 2;

	final protected ArrayList<Contact> suggestedContacts;
	final private Comparator<Contact> comparator;
	protected String dinerNameSearch;
	private boolean addedContactBasedOnCurrentSearch;

	public DinerEntryModel(ActivityTemplate<?> activity, Bundle bundle)
	{
		super(activity,bundle);
		suggestedContacts = new ArrayList<Contact>();
		comparator = new Comparator<Contact>(){public int compare(Contact c1, Contact c2)
		{
			if(c1.searchMatchScore == c2.searchMatchScore)
				return -(c1.name.compareToIgnoreCase(c2.name));
			return (int)(100.0*(c1.searchMatchScore - c2.searchMatchScore));
		}};

		if(bundle==null)
		{
			bundle = activity.getIntent().getExtras();
			if(bundle==null)
				processNewSearch("");
			else
				processNewSearch(bundle.getString("com.grouptuity.dinerNameSearch"));
		}
		else
			processNewSearch(bundle.getString("com.grouptuity.dinerNameSearch"));
	}

	protected void saveState(Bundle bundle){bundle.putString("com.grouptuity.dinerNameSearch",dinerNameSearch);}

	protected boolean getAddedContactBasedOnCurrentSearch(){return addedContactBasedOnCurrentSearch;}
	protected void setAddedContactBasedOnCurrentSearch(boolean added){addedContactBasedOnCurrentSearch = added;}

	protected void clearSuggestedContacts(){suggestedContacts.clear();}
	protected void processNewSearch(String searchString)
	{
		if(searchString==null)
			searchString = "";
		else
			searchString = searchString.trim();
		dinerNameSearch = searchString;
		if(!Grouptuity.SEARCH_ALGORITHM.getValue())
			searchString = "";

		clearSuggestedContacts();
		for(Contact contact: Grouptuity.contacts)
			contact.searchMatchScore = computeMatchScore(contact,searchString);
		Collections.sort(Grouptuity.contacts,comparator);
		for(int i=Grouptuity.contacts.size()-1; i>=0; i--)
		{
			if(Grouptuity.contacts.get(i).searchMatchScore<0)
				break;
			suggestedContacts.add(Grouptuity.contacts.get(i));
		}

		if(!dinerNameSearch.equals(""))
		{
			boolean abortInsertion = false;
			for(Diner d: Grouptuity.getBill().diners)
				if(d.name.compareToIgnoreCase(dinerNameSearch)==0){abortInsertion = true;break;}
			if(!abortInsertion)
				for(Contact c: suggestedContacts)
					if(c.name.compareToIgnoreCase(dinerNameSearch)==0)
						abortInsertion = true;
			if(!abortInsertion)
			{
				Contact insertion = new Contact(true, null, dinerNameSearch, null, null, null);
				if(Grouptuity.SEARCH_ALGORITHM.getValue()){suggestedContacts.add(0,insertion);return;}
				insertion.searchMatchScore = computeMatchScore(insertion,searchString);
				for(int i=0; i<suggestedContacts.size(); i++)
				{
					double comparisonScore = suggestedContacts.get(i).searchMatchScore;
					if(comparisonScore<insertion.searchMatchScore)
					{suggestedContacts.add(i,insertion);break;}
					else if(comparisonScore==insertion.searchMatchScore && insertion.name.compareToIgnoreCase(suggestedContacts.get(i).name)<0)
					{suggestedContacts.add(i,insertion);break;}
				}
			}
		}
	}
	private double computeMatchScore(Contact contact, String searchString)
	{
		if(contact==null || contact.name==null || contact.name.trim().equals(""))
			return -1;
		for(Diner d: Grouptuity.getBill().diners)
			if(d.contact==contact)
				return -1;
		if(searchString.trim().equals(""))
			return 0;

		String[] contactNames = contact.name.split(" ");
		contactNames[0] = contact.name;
		double[] scores = new double[contactNames.length];
		for(int i=0; i<contactNames.length; i++)
		{
			char[] searchChars = searchString.toLowerCase().toCharArray();
			char[] contactChars = contactNames[i].toLowerCase().toCharArray();
			for(int j=0; j<searchChars.length;j++)
			{
				double multiplier = 10/(1+j)*(100.0/(1+0.3*i));
				if(j==0)
				{
					if(contactChars.length>1)
						scores[i] += multiplier*(2*Grouptuity.scoreChar(searchChars[0],contactChars[0]) + Grouptuity.scoreChar(searchChars[0],contactChars[1]));
					else if(contactChars.length>0)
						scores[i] += multiplier*(2*Grouptuity.scoreChar(searchChars[0],contactChars[0]));
				}
				else
				{
					if(contactChars.length>j+1)
						scores[i] += multiplier*(2*Grouptuity.scoreChar(searchChars[j],contactChars[j]) + Grouptuity.scoreChar(searchChars[j],contactChars[j+1]) + Grouptuity.scoreChar(searchChars[j],contactChars[j-1]));
					else if(contactChars.length>j)
						scores[i] += multiplier*(2*Grouptuity.scoreChar(searchChars[j],contactChars[j]) + Grouptuity.scoreChar(searchChars[j],contactChars[j-1]));
					else if(contactChars.length>j-1)
						scores[i] += multiplier*(Grouptuity.scoreChar(searchChars[j],contactChars[j-1]));
					else
						break;
				}
			}
		}
		double returnScore = 0;
		for(int i=0; i<scores.length; i++)
		{
			if(scores[i]>returnScore)
				returnScore = (scores[i]);
		}

		return returnScore;
	}
}