package com.grouptuity;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import com.grouptuity.database.Database;
import com.grouptuity.database.DatabaseHelper;
import com.grouptuity.model.*;

public class Grouptuity extends Application
{
	public static Context context;
	private static DatabaseHelper database;
	private static Bill bill, quickCalcBill;
	private static Diner restaurant, self;
	public static SharedPreferences preferences;
	public static BackupManager backupManager;
	public static HashMap<String,GrouptuityPreference<?>> preferencesHashMap;
	public static ContactsLoadTask contactsLoadTask;
	public static ArrayList<ActivityTemplate<?>> contactLoadListeners;
	public static ArrayList<Contact> contacts;
	public static HashMap<String,Contact> contactsByLookupKey;
	public static int contactsLoadPercent;
	public static boolean contactsLoaded;
	public static int contactIconSize;
	public static HashMap<Contact,String> venmoUsernames;
	public static HashMap<Contact,Boolean> venmoCurrentlyQuerying;
	public static boolean terminateIfNotMainMenu;

	//HARDCODED STUFF
	final public static String VENMO_APP_ID = "####";
	final public static String VENMO_SECRET = "################################";
	
	final public static String TAG = "Grouptuity";
	final public static boolean DEBUG_MODE = false;
	final public static boolean FAKE_VENMO_PAYMENTS = false;
	final public static boolean OK_ON_LEFT = true;
	final public static String RESTAURANT_NAME = "restaurant";
	final public static double PRECISION_ERROR = 0.001;
	final public static int MENU_ID_OPTIONS = 0;
	final public static int MENU_ID_PAYMENT_SETTINGS = 1;
	final public static int savedBillLifetimeMinutes = 180;
	final public static long maximumFirstRunDelayForCreditGrantMin = 1000L*60*60*24*30;
	final public static int foregroundColor = Color.WHITE;
	final public static int backgroundColor = 0xFFFFEFCD;
	final public static int lightColor = 0xFFC80815;
	final public static int mediumColor = 0xFF65000B;
	final public static int darkColor = 0xFF5B0000;
	final public static int shroudColor = 0xE2505050;
	final public static int softTextColor = 0xFF555555;
	
	//PREFERENCES
	public static GrouptuityPreference<Boolean> HORIZONTAL_ENABLED;
	public static GrouptuityPreference<String> USER_NAME;
	public static GrouptuityPreference<Boolean> USER_NAME_CHOSEN;
	public static GrouptuityPreference<Float> DEFAULT_TAX, DEFAULT_TIP;
	public static GrouptuityPreference<Float> QUICK_CALC_TAX, QUICK_CALC_TIP, QUICK_CALC_SUBTOTAL;
	public static GrouptuityPreference<Integer> QUICK_CALC_OVERRIDE;
	public static GrouptuityPreference<Boolean> TIP_THE_TAX;
	public static GrouptuityPreference<Integer> DISCOUNT_TAX_METHOD;
	public static GrouptuityPreference<Integer> DISCOUNT_TIP_METHOD;
	public static GrouptuityPreference<Boolean> ENABLE_ADVANCED_ITEMS;
	public static GrouptuityPreference<Boolean> INCLUDE_SELF;
	public static GrouptuityPreference<Boolean> SEARCH_ALGORITHM;
	public static GrouptuityPreference<Integer> DEFAULT_PAYMENT_TYPE;
	public static GrouptuityPreference<Boolean> VENMO_ENABLED;
	public static GrouptuityPreference<String> VENMO_USER_NAME;
	public static GrouptuityPreference<String> VENMO_USER_NAME_LOOKUP_DATA;
	public static GrouptuityPreference<String> VENMO_USER_NAME_LOOKUP_DATA_TYPE;
	public static GrouptuityPreference<Integer> VENMO_ALTERNATIVE;
	public static GrouptuityPreference<Boolean> VENMO_BY_3RD_PARTY;
	public static GrouptuityPreference<Integer> DEFAULT_ROUNDING_RULE;
	public static GrouptuityPreference<String> DECIMAL_SYMBOL;
	public static GrouptuityPreference<String> NUMBER_GROUPING_SYMBOL;
	public static GrouptuityPreference<Integer> NUMBER_GROUPING_DIGITS;
	public static GrouptuityPreference<String> CURRENCY_PREFIX_SYMBOL;
	public static GrouptuityPreference<String> CURRENCY_POSTFIX_SYMBOL;
	public static GrouptuityPreference<Integer> CURRENCY_DECIMAL_PLACES;
	public static GrouptuityPreference<Boolean> SHOW_EULA;
	public static GrouptuityPreference<Boolean> SHOW_TUTORIAL_DINERENTRY;
	public static GrouptuityPreference<Boolean> SHOW_TUTORIAL_ITEMENTRY;
	public static GrouptuityPreference<Boolean> SHOW_TUTORIAL_BILLCALC;
	public static GrouptuityPreference<Boolean> SHOW_TUTORIAL_BILLREVIEW;
	public static GrouptuityPreference<Boolean> SHOW_TUTORIAL_INDIVBILL;
	public static GrouptuityPreference<Boolean> SHOW_TUTORIAL_QUICKCALC;

