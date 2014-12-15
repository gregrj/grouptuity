package com.grouptuity.preferences;

import java.util.ArrayList;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.Handler;
import android.preference.*;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.widget.EditText;
import com.grouptuity.*;
import com.grouptuity.Grouptuity.*;
import com.grouptuity.model.*;
import com.grouptuity.model.Discount.DiscountMethod;
import com.grouptuity.replacements.AsyncTaskReplacement;
import com.grouptuity.venmo.VenmoUtility;
import com.grouptuity.view.AlertDialogList;
import com.grouptuity.view.NumberPadBasic;
import com.grouptuity.view.NumberPadBasic.NumberPadBasicController;
import com.grouptuity.view.AlertDialogList.Controller;

public class PreferencesActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener
{
	private Activity activity;
	private PreferenceScreen screen;
	private NumberPadBasic numberPad;
	private AlertDialogList emailSelector, phoneNumberSelector, inputMethodDialog;
	private AlertDialog venmoPhoneInputAlert, venmoEmailInputAlert;
	private State state = State.DEFAULT;
	private boolean venmoLookupInProgress;
	private static enum State{EDIT_DEFAULT_TAX,EDIT_DEFAULT_TIP,DEFAULT;}

	private Preference accountBalance, defaultTax, defaultTip, defaultPaymentType, venmoUserName, discountTaxMethod, discountTipMethod, promoCode;
	private EditTextPreference userName;
	private CheckBoxPreference creditUseNotification, includeSelf, searchAlgorithm, advancedItems, tipTheTax, venmoEnabled;

	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		activity = this;

		if(Grouptuity.terminateIfNotMainMenu)
			finish();

		screen = getPreferenceManager().createPreferenceScreen(this);
		setPreferenceScreen(screen);

		createAccountPreferences();
		createDinerEntryPreferences();
		createItemEntryPreferences();
		createCalculationsPreferences();
		createVenmoPreferences();
		createFinePrintPreferences();

