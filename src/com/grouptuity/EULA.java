package com.grouptuity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class EULA
{
	private static String getIntro()
	{
		return	"Welcome to Grouptuity, the app for splitting restaurant bills with groups! Each diner on a bill uses one Grouptuity Credit. "+
				"\n\nBy using this application, you agree to the privacy policy and terms of service linked in the buttons below\n";
	}

	private static String getPrivacyPolicy()
	{
		return	"Grouptuity is a simple app, so we think our policy should be too. We don’t collect personal data.\n\n"+
				"Do you send or collect any information?\n\n"+
				"We never collect and store people's names, contact info, or locations. Features that send email messages, like receipts or payment requests, are fully handled through your phone, so we never see what's being sent (unless of course you directly email us at support@grouptuity.com).\n\n"+
				"If you use the Venmo integration feature, the app may send contact information for diners on a bill to Venmo's server to do Venmo username lookups and processing of Venmo payment requests. This is the only time contact data gets sent over the internet during the use of Grouptuity, and you can disable using Venmo in the app settings if you prefer. Outside of that, we never give out personal information about you or your contacts to third parties.\n";
	}

	private static String getTermsOfSerivce()
	{
		return "Applicable from: 8 December, 2012.\n"+
				"\nBY USING THIS MOBILE APPLICATION, YOU ACCEPT AND AGREE TO THESE TERMS AND CONDITIONS.\n"+
				"\nThe following terms of service and end user license agreement (“EULA”) constitute an agreement between you and the owners of the Grouptuity mobile application (“Grouptuity”). This EULA governs your use of the Application (as specified below).\n"+
				"For purposes of this EULA the \"Application\" means all software programs made available by Grouptuity including, but not limited to the Grouptuity mobile application. The “Application” also includes updates and upgrades as well as accompanying manual(s), packaging and other written, files, electronic or on-line materials or documentation, and any and all copies of such software and its materials.\n"+
				"The application is licensed, not sold. Your use of the Application (as specified below) is subject to the terms and conditions set forth in this EULA. By installing, using or accessing the Application or any materials included in or with the Application, you hereby accept the terms of this EULA. If you do not accept the terms of this EULA, do not install, use or access the Application.\n"+
				"\n1. LICENSES\n"+
				"SOFTWARE LICENSE: Subject to this EULA and its terms and conditions, Grouptuity hereby grants you a non-exclusive, non-transferable, non-sublicensable, limited right and license to use the Application on your mobile devices, unless otherwise specified in the Application documentation. The rights granted herein are subject to your compliance with this EULA. The Application is being licensed to you and you hereby acknowledge that no title or ownership in the Application is being transferred or assigned and this EULA is not to be construed as a sale of any rights in the Application.\n"+
				"OWNERSHIP; NO OTHER LICENSES: Grouptuity retains all right, title and interest in and to the Application, including, but not limited to, all copyrights, trademarks, trade secrets, trade names, proprietary rights, patents, titles, computer codes, themes, settings, and artwork whether registered or not and all applications thereof. The Application is protected by applicable laws and treaties throughout the world. Unless expressly authorized by mandatory legislation, the Application may not be copied, reproduced or distributed in any manner or medium, in whole or in part, without prior written consent from Grouptuity. All rights not expressly granted to you herein are reserved by Grouptuity.\n"+
				"\n2. CREDITS\n"+
				"The Grouptuity Credits virtual currency (“Credits”) is a payment method for use of features in the Application. The following terms apply to Credits usage:\n"+
				"When you purchase or receive Credits, you do not own those Credits. Notwithstanding the terminology used, Credits represent a limited license right governed solely under this EULA. You have a limited right to use the Credits in connection with your use of the features in Grouptuity. Except as otherwise stated, purchases of Credits are non-refundable to the full extent permitted by law.\n"+
				"Your balance of Credits is stored locally on your device. Uninstallation of the Application or deletion of data related to the Application will void your credit balance. You acknowledge and agree that credits have no cash value and that neither Grouptuity nor any other person or entity has any obligation to exchange your credits for anything of value, including without limitation, real currency, and that, if your copy of the Application or its data is uninstalled, deleted, removed, reset, or otherwise modified or if your right to access the Application is terminated, the credits shall have no value, and Grouptuity shall have no responsibility to refund, replace, or restore your Credits.\n"+
				"Grouptuity has the absolute right to manage, modify, control, regulate and/or eliminate Credits as it sees fit in its sole discretion, and Grouptuity shall have no liability to you or anyone else for the exercise of such rights. For example, Credits will be lost, deleted, or forfeited when/if your Application data is uninstalled, deleted, or removed for any reason.\n"+
				"Grouptuity reserves the right, in its sole discretion, to make all calculations regarding your balance of Credits. Grouptuity further reserves the right, in its sole discretion, to determine the number of Credits that are credited and debited to you in connection with your use of the Application. While Grouptuity strives to make all such calculations on a consistent and reasonable basis, you hereby acknowledge and agree that Grouptuity’s determination of your balance of Credits is final, unless you can provide documentation to Grouptuity that such calculation was or is intentionally incorrect.\n"+
				"Grouptuity may change the purchase price for Credits at any time, as well as the ways that you can use or transfer Credits. Grouptuity also reserves the right to stop issuing Credits. Credits are not redeemable for any sum of money or monetary value from us unless we agree otherwise in writing or unless required by law. Grouptuity may issue a small number of free or promotional Credits to users. Grouptuity may expire these at any time.\n"+
				"\n3. INFORMATION COLLECTION AND USE; PRIVACY POLICY\n"+
				"By installing, accessing or using the Application, you consent to these information collection and usage terms. Grouptuity respects your privacy rights and recognizes the importance of protecting any information collected about you. Grouptuity's privacy policy as amended from time to time is available at www.grouptuity.com/legal (\"Privacy Policy\") and applicable to this EULA. Grouptuity’s Privacy Policy defines how, why and to which extent Grouptuity collects and uses personal and non-personal information in relation to Grouptuity's products and services. By installing, accessing or using the Application you explicitly agree with the terms and conditions of Grouptuity’s Privacy Policy and to any terms and conditions included therein by reference.\n"+
				"\n4. WARRANTY\n"+
				"NO WARRANTY: Grouptuity will not be liable for losses or damages arising from or in any way related to your access or use of the Application. To the fullest extent permissible under applicable law, the application is provided to you “as is,” with all faults, without warranty of any kind, without performance assurances or guarantees of any kind, and your use is at your sole risk. The entire risk of satisfactory quality and performance resides with you. Grouptuity does not make, and hereby disclaim, any and all express, implied or statutory warranties, including implied warranties of condition, uninterrupted use, accuracy of data, merchantability, satisfactory quality, fitness for a particular purpose, non-infringement of third party rights, and warranties (if any) arising from a course of dealing, usage, or trade practice. Grouptuity does not warrant against interference with your use of the application; that the application will meet your requirements; that operation of the application will be uninterrupted or error-free, or that the application will interoperate or be compatible with any other application or that any errors in the application will be corrected. No oral or written advice provided by Grouptuity or any authorized representative shall create a warranty. Some jurisdictions do not allow the exclusion of or limitations on implied warranties or the limitations on the applicable statutory rights of a consumer, so some or all of the above exclusions and limitations apply only to the fullest extent permitted by law in the applicable jurisdiction.\n"+
				"\n5. LIMITATION OF LIABILITY\n"+
				"In no event will Grouptuity be liable for special, incidental or consequential damages resulting from possession, access, use or malfunction of the application, including but not limited to, damages to property, loss of goodwill, computer failure or malfunction and, to the extent permitted by law, damages for personal injuries, property damage, lost profits or punitive damages from any causes of action arising out of or related to this EULA or the application, whether arising in tort (including negligence), contract, strict liability or otherwise and whether or not Grouptuity has been advised of the possibility of such damages. Because some states/countries do not allow certain limitations of liability, this limitation of liability shall apply to the fullest extent permitted by law in the applicable jurisdiction. This limitation of liability shall not be applicable solely to the extent that any specific provision of this limitation of liability is prohibited by any federal, state, or municipal law, which cannot be pre-empted. This EULA gives you specific legal rights, and you may have other rights that vary from jurisdiction to jurisdiction.\n"+
				"In no event shall Grouptuity’s liability for all damages (except as required by applicable law) exceed the actual price paid by you for use of the application or one US dollar ($1 USD), whichever is less.\n"+
				"INDEMNITY: You agree to indemnify, defend and hold Grouptuity, its partners, affiliates, contractors, officers, directors, employees and agents harmless from and against any and all damages, losses and expenses arising directly or indirectly from: (i) your acts and omissions to act in using the Application pursuant to the terms of the EULA; or (ii) your breach of this EULA.\n"+
				"MISCELLANEOUS: This EULA represents the complete agreement concerning this license between the parties and supersedes all prior agreements and representations between them. It may be amended only by a written document executed by both parties. If any provision of this EULA is held to be unenforceable for any reason, such provision shall be reformed only to the extent necessary to make it enforceable and the remaining provisions of this EULA shall not be affected.\n"+
				"GOVERNING LAW AND DISPUTE RESOLUTION: This Agreement shall be governed by the laws of the State of California.  Exclusive jurisdiction and venue for all matters relating to this Agreement shall be in courts and fora located in the State of California, and you consent to such jurisdiction and venue.\n"+
				"If you have any questions concerning this agreement, contact support@grouptuity.com.";
	}

	public static void showEULADialog(final Activity context)
	{
		LinearLayout layout = new LinearLayout(context);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setGravity(Gravity.CENTER_HORIZONTAL);

		ScrollView scrollView = new ScrollView(context);
		TextView textView = new TextView(context);
		textView.setTextSize(TypedValue.COMPLEX_UNIT_PX,Grouptuity.toActualPixels(18));
		int padding = Grouptuity.toActualPixels(10);
		textView.setPadding(padding, padding, padding, padding);
		textView.setText(EULA.getIntro());
		scrollView.addView(textView);
		layout.addView(scrollView,Grouptuity.wrapWrapLP(1.0f));

		LinearLayout spacer1 = new LinearLayout(context);
		LinearLayout spacer2 = new LinearLayout(context);
		LinearLayout spacer3 = new LinearLayout(context);

		Button privacyPolicy = new Button(context);
		privacyPolicy.setText("Privacy Policy");
		privacyPolicy.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v){showPrivacyPolicy(context);}
		});

		Button termsOfService = new Button(context);
		termsOfService.setText("Terms of Service");
		termsOfService.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v){showTermsOfService(context);}
		});

		LinearLayout buttonLayout = new LinearLayout(context);
		layout.addView(buttonLayout);
		buttonLayout.addView(spacer1,Grouptuity.wrapWrapLP(1.0f));
		buttonLayout.addView(privacyPolicy);
		buttonLayout.addView(spacer2,Grouptuity.wrapWrapLP(1.0f));
		buttonLayout.addView(termsOfService);
		buttonLayout.addView(spacer3,Grouptuity.wrapWrapLP(1.0f));

		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setCancelable(true);
		builder.setView(layout);
		builder.setIcon(R.drawable.logo_on_red);
		builder.setTitle("License Acceptance");
		builder.setPositiveButton("Accept", new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int id){Grouptuity.SHOW_EULA.setValue(false);}});
		builder.setNegativeButton("Decline", new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int id){context.finish();}});
		builder.show();
	}

	public static void showPrivacyPolicy(final Activity context)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setCancelable(true);
		builder.setTitle("Privacy Policy");
		builder.setMessage(getPrivacyPolicy());
		builder.setPositiveButton("Return", new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int id){}});
		builder.show();
	}

	public static void showTermsOfService(final Activity context)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setCancelable(true);
		builder.setTitle("Terms of Service");
		builder.setMessage(getTermsOfSerivce());
		builder.setPositiveButton("Return", new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int id){}});
		builder.show();
	}
}