	public static enum RoundingRule{ROUND_DOWN, ROUND_UP, ROUND_TO_EVEN, ROUND_TO_ODD;}
	public static enum PaymentType
	{
		CASH("Cash",R.drawable.payment_cash,false),
		CREDIT("Credit Card",R.drawable.payment_credit,false),
		VENMO("Venmo",R.drawable.payment_venmo,true),
		IOU_EMAIL("IOU Email",R.drawable.payment_iou_email,true);

		final public String description;
		final public int imageInt;
		final public boolean deferred;

		private PaymentType(String desc, int i, boolean d){description = desc;imageInt = i;deferred = d;}
	}
	public static enum BillableType
	{
		ENTREE("Entr\u00E9e",true,false,false),
		SIDE("Side",true,false,false),
		DRINK("Drink",true,false,false),
		OTHER("Other",true,false,false),
		DEBT("Personal Debt",false,true,false),
		DEAL_PERCENT_ITEM("Item Percent Discount",false,false,true),
		DEAL_PERCENT_BILL("Bill Percent Discount",false,false,true),
		DEAL_FIXED("Fixed Value Discount",false,false,true),
		DEAL_GROUP_VOUCHER("Group Deal Voucher",false,false,true);

		final public String name;
		final public boolean isItem, isDebt, isDiscount;
		private BillableType(String n, boolean item, boolean debt, boolean discount){name = n;isItem=item;isDebt=debt;isDiscount=discount;}
	}
	public static enum NumberFormat
	{
		NUMBER(true,0.0,1000.0,0.0),
		INTEGER(true,0,100,0),
		CURRENCY(true,0.0,1000.0,-1),
		CURRENCY_NO_ZERO(true,0.0,1000.0,PRECISION_ERROR),
		TAX_PERCENT(true,0.0,10.0,-PRECISION_ERROR),
		TIP_PERCENT(true,0.0,10.0,-PRECISION_ERROR);

		final public boolean negativeBeforePrefix;
		final public double initialValue, maxValue, minValue;

		private NumberFormat(boolean before, double initial, double max, double min)
		{
			negativeBeforePrefix = before;
			initialValue = initial;
			maxValue = max;
			minValue = min;
		}

		//Preferences cannot be loaded in static block so the prefix and post fix must be queried at runtime
		public String getPrefix()
		{
			switch(this)
			{
			case CURRENCY:
			case CURRENCY_NO_ZERO:	return Grouptuity.CURRENCY_PREFIX_SYMBOL.getValue();
			default:				return "";
			}
		}
		public String getPostfix()
		{
			switch(this)
			{
			case CURRENCY:
			case CURRENCY_NO_ZERO:	return Grouptuity.CURRENCY_POSTFIX_SYMBOL.getValue();
			case TAX_PERCENT:		
			case TIP_PERCENT:		return "%";
			default:				return "";
			}
		}
		public int getNumDecimalPlaces()
		{
			switch(this)
			{
			case CURRENCY:			
			case CURRENCY_NO_ZERO:	return Grouptuity.CURRENCY_DECIMAL_PLACES.getValue();
			case TAX_PERCENT:		return 2;
			case TIP_PERCENT:		return 1;
			default:				return 2;
			}
		}
	}

	@SuppressLint("ShowToast")
	public void onCreate()
	{
		super.onCreate();
		context = getApplicationContext();
		contactIconSize = toActualPixels(48);
		backupManager = new BackupManager(context);
		preferences = PreferenceManager.getDefaultSharedPreferences(context);
		preferencesHashMap = new HashMap<String, GrouptuityPreference<?>>();
		contactLoadListeners = new ArrayList<ActivityTemplate<?>>();
		contacts = new ArrayList<Contact>();
		contactsByLookupKey = new HashMap<String,Contact>();
		venmoUsernames = new HashMap<Contact, String>();
		venmoCurrentlyQuerying = new HashMap<Contact, Boolean>();
		database = new DatabaseHelper(context);

		createPreferences();

		restaurant = new Diner(new Contact(true, null, RESTAURANT_NAME, null, null,null));
		bill = database.loadBill();
		if(bill!=null)
		{
			for(Diner d: bill.diners)
				if(d.isSelf)
				{
					self = d;
					break;
				}
		}
		quickCalcBill = new Bill();
		if(bill==null || (new Date().getTime() - bill.date) > 1000*60*savedBillLifetimeMinutes)
		{
			resetBill();
			terminateIfNotMainMenu = true;
		}
		else
		{
			quickCalcBill.overrideRawSubTotal(QUICK_CALC_SUBTOTAL.getValue());
			quickCalcBill.setTaxPercent(QUICK_CALC_TAX.getValue());
			quickCalcBill.setTipPercent(QUICK_CALC_TIP.getValue());
			quickCalcBill.overrideOverride(QUICK_CALC_OVERRIDE.getValue());
		}
		for(Diner d: bill.diners){if(d.contact.lookupKey!=null)contactsByLookupKey.put(d.contact.lookupKey,d.contact);}

		reloadContacts();
	}

