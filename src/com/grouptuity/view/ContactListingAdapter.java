package com.grouptuity.view;

import com.grouptuity.*;
import com.grouptuity.model.*;
import com.grouptuity.view.ContactListing;
import com.grouptuity.view.ContactListing.ContactListingStyle;
import java.util.ArrayList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.SectionIndexer;

public class ContactListingAdapter extends BaseAdapter implements SectionIndexer
{
	final private ActivityTemplate<?> context;
	final private ContactListingAdapterController controllerRef;
	final private ArrayList<Contact> contacts;
	final private ArrayList<Diner> diners;
	final private ContactListingStyle contactListingStyle;
	final private Bitmap defaultIcon;
	final private Bitmap[] paymentImages;
	final private boolean dinerMode, sectionIndexing;
	final private static String[] defaultSections = new String[]{"A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z"};

	public static interface ContactListingAdapterController extends ControllerTemplate
	{
		void onContactClick(Contact c);
		void onDinerClick(Diner d);
		void onDinerLongClick(Diner d);
		void onPaymentToggle(Diner d);
	}

	public ContactListingAdapter(ActivityTemplate<?> c, ArrayList<Diner> list, Bitmap[] pImages, ContactListingAdapterController control)
	{this(c,true,true,ContactListingStyle.PAYMENT_MODE_SELECTOR,null,list,pImages,control);}
	public ContactListingAdapter(ActivityTemplate<?> c, ContactListingStyle cls, ArrayList<Diner> list, ContactListingAdapterController control)
	{this(c,true,true,cls,null,list,null,control);}
	public ContactListingAdapter(ActivityTemplate<?> c, boolean sections, ArrayList<Contact> list, ContactListingStyle cls, ContactListingAdapterController control)
	{this(c,false,sections,cls,list,null,null,control);}
	private ContactListingAdapter(ActivityTemplate<?> c, boolean dMode, boolean sections, ContactListingStyle cls, ArrayList<Contact> contactsList, ArrayList<Diner> dinersList, Bitmap[] pImages, ContactListingAdapterController control)
	{
		dinerMode = dMode;
		sectionIndexing = sections;
		context = c;
		contacts = contactsList;
		diners = dinersList;
		contactListingStyle = cls;
		controllerRef = control;
		paymentImages = pImages;
		defaultIcon = BitmapFactory.decodeResource(context.getResources(),R.drawable.contact_icon);
	}

	public int getCount(){return dinerMode?diners.size():contacts.size();}
	public Object getItem(int position){return dinerMode?diners.get(position):contacts.get(position);}
	public long getItemId(int position){return position;}

	public View getView(int position, View convertView, ViewGroup parent)
	{
		ContactListing contactListing;
		if(convertView == null)
		{
			if(dinerMode)
			{
				contactListing = new ContactListing(context, diners.get(position), contactListingStyle)
				{
					protected void longClickCallback(Diner d){controllerRef.onDinerLongClick(d);}
					protected void clickCallback(Diner d){controllerRef.onDinerClick(d);}
					protected void paymentToggle(Diner d){controllerRef.onPaymentToggle(d);}
				};
				if(contactListingStyle==ContactListingStyle.PAYMENT_MODE_SELECTOR)
					contactListing.paymentImages = paymentImages;
			}
			else
				contactListing = new ContactListing(context, contacts.get(position), contactListingStyle){protected void clickCallback(Contact c){controllerRef.onContactClick(c);}};
			contactListing.defaultPhoto = defaultIcon;
		}
		else
			contactListing = (ContactListing) convertView;

		if(contactListing.contact.loadingPhotoTask!=null)
		{
			contactListing.contact.loadingPhotoTask.cancel(false);
			contactListing.contact.loadingPhotoTask = null;
		}

		if(dinerMode)
		{
			contactListing.diner = diners.get(position);
			contactListing.contact = contactListing.diner.contact;
		}
		else
			contactListing.contact = contacts.get(position);

		contactListing.setViewState(contactListing.viewState);
		contactListing.refresh();
		return contactListing;
	}

	public int getPositionForSection(int section)
	{
		if(sectionIndexing && contacts!=null)
		{
			for(int i=0; i<contacts.size(); i++)
			{
				String name = contacts.get(i).name.trim().toUpperCase();
				if(name.equals(""))
					continue;
				char c = name.substring(0,1).toCharArray()[0];
				switch(c)
				{
					case 'A':case 'B':case 'C':case 'D':case 'E':case 'F':case 'G':case 'H':
					case 'I':case 'J':case 'K':case 'L':case 'M':case 'N':case 'O':case 'P':
					case 'Q':case 'R':case 'S':case 'T':case 'U':case 'V':case 'W':case 'X':
					case 'Y':case 'Z':	if(c==defaultSections[section].toCharArray()[0])
											return i;
										else if(c>defaultSections[section].toCharArray()[0])
											return i;
										break;
				}
			}
		}
		return 0;
	}
	public int getSectionForPosition(int position)
	{
		if(sectionIndexing && contacts!=null)
		{
			String name = contacts.get(position).name.trim().toUpperCase();
			if(!name.equals(""))
				for(int i=0; i<defaultSections.length; i++)
					if(name.substring(0,1).equals(defaultSections[i]))
						return i;
		}
		return 0;
	}
	public Object[] getSections()
	{
		if(!sectionIndexing || contacts==null)
			return null;
		else
			return defaultSections;
	}
}