		numberPad = new NumberPadBasic(this, new NumberPadBasicController()
		{
			public void onConfirm(double returnValue)
			{
				switch(state)
				{
					case EDIT_DEFAULT_TAX:	Grouptuity.preferences.edit().putFloat(Grouptuity.DEFAULT_TAX.key,(float)returnValue).commit();state=State.DEFAULT;break;
					case EDIT_DEFAULT_TIP:	Grouptuity.preferences.edit().putFloat(Grouptuity.DEFAULT_TIP.key,(float)returnValue).commit();state=State.DEFAULT;break;
					case DEFAULT:
				}
			}
			public void onCancel()
			{
				switch(state)
				{
					case EDIT_DEFAULT_TAX:
					case EDIT_DEFAULT_TIP:	state=State.DEFAULT;break;
					case DEFAULT:
				}
			}
		});
		addContentView(numberPad, Grouptuity.fillFillLP());
		numberPad.close();
	}

	private void createAccountPreferences()
	{
		PreferenceCategory category = new PreferenceCategory(this);
		category.setTitle("Account");
		screen.addPreference(category);

		//USER_NAME
		userName = new EditTextPreference(this);
		userName.setKey(Grouptuity.USER_NAME.key);
		userName.setTitle("User Name: "+Grouptuity.USER_NAME.getValue());	
		userName.setSummary("Enter your name to be displayed within the app.");
		userName.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
		{
			public boolean onPreferenceChange(Preference preference, Object newValue)
			{
				Grouptuity.preferences.edit().putBoolean(Grouptuity.USER_NAME_CHOSEN.key,true).commit();
				return true;
			}
		});
		category.addPreference(userName);
	}
	private void createDinerEntryPreferences()
	{
		PreferenceCategory category = new PreferenceCategory(this);
		category.setTitle("Diner Entry");
		screen.addPreference(category);

		//INCLUDE_SELF
		includeSelf = new CheckBoxPreference(this);
		includeSelf.setKey(Grouptuity.INCLUDE_SELF.key);
		includeSelf.setTitle("Auto-include Self");
		includeSelf.setSummary("Automatically include yourself in group bill split calculations.");
		category.addPreference(includeSelf);

		//SEARCH_ALGORITHM
		searchAlgorithm = new CheckBoxPreference(this);
		searchAlgorithm.setKey(Grouptuity.SEARCH_ALGORITHM.key);
		searchAlgorithm.setTitle("Contact Search Mode");
		searchAlgorithm.setSummary("Sort phone contacts by closest match taking typos into account.  Uncheck this option to keep contacts sorted alphabetically.");
		category.addPreference(searchAlgorithm);
	}
	private void createItemEntryPreferences()
	{
		PreferenceCategory category = new PreferenceCategory(this);
		category.setTitle("Item Entry");
		screen.addPreference(category);

		//ENABLE_ADVANCED_ITEMS
		advancedItems = new CheckBoxPreference(this);
		advancedItems.setKey(Grouptuity.ENABLE_ADVANCED_ITEMS.key);
		advancedItems.setTitle("Enable Item Types on Bills?");	
		advancedItems.setSummary("Allow display of item types (e.g. "+BillableType.ENTREE.name+" or "+BillableType.DRINK.name+").");
		category.addPreference(advancedItems);

		//DISCOUNT_TAX_METHOD
		discountTaxMethod = new Preference(this);
		discountTaxMethod.setKey(Grouptuity.DISCOUNT_TAX_METHOD.key);
		discountTaxMethod.setTitle("Apply Tax to Discounts?");
		switch(DiscountMethod.values()[Grouptuity.DISCOUNT_TAX_METHOD.getValue()])
		{
			case NONE:		discountTaxMethod.setSummary("By default, discounts will not be taxed.");break;
			case ON_COST:	discountTaxMethod.setSummary("By default, tax will be paid on the purchase cost of the discounts (only applicable to group deal vouchers, e.g. Groupons).");break;
			case ON_VALUE:	discountTaxMethod.setSummary("Tax will be paid on the full value of the food and drinks ordered regardless of discounts.");break;
			default:		break;	
		}
		discountTaxMethod.setOnPreferenceClickListener(new OnPreferenceClickListener()
		{
			public boolean onPreferenceClick(Preference preference)
			{
				AlertDialogList list = new AlertDialogList(activity, new String[]{"Do not Tax","Tax Purchase Cost","Tax Discount Value"}, null, new AlertDialogList.Controller()
				{
					public void optionSelected(int i){Grouptuity.preferences.edit().putInt(Grouptuity.DISCOUNT_TAX_METHOD.key,i).commit();}
				});
				list.setTitle("Choose Discount Tax Method:");
				list.show();
				return true;
			}
		});
		category.addPreference(discountTaxMethod);

		//DISCOUNT_TIP_METHOD
		discountTipMethod = new Preference(this);
		discountTipMethod.setKey(Grouptuity.DISCOUNT_TIP_METHOD.key);
		discountTipMethod.setTitle("Apply Tip to Discounts?");
		switch(DiscountMethod.values()[Grouptuity.DISCOUNT_TIP_METHOD.getValue()])
		{
			case NONE:		discountTipMethod.setSummary("By default, discounts will not be tiped.");break;
			case ON_COST:	discountTipMethod.setSummary("By default, tip will be paid on the purchase cost of the discounts (only applicable to group deal vouchers, e.g. Groupons).");break;
			case ON_VALUE:	discountTipMethod.setSummary("Tip will be paid on the full value of the food and drinks ordered regardless of discounts.");break;
			default:		break;	
		}
		discountTipMethod.setOnPreferenceClickListener(new OnPreferenceClickListener()
		{
			public boolean onPreferenceClick(Preference preference)
			{
				AlertDialogList list = new AlertDialogList(activity, new String[]{"Do not Tip","Tip Purchase Cost","Tip Discount Value"}, null, new AlertDialogList.Controller()
				{
					public void optionSelected(int i){Grouptuity.preferences.edit().putInt(Grouptuity.DISCOUNT_TIP_METHOD.key,i).commit();}
				});
				list.setTitle("Choose Discount Tip Method:");
				list.show();
				return true;
			}
		});
		category.addPreference(discountTipMethod);
	}
	private void createCalculationsPreferences()
	{
		PreferenceCategory category = new PreferenceCategory(this);
		category.setTitle("Calculations");
		screen.addPreference(category);

		//DEFAULT_TAX
		defaultTax = new Preference(this);
		defaultTax.setKey(Grouptuity.DEFAULT_TAX.key);
		defaultTax.setTitle("Default Tax Percentage");
		defaultTax.setSummary(Grouptuity.formatNumber(Grouptuity.NumberFormat.TAX_PERCENT,(double)Grouptuity.DEFAULT_TAX.getValue()));
		defaultTax.setOnPreferenceClickListener(new OnPreferenceClickListener()
		{
			public boolean onPreferenceClick(Preference preference)
			{
				numberPad.open(Grouptuity.NumberFormat.TAX_PERCENT);
				state=State.EDIT_DEFAULT_TAX;
				return true;
			}
		});
		category.addPreference(defaultTax);
		
		//DEFAULT_TIP
		defaultTip = new Preference(this);
		defaultTip.setKey(Grouptuity.DEFAULT_TIP.key);
		defaultTip.setTitle("Default Tip Percentage");
		defaultTip.setSummary(Grouptuity.formatNumber(Grouptuity.NumberFormat.TIP_PERCENT,(double)Grouptuity.DEFAULT_TIP.getValue()));
		defaultTip.setOnPreferenceClickListener(new OnPreferenceClickListener()
		{
			public boolean onPreferenceClick(Preference preference)
			{
				numberPad.open(Grouptuity.NumberFormat.TIP_PERCENT);
				state=State.EDIT_DEFAULT_TIP;
				return true;
			}
		});
		category.addPreference(defaultTip);

		//TIP_THE_TAX
		tipTheTax = new CheckBoxPreference(this);
		tipTheTax.setKey(Grouptuity.TIP_THE_TAX.key);
		tipTheTax.setTitle("Tip the tax?");
		tipTheTax.setSummary("Calculate tip based on the subtotal with tax included.");
		category.addPreference(tipTheTax);

//		//DEFAULT_PAYMENT_TYPE
//		defaultPaymentType = new Preference(this);
//		defaultPaymentType.setKey(Grouptuity.DEFAULT_PAYMENT_TYPE.key);
//		defaultPaymentType.setTitle("Default Payment Method");
//		defaultPaymentType.setSummary("Default payment method assignment for diners");
//		defaultPaymentType.setOnPreferenceClickListener(new OnPreferenceClickListener()
//		{
//			public boolean onPreferenceClick(Preference preference)
//			{
//				return true;
//			}
//		});
//		category.addPreference(defaultPaymentType);
	}
	private void createVenmoPreferences()
	{
		PreferenceCategory category = new PreferenceCategory(this);
		category.setTitle("Venmo");
		screen.addPreference(category);

		//VENMO_ENABLED
		venmoEnabled = new CheckBoxPreference(this);
		venmoEnabled.setKey(Grouptuity.VENMO_ENABLED.key);
		venmoEnabled.setTitle("Enable Venmo?");
		venmoEnabled.setSummary("Use Venmo as a payment option. If enabled, data may be sent to Venmo for username lookups and to process your transactions.");
		category.addPreference(venmoEnabled);

		//VENMO_USER_NAME
		venmoUserName = new Preference(this);
		venmoUserName.setEnabled(Grouptuity.VENMO_ENABLED.getValue());
		venmoUserName.setKey(Grouptuity.VENMO_USER_NAME.key);
		venmoUserName.setTitle("Venmo Username Lookup");
		setVenmoUserNameSummary();
		venmoUserName.setOnPreferenceClickListener(new OnPreferenceClickListener()
		{
			public boolean onPreferenceClick(Preference preference)
			{
				inputMethodDialog = new AlertDialogList(activity,new String[]{"Email Address","Phone Number","Raw Venmo ID"}, null, new Controller()
				{
					public void optionSelected(int i)
					{
						switch(i)
						{
							case 0:		showVenmoEmailChoiceAlert();break;
							case 1:		showVenmoPhoneChoiceAlert();break;
							default:	final EditText rawIDEditText = new EditText(activity);
										new AlertDialog.Builder(activity)
										.setCancelable(true)
										.setTitle("Enter Venmo ID")
										.setMessage("Enter the official username assigned to you by Venmo (not email address):")
										.setIcon(R.drawable.payment_venmo)
										.setView(rawIDEditText)
										.setPositiveButton("OK", new DialogInterface.OnClickListener()
										{
											@Override
											public void onClick(DialogInterface dialog, int id)
											{
												//	TODO need to prevent bad inputs
												Grouptuity.preferences.edit().putString(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA_TYPE.key,VenmoUtility.LookupDataType.RAW_ID.toString()).commit();
												Grouptuity.preferences.edit().putString(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA.key,rawIDEditText.getText().toString()).commit();
											}
										}).setNegativeButton("Cancel", new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int id){dialog.cancel();}})
										.show();
								;break;
						}
					}
				});
				inputMethodDialog.setTitle("Venmo Username Input");
				inputMethodDialog.show();
				return true;
			}
		});
		category.addPreference(venmoUserName);

	}
	private void createFinePrintPreferences()
	{
		PreferenceCategory category = new PreferenceCategory(this);
		category.setTitle(" ");
		screen.addPreference(category);

		Preference reset = new Preference(this);
		reset.setTitle("Reset to Default Settings");
		reset.setOnPreferenceClickListener(new OnPreferenceClickListener()
		{
			public boolean onPreferenceClick(Preference preference)
			{
				AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(activity);
				confirmBuilder.setCancelable(true);
				confirmBuilder.setMessage("Do you want to reset ALL settings to their default values?");
				confirmBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int id){dialog.cancel();}});
				confirmBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int id)
					{
						boolean acceptedEULA = Grouptuity.SHOW_EULA.getValue();
						Grouptuity.preferences.edit().clear().commit();
						Grouptuity.preferences.edit().putBoolean(Grouptuity.SHOW_EULA.key,acceptedEULA).commit();
						for(GrouptuityPreference<?> gp: Grouptuity.preferencesHashMap.values())
							gp.refresh();
						dialog.cancel();
					}
				});
				AlertDialog confirm = confirmBuilder.create();
				confirm.setTitle("Reset to Default Settings");
				confirm.show();
				return true;
			}
		});
		category.addPreference(reset);

		Preference about = new Preference(this);
		about.setTitle("About Grouptuity");
		about.setOnPreferenceClickListener(new OnPreferenceClickListener(){public boolean onPreferenceClick(Preference preference){Grouptuity.showAboutWindow(activity);return true;}});
		category.addPreference(about);

		Preference privacyPolicy = new Preference(this);
		privacyPolicy.setTitle("Privacy Policy");
		privacyPolicy.setOnPreferenceClickListener(new OnPreferenceClickListener(){public boolean onPreferenceClick(Preference preference){EULA.showPrivacyPolicy(activity);return true;}});
		category.addPreference(privacyPolicy);

		Preference termsOfService = new Preference(this);
		termsOfService.setTitle("Terms of Service");
		termsOfService.setOnPreferenceClickListener(new OnPreferenceClickListener(){public boolean onPreferenceClick(Preference preference){EULA.showTermsOfService(activity);return true;}});
		category.addPreference(termsOfService);
	}

	@Override
	protected void onDestroy()
	{
		if(inputMethodDialog!=null)
			inputMethodDialog.dismiss();
		if(emailSelector!=null)
			emailSelector.dismiss();
		if(venmoEmailInputAlert!=null)
			venmoEmailInputAlert.dismiss();
		if(phoneNumberSelector!=null)
			phoneNumberSelector.dismiss();
		if(venmoPhoneInputAlert!=null)
			venmoPhoneInputAlert.dismiss();
		super.onDestroy();
	}
	protected void onPause(){super.onPause();screen.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);}
	protected void onResume(){super.onResume();screen.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);}
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		GrouptuityPreference<?> preference = Grouptuity.preferencesHashMap.get(key);
		if(preference!=null)
			preference.refresh();

		//ACCOUNT
		if(key.equals(Grouptuity.USER_NAME.key))
			userName.setTitle("User Name: "+Grouptuity.USER_NAME.getValue());
		
		//DINER ENTRY
		else if(key.equals(Grouptuity.INCLUDE_SELF.key))
			includeSelf.setChecked(Grouptuity.INCLUDE_SELF.getValue());
		else if(key.equals(Grouptuity.SEARCH_ALGORITHM.key))
			searchAlgorithm.setChecked(Grouptuity.SEARCH_ALGORITHM.getValue());

		//ITEM ENTRY
		else if(key.equals(Grouptuity.ENABLE_ADVANCED_ITEMS.key))
			advancedItems.setChecked(Grouptuity.ENABLE_ADVANCED_ITEMS.getValue());
		else if(key.equals(Grouptuity.DISCOUNT_TAX_METHOD.key))
			switch(DiscountMethod.values()[Grouptuity.DISCOUNT_TAX_METHOD.getValue()])
			{
				case NONE:		discountTaxMethod.setSummary("By default, discounts will not be taxed.");break;
				case ON_COST:	discountTaxMethod.setSummary("By default, tax will be paid on the purchase cost of the discounts (only applicable to group deal vouchers, e.g. Groupons).");break;
				case ON_VALUE:	discountTaxMethod.setSummary("Tax will be paid on the full value of the food and drinks ordered regardless of discounts.");break;
				default:		break;	
			}
		else if(key.equals(Grouptuity.DISCOUNT_TIP_METHOD.key))
			switch(DiscountMethod.values()[Grouptuity.DISCOUNT_TIP_METHOD.getValue()])
			{
				case NONE:		discountTipMethod.setSummary("By default, discounts will not be tiped.");break;
				case ON_COST:	discountTipMethod.setSummary("By default, tip will be paid on the purchase cost of the discounts (only applicable to group deal vouchers, e.g. Groupons).");break;
				case ON_VALUE:	discountTipMethod.setSummary("Tip will be paid on the full value of the food and drinks ordered regardless of discounts.");break;
				default:		break;	
			}

		//CALCULATIONS
		else if(key.equals(Grouptuity.DEFAULT_TAX.key))
			defaultTax.setSummary(Grouptuity.formatNumber(Grouptuity.NumberFormat.TAX_PERCENT,(double)Grouptuity.DEFAULT_TAX.getValue()));
		else if(key.equals(Grouptuity.DEFAULT_TIP.key))
			defaultTip.setSummary(Grouptuity.formatNumber(Grouptuity.NumberFormat.TIP_PERCENT,(double)Grouptuity.DEFAULT_TIP.getValue()));
		else if(key.equals(Grouptuity.TIP_THE_TAX.key))
			tipTheTax.setChecked(Grouptuity.TIP_THE_TAX.getValue());

		//VENMO
		else if(key.equals(Grouptuity.VENMO_ENABLED.key))
		{
			if(Grouptuity.VENMO_ENABLED.getValue())
			{
				venmoEnabled.setChecked(true);
				venmoUserName.setEnabled(true);
				if(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA.getValue()!=null)
					new SelfVenmoLookupTask().execute();
				Bill bill = Grouptuity.getBill();
				if(bill!=null)
				{
					for(Diner d: bill.diners)
						d.contact.refreshVenmoUsername();
				}
			}
			else
			{
				venmoEnabled.setChecked(false);
				venmoUserName.setEnabled(false);
			}
		}
		else if(key.equals(Grouptuity.VENMO_USER_NAME.key))
		{
			setVenmoUserNameSummary();
			Bill bill = Grouptuity.getBill();
			if(bill!=null)
			{
				for(Diner d: bill.diners)
				{
					if(d.contact.isSelf)
						d.contact.venmoUsername = Grouptuity.VENMO_USER_NAME.getValue();
				}
			}
		}
		else if(key.equals(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA.key))
		{
			if(Grouptuity.VENMO_ENABLED.getValue())
				new SelfVenmoLookupTask().execute();
		}
	}

	@Override
	public void onBackPressed()
	{
		switch(state)
		{
			case EDIT_DEFAULT_TAX:	
			case EDIT_DEFAULT_TIP:	numberPad.close();state=State.DEFAULT;break;
			default:				super.onBackPressed();break;
		}
	}

	private void setVenmoUserNameSummary()
	{
		if(venmoLookupInProgress)
			venmoUserName.setSummary("[Venmo username lookup in progress]");
		else if(Grouptuity.VENMO_USER_NAME.getValue()==null)
		{
			if(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA.getValue()==null)
				venmoUserName.setSummary("[Please enter a username]");
			else if(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA_TYPE.getValue()!=null)
			{
				if(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA_TYPE.getValue().equals(VenmoUtility.LookupDataType.EMAIL.toString()))
					venmoUserName.setSummary("[Email address not recognized by Venmo]");
				else if(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA_TYPE.getValue().equals(VenmoUtility.LookupDataType.PHONE.toString()))
					venmoUserName.setSummary("[Phone number not recognized by Venmo]");
				else
					venmoUserName.setSummary("[Username not recognized by Venmo]");
			}
			else
				venmoUserName.setSummary("[Please enter a username]");
		}
		else
		{
			if(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA.getValue()==null)
				venmoUserName.setSummary(Grouptuity.VENMO_USER_NAME.getValue());
			else
			{
				if(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA_TYPE.getValue()==null)
					venmoUserName.setSummary(Grouptuity.VENMO_USER_NAME.getValue());
				else
				{
					if(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA_TYPE.getValue().equals(VenmoUtility.LookupDataType.RAW_ID.toString()))
						venmoUserName.setSummary(Grouptuity.VENMO_USER_NAME.getValue());
					else
						venmoUserName.setSummary(Grouptuity.VENMO_USER_NAME.getValue()+" ("+Grouptuity.VENMO_USER_NAME_LOOKUP_DATA.getValue()+")");
				}
			}
		}
	}
	private void showVenmoEmailChoiceAlert()
	{
		ArrayList<String> emails = new ArrayList<String>();
		for(Account account: AccountManager.get(this).getAccounts())
		{
			String str = Grouptuity.validateEmailFormat(account.name);
			if(str!=null && !emails.contains(str))
				emails.add(str);
		}

		int count = emails.size() + 1;
		if(count==1){showVenmoEmailInputAlert();return;}

		final String[] contactOptions = new String[count];
		contactOptions[0] = " (enter other email)";
		for(int i=1; i<contactOptions.length; i++)
			contactOptions[i] = emails.get(i-1);
		emailSelector= new AlertDialogList(this,contactOptions,null, new Controller()
		{
			public void optionSelected(int i)
			{
				if(i==0)
					showVenmoEmailInputAlert();
				else
				{
					Grouptuity.preferences.edit().putString(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA_TYPE.key,VenmoUtility.LookupDataType.EMAIL.toString()).commit();
					Grouptuity.preferences.edit().putString(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA.key,contactOptions[i]).commit();
				}
			}
		});
		emailSelector.setTitle("Choose the email address you use on Venmo:");
		emailSelector.show();
	}
	private void showVenmoEmailInputAlert()
	{
		final EditText input = new EditText(this);
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
		.setCancelable(true)
		.setTitle("Enter Venmo Email")
		.setMessage("Enter the email address you use on Venmo:")
		.setIcon(R.drawable.payment_venmo)
		.setView(input)
		.setPositiveButton("OK", new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int id)
			{
				//TODO need to prevent bad inputs
				Grouptuity.preferences.edit().putString(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA_TYPE.key,VenmoUtility.LookupDataType.EMAIL.toString()).commit();
				Grouptuity.preferences.edit().putString(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA.key,input.getText().toString()).commit();
			}
		}).setNegativeButton("Cancel", new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int id){dialog.cancel();}});
		venmoEmailInputAlert = builder.show();
	}
	private void showVenmoPhoneChoiceAlert()
	{
		String phoneNumber = ((TelephonyManager)(activity.getSystemService(Context.TELEPHONY_SERVICE))).getLine1Number();
		if(phoneNumber==null || phoneNumber.trim().equals(""))
		{
			showVenmoPhoneInputAlert();
			return;
		}

		final String[] contactOptions = new String[]{" (enter other phone number)",phoneNumber};
		phoneNumberSelector = new AlertDialogList(this,contactOptions,null, new Controller()
		{
			public void optionSelected(int i)
			{
				if(i==0)
					showVenmoPhoneInputAlert();
				else
				{
					Grouptuity.preferences.edit().putString(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA_TYPE.key,VenmoUtility.LookupDataType.PHONE.toString()).commit();
					Grouptuity.preferences.edit().putString(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA.key,contactOptions[i]).commit();
				}
			}
		});
		phoneNumberSelector.setTitle("Choose the phone number you use on Venmo:");
		phoneNumberSelector.show();
	}
	private void showVenmoPhoneInputAlert()
	{
		final EditText input = new EditText(this);
		input.setInputType(InputType.TYPE_CLASS_PHONE);
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
		.setCancelable(true)
		.setTitle("Enter Venmo Phone Number")
		.setMessage("Enter the phone number you use on Venmo:")
		.setIcon(R.drawable.payment_venmo)
		.setView(input)
		.setPositiveButton("OK", new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int id)
			{
				//TODO need to prevent bad inputs
				Grouptuity.preferences.edit().putString(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA_TYPE.key,VenmoUtility.LookupDataType.PHONE.toString()).commit();
				Grouptuity.preferences.edit().putString(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA.key,input.getText().toString()).commit();
			}
		}).setNegativeButton("Cancel", new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int id){dialog.cancel();}});
		venmoPhoneInputAlert = builder.show();
	}

	final private class SelfVenmoLookupTask extends AsyncTaskReplacement<Void, Integer, String>
	{
		protected void onPreExecute(){venmoLookupInProgress = true;setVenmoUserNameSummary();}
		protected String doInBackground(Void... params){return VenmoUtility.getVenmoUsername(Grouptuity.VENMO_USER_NAME_LOOKUP_DATA.getValue(),Grouptuity.VENMO_USER_NAME_LOOKUP_DATA_TYPE.getValue());}
		protected void onProgressUpdate(Integer... progress){}
		protected void onPostExecute(String result)
		{
			if(result!=null && !result.trim().equals(""))
				Grouptuity.preferences.edit().putString(Grouptuity.VENMO_USER_NAME.key,result).commit();
			else
				Grouptuity.preferences.edit().putString(Grouptuity.VENMO_USER_NAME.key,null).commit();
			venmoLookupInProgress = false;
			setVenmoUserNameSummary();
		}
	}

}