	final private static void createPreferences()
	{
		String bestAccountName = null;
		for(Account account: AccountManager.get(context).getAccounts())
		{
			String candidate = account.name.trim();
			if(!candidate.equals("") && !candidate.equals("MobileLife Contacts"))
			{
				if(candidate.endsWith("@gmail.com"))
				{
					bestAccountName = candidate.substring(0,candidate.length()-10);
					break;
				}
				if(bestAccountName==null)
					bestAccountName = candidate;
			}
		}
		if(bestAccountName==null)
			bestAccountName = "Me";

		//ACCOUNT
		USER_NAME = new GrouptuityPreference<String>("USER_NAME",bestAccountName);
		USER_NAME_CHOSEN = new GrouptuityPreference<Boolean>("USER_NAME_CHOSEN",false);

		//DINER ENTRY
		INCLUDE_SELF = new GrouptuityPreference<Boolean>("INCLUDE_SELF",true);
		SEARCH_ALGORITHM = new GrouptuityPreference<Boolean>("SEARCH_ALGORITHM",true);

		//ITEM ENTRY
		ENABLE_ADVANCED_ITEMS = new GrouptuityPreference<Boolean>("ENABLE_ADVANCED_ITEMS",true);

		//BILL CALCULATION
		DEFAULT_TAX = new GrouptuityPreference<Float>("DEFAULT_TAX",Float.valueOf(8.75f));
		DEFAULT_TIP = new GrouptuityPreference<Float>("DEFAULT_TIP",Float.valueOf(15.0f));
		TIP_THE_TAX = new GrouptuityPreference<Boolean>("TIP_THE_TAX",false);
		DISCOUNT_TAX_METHOD = new GrouptuityPreference<Integer>("DISCOUNT_TAX_METHOD",Discount.DiscountMethod.NONE.ordinal());
		DISCOUNT_TIP_METHOD = new GrouptuityPreference<Integer>("DISCOUNT_TIP_METHOD",Discount.DiscountMethod.ON_VALUE.ordinal());
		DEFAULT_PAYMENT_TYPE = new GrouptuityPreference<Integer>("DEFAULT_PAYMENT_TYPE",PaymentType.CASH.ordinal());
		DEFAULT_ROUNDING_RULE = new GrouptuityPreference<Integer>("DEFAULT_ROUNDING_RULE",RoundingRule.ROUND_TO_EVEN.ordinal());

		//QUICK CALCULATION
		QUICK_CALC_SUBTOTAL = new GrouptuityPreference<Float>("QUICK_CALC_SUBTOTAL",Float.valueOf(0.0f));
		QUICK_CALC_TAX = new GrouptuityPreference<Float>("QUICK_CALC_TAX",Float.valueOf(DEFAULT_TAX.getValue()));
		QUICK_CALC_TIP = new GrouptuityPreference<Float>("QUICK_CALC_TIP",Float.valueOf(DEFAULT_TIP.getValue()));
		QUICK_CALC_OVERRIDE = new GrouptuityPreference<Integer>("QUICK_CALC_OVERRIDE",Bill.Override.SUB.ordinal());

		//VENMO
		VENMO_ENABLED = new GrouptuityPreference<Boolean>("VENMO_ENABLED",true);
		VENMO_USER_NAME = new GrouptuityPreference<String>("VENMO_USER_NAME",null);
		VENMO_USER_NAME_LOOKUP_DATA = new GrouptuityPreference<String>("VENMO_USER_NAME_LOOKUP_DATA",null);
		VENMO_USER_NAME_LOOKUP_DATA_TYPE = new GrouptuityPreference<String>("VENMO_USER_NAME_LOOKUP_DATA_TYPE",null);
		VENMO_ALTERNATIVE = new GrouptuityPreference<Integer>("VENMO_ALTERNATIVE",PaymentType.CASH.ordinal()); 
		VENMO_BY_3RD_PARTY = new GrouptuityPreference<Boolean>("VENMO_BY_3RD_PARTY",false);

		//GENERAL
		HORIZONTAL_ENABLED = new GrouptuityPreference<Boolean>("HORIZONTAL_ENABLED",false);
		DECIMAL_SYMBOL = new GrouptuityPreference<String>("DECIMAL_SYMBOL",".");
		NUMBER_GROUPING_SYMBOL = new GrouptuityPreference<String>("NUMBER_GROUPING_SYMBOL",",");
		NUMBER_GROUPING_DIGITS = new GrouptuityPreference<Integer>("NUMBER_GROUPING_DIGITS",3);
		CURRENCY_PREFIX_SYMBOL = new GrouptuityPreference<String>("CURRENCY_PREFIX_SYMBOL","$");
		CURRENCY_POSTFIX_SYMBOL = new GrouptuityPreference<String>("CURRENCY_POSTFIX_SYMBOL","");
		CURRENCY_DECIMAL_PLACES = new GrouptuityPreference<Integer>("CURRENCY_DECIMAL_PLACES",2);

		//TUTORIALS
		SHOW_EULA = new GrouptuityPreference<Boolean>("SHOW_EULA", true);
		SHOW_TUTORIAL_DINERENTRY = new GrouptuityPreference<Boolean>("SHOW_TUTORIAL_DINERENTRY", true);
		SHOW_TUTORIAL_ITEMENTRY = new GrouptuityPreference<Boolean>("SHOW_TUTORIAL_ITEMENTRY", true);
		SHOW_TUTORIAL_BILLCALC = new GrouptuityPreference<Boolean>("SHOW_TUTORIAL_BILLCALC", true);
		SHOW_TUTORIAL_BILLREVIEW = new GrouptuityPreference<Boolean>("SHOW_TUTORIAL_BILLREVIEW", true);
		SHOW_TUTORIAL_INDIVBILL = new GrouptuityPreference<Boolean>("SHOW_TUTORIAL_INDIVBILL", true);
		SHOW_TUTORIAL_QUICKCALC = new GrouptuityPreference<Boolean>("SHOW_TUTORIAL_QUICKCALC", true);
	}

	public static void resetBill()
	{
		bill = new Bill();
		if(INCLUDE_SELF.getValue())
		{
			self = new Diner(Contact.createSelf(context));
			bill.addDiner(self);
		}
		else
		{
			self = null;
		}
	}
	public static Bill getBill(){return bill;}
	public static Bill getQuickCalcBill(){return quickCalcBill;}
	public static void saveBill(){database.saveBill(bill);}
	public static Diner getRestaurant(){return restaurant;}
	public static Diner getSelf(){return self;}

	final public static String formatNumber(NumberFormat format, double value)
	{
		java.text.NumberFormat numberFormat = java.text.NumberFormat.getNumberInstance();
		numberFormat.setMinimumFractionDigits(format.getNumDecimalPlaces());
		numberFormat.setMaximumFractionDigits(format.getNumDecimalPlaces());
		numberFormat.setGroupingUsed(true);
		String returnString = numberFormat.format(value);

		//TODO custom locales
		if(value<0 && format.negativeBeforePrefix)
			return "-"+format.getPrefix()+returnString.substring(1)+format.getPostfix();
		else
			return format.getPrefix()+returnString+format.getPostfix();
	}
	final public static double scoreChar(char search, char match)
	{
		//requires lowercase chars
		if(search==match)
			return 3;

		switch(search)
		{
		case 'a':	switch(match){case 'q':case 'w':case 's':case 'z':case 'x':return 1;}break;
		case 'b':	switch(match){case 'v':case 'g':case 'h':case 'n':return 1;}break;
		case 'c':	switch(match){case 'x':case 'd':case 'f':case 'v':return 1;}break;
		case 'd':	switch(match){case 's':case 'e':case 'r':case 'f':case 'c':case 'x':return 1;}break;
		case 'e':	switch(match){case 'w':case 's':case 'd':case 'r':return 1;}break;
		case 'f':	switch(match){case 'd':case 'r':case 't':case 'g':case 'v':case 'c':return 1;}break;
		case 'g':	switch(match){case 't':case 'y':case 'h':case 'b':case 'f':case 'v':return 1;}break;
		case 'h':	switch(match){case 'y':case 'u':case 'j':case 'n':case 'b':case 'g':return 1;}break;
		case 'i':	switch(match){case 'u':case 'j':case 'k':case 'o':return 1;}break;
		case 'j':	switch(match){case 'h':case 'u':case 'i':case 'k':case 'm':case 'n':return 1;}break;
		case 'k':	switch(match){case 'j':case 'i':case 'o':case 'l':case 'm':return 1;}break;
		case 'l':	switch(match){case 'p':case 'o':case 'k':return 1;}break;
		case 'm':	switch(match){case 'k':case 'j':case 'n':return 1;}break;
		case 'n':	switch(match){case 'b':case 'h':case 'j':case 'm':return 1;}break;
		case 'o':	switch(match){case 'i':case 'k':case 'l':case 'p':return 1;}break;
		case 'p':	switch(match){case 'o':case 'l':return 1;}break;
		case 'q':	switch(match){case 'w':case 'a':return 1;}break;
		case 'r':	switch(match){case 'e':case 'd':case 'f':case 't':return 1;}break;
		case 's':	switch(match){case 'a':case 'w':case 'e':case 'd':case 'x':case 'z':return 1;}break;
		case 't':	switch(match){case 'r':case 'f':case 'g':case 'y':return 1;}break;
		case 'u':	switch(match){case 'y':case 'h':case 'j':case 'i':return 1;}break;
		case 'v':	switch(match){case 'c':case 'f':case 'g':case 'b':return 1;}break;
		case 'w':	switch(match){case 'q':case 'a':case 's':case 'e':return 1;}break;
		case 'x':	switch(match){case 'z':case 's':case 'd':case 'c':return 1;}break;
		case 'y':	switch(match){case 't':case 'g':case 'h':case 'u':return 1;}break;
		case 'z':	switch(match){case 'a':case 's':case 'x':return 1;}break;
		}
		return 0;
	}
	final public static String validateEmailFormat(String str)
	{
		if(str.equals("MobileLife Contacts"))
			return null;
		return str;
	}

	final public static Intent generateSupportEmailIntent(Context context)
	{
		String emailString = "support@grouptuity.com";
		String emailTitle = "Grouptuity Support Email";

		String underline = "";
		for(int n=0;n<25;n++)
			underline+="\u00AF";
		String emailBody = "\n\n\n"+underline;

		PackageInfo pInfo = null;
		try{pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);}
		catch(NameNotFoundException e){Grouptuity.log(e);}
		if(pInfo!=null)
			emailBody += "\nApp Version: "+pInfo.versionName;

		emailBody += "\nOS Version: " + System.getProperty("os.version") + "(" + android.os.Build.VERSION.INCREMENTAL + ")";
		emailBody += "\nOS API Level: " + android.os.Build.VERSION.SDK_INT;
		emailBody += "\nDevice: " + android.os.Build.DEVICE;
		emailBody += "\nModel (and Product): " + android.os.Build.MODEL + " ("+ android.os.Build.PRODUCT + ")\n";

		return new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"+ Uri.encode(emailString) + "?subject=" + Uri.encode(emailTitle) + "&body=" + Uri.encode(emailBody)));
	}
	final public static void showAboutWindow(final Activity a)
	{
		AlertDialog.Builder aboutBuilder = new AlertDialog.Builder(a);
		aboutBuilder.setCancelable(true);
		PackageInfo pInfo = null;
		try{pInfo = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);}
		catch(NameNotFoundException e){Grouptuity.log(e);}
		String message = "";
		if(pInfo!=null)
			message += "Version: "+pInfo.versionName;
		message += "\nCopyright (c) 2013";
		message += "\n\nFor more information, visit our website:\ngrouptuity.com";
		aboutBuilder.setMessage(message);
		aboutBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int id){dialog.cancel();}});
		aboutBuilder.setNegativeButton("Support Email", new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id){a.startActivity(Grouptuity.generateSupportEmailIntent(a));}
		});
		AlertDialog about = aboutBuilder.create();
		about.setTitle("About Grouptuity");
		about.setIcon(R.drawable.logo_on_red);
		about.show();
	}

	final public static int toActualPixels(int dip){return (int)(dip*context.getResources().getDisplayMetrics().density);}

	final public static LayoutParams fillFillLP(){return new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT);}
	final public static LayoutParams fillWrapLP(){return new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);}
	final public static LayoutParams wrapFillLP(){return new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.MATCH_PARENT);}
	final public static LayoutParams wrapWrapLP(){return new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);}
	final public static LinearLayout.LayoutParams fillFillLP(float weight){return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.MATCH_PARENT,weight);}
	final public static LinearLayout.LayoutParams fillWrapLP(float weight){return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT,weight);}
	final public static LinearLayout.LayoutParams wrapFillLP(float weight){return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.MATCH_PARENT,weight);}
	final public static LinearLayout.LayoutParams wrapWrapLP(float weight){return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT,weight);}
	final public static LinearLayout.LayoutParams gravFillFillLP(int gravity){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.MATCH_PARENT);lp.gravity = gravity;return lp;}
	final public static LinearLayout.LayoutParams gravFillWrapLP(int gravity){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);lp.gravity = gravity;return lp;}
	final public static LinearLayout.LayoutParams gravWrapFillLP(int gravity){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.MATCH_PARENT);lp.gravity = gravity;return lp;}
	final public static LinearLayout.LayoutParams gravWrapWrapLP(int gravity){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);lp.gravity = gravity;return lp;}
	final public static LinearLayout.LayoutParams gravFillFillLP(float weight, int gravity){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.MATCH_PARENT,weight);lp.gravity = gravity;return lp;}
	final public static LinearLayout.LayoutParams gravFillWrapLP(float weight, int gravity){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT,weight);lp.gravity = gravity;return lp;}
	final public static LinearLayout.LayoutParams gravWrapFillLP(float weight, int gravity){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.MATCH_PARENT,weight);lp.gravity = gravity;return lp;}
	final public static LinearLayout.LayoutParams gravWrapWrapLP(float weight, int gravity){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT,weight);lp.gravity = gravity;return lp;}
	final public static RelativeLayout.LayoutParams relFillFillLP(){return new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,RelativeLayout.LayoutParams.MATCH_PARENT);}
	final public static RelativeLayout.LayoutParams relFillWrapLP(){return new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,RelativeLayout.LayoutParams.WRAP_CONTENT);}
	final public static RelativeLayout.LayoutParams relWrapFillLP(){return new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,RelativeLayout.LayoutParams.MATCH_PARENT);}
	final public static RelativeLayout.LayoutParams relWrapWrapLP(){return new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,RelativeLayout.LayoutParams.WRAP_CONTENT);}
	final public static RelativeLayout.LayoutParams relFillFillLP(int verb){RelativeLayout.LayoutParams lp=new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,RelativeLayout.LayoutParams.MATCH_PARENT);lp.addRule(verb);return lp;}
	final public static RelativeLayout.LayoutParams relFillWrapLP(int verb){RelativeLayout.LayoutParams lp=new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,RelativeLayout.LayoutParams.WRAP_CONTENT);lp.addRule(verb);return lp;}
	final public static RelativeLayout.LayoutParams relWrapFillLP(int verb){RelativeLayout.LayoutParams lp=new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,RelativeLayout.LayoutParams.MATCH_PARENT);lp.addRule(verb);return lp;}
	final public static RelativeLayout.LayoutParams relWrapWrapLP(int verb){RelativeLayout.LayoutParams lp=new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,RelativeLayout.LayoutParams.WRAP_CONTENT);lp.addRule(verb);return lp;}
	final public static RelativeLayout.LayoutParams relFillFillLP(int verb, int anchor){RelativeLayout.LayoutParams lp=new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,RelativeLayout.LayoutParams.MATCH_PARENT);lp.addRule(verb,anchor);return lp;}
	final public static RelativeLayout.LayoutParams relFillWrapLP(int verb, int anchor){RelativeLayout.LayoutParams lp=new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,RelativeLayout.LayoutParams.WRAP_CONTENT);lp.addRule(verb,anchor);return lp;}
	final public static RelativeLayout.LayoutParams relWrapFillLP(int verb, int anchor){RelativeLayout.LayoutParams lp=new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,RelativeLayout.LayoutParams.MATCH_PARENT);lp.addRule(verb,anchor);return lp;}
	final public static RelativeLayout.LayoutParams relWrapWrapLP(int verb, int anchor){RelativeLayout.LayoutParams lp=new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,RelativeLayout.LayoutParams.WRAP_CONTENT);lp.addRule(verb,anchor);return lp;}

	final public static void log(String str){Log.i(TAG,str);}
	final public static void log(Object object){Log.i(TAG,object.toString());}
	final public static void log(boolean b){Log.i(TAG,String.valueOf(b));}
	final public static void log(int i){Log.i(TAG,String.valueOf(i));}
	final public static void log(double d){Log.i(TAG,String.valueOf(d));}
	final public static void log(Exception e)
	{
		if(!DEBUG_MODE)
			return;
		String errorString = "Grouptuity Error!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n";
		errorString += e.getMessage()+"\n";
		for(StackTraceElement ste: e.getStackTrace())
		{
			String lineReference;
			if(ste.getFileName()==null)
				lineReference = "Unknown Source";
			else
				lineReference = ste.getFileName()+": "+ste.getLineNumber();
			errorString += "\tat "+ste.getClassName()+"."+ste.getMethodName()+"("+lineReference+")\n";
		}
		Log.i(TAG,errorString);
	}

	final public static void registerActivityListener(ActivityTemplate<?> at){if(!contactLoadListeners.contains(at))contactLoadListeners.add(at);}
	final public static void unregisterActivityListener(ActivityTemplate<?> at){contactLoadListeners.remove(at);}
	final public static void reloadContacts(){contacts.clear();contactsLoadTask = new ContactsLoadTask();contactsLoadTask.execute();}

	final public static class ContactsLoadTask extends AsyncTask<Void, Integer, ArrayList<Contact>>
	{
		public void reportProgress(Integer progress){publishProgress(progress);};

		protected void onPreExecute(){contactsLoaded = false;contactsLoadPercent = 0;for(ActivityTemplate<?> at: contactLoadListeners)at.onContactsLoadStart();}
		protected ArrayList<Contact> doInBackground(Void... params){return Database.getAllContacts(this);}
		protected void onProgressUpdate(Integer... progress){contactsLoadPercent = progress[0];for(ActivityTemplate<?> at: contactLoadListeners)at.onContactsLoadProgressUpdate();}
		protected void onPostExecute(ArrayList<Contact> result)
		{
			contactsLoadPercent = 100;
			contacts = result;
			contactsByLookupKey.clear();
			for(Contact c: contacts)
				if(c.lookupKey!=null)
					contactsByLookupKey.put(c.lookupKey,c);
			contactsLoaded = true;
			for(ActivityTemplate<?> at: contactLoadListeners)
				at.onContactsLoadComplete();
		}
	}
}