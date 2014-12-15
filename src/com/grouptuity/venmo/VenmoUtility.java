/*	The MIT License (MIT)
 *	
 *	Copyright (c) 2014 Venmo Inc.
 *	
 *	Permission is hereby granted, free of charge, to any person obtaining a copy of
 *	this software and associated documentation files (the "Software"), to deal in
 *	the Software without restriction, including without limitation the rights to
 *	use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *	the Software, and to permit persons to whom the Software is furnished to do so,
 *	subject to the following conditions:
 *	The above copyright notice and this permission notice shall be included in all
 *	copies or substantial portions of the Software.
 *	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *	IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *	FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *	COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *	IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *	CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *	MODIFIED FOR GROUPTUITY
 */

package com.grouptuity.venmo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.grouptuity.Grouptuity;
import com.grouptuity.database.Database;
import com.grouptuity.model.Contact;

public abstract class VenmoUtility
{
	//Random string to indicate a failed lookup
	final public static String retryCode = "!$#%^EGRsertgh34y$regtih\u3234nulor42R%#g89$3982@#";

	public static enum LookupDataType{EMAIL,PHONE,RAW_ID;}

	private VenmoUtility(){}
	private static boolean isNullOrEmpty(String s) {return s==null || (s!=null && s.length()==0);}
	private static boolean isNullOrEmpty(String[] s) {return s==null || (s!=null && s.length==0);}
	private static String getCommaSeperatedList(String[] s, boolean isPhoneNumber)
	{
		String list = "";
		if(s!=null)
		{
			for (int i = 0; i < s.length; i++)
				//if it's a phone number, drop all non-digit characters
				list += (i>0 ? "," : "") + (isPhoneNumber ? s[i].replaceAll("[^\\d]", "") : s[i]);
		}
		return list;
	}
	public static String getVenmoUsername(final Contact c)
	{
		if(isNullOrEmpty(c.phoneNumbers))
			Database.refreshPhoneNumbers(c);
		if(isNullOrEmpty(c.emailAddresses))
			Database.refreshEmailAddresses(c);
		if(isNullOrEmpty(c.emailAddresses) && isNullOrEmpty(c.phoneNumbers))
			return null;

		try
		{
			HttpResponse venmoHttpResponse = requestVenmoData(getCommaSeperatedList(c.emailAddresses,false),getCommaSeperatedList(c.phoneNumbers,true));
			String venmoResponse = convertStreamToString(venmoHttpResponse.getEntity().getContent());
			JSONObject venmoJson = new JSONObject(venmoResponse);
			JSONArray jsonData = venmoJson.getJSONArray("data");
			if(!jsonData.isNull(0))
				return jsonData.getJSONObject(0).getString("username"); //return the first username
			return null;
		}
		catch(IllegalStateException e){Grouptuity.log(e);}
		catch(SSLPeerUnverifiedException e){Grouptuity.log("Venmo SSL Connection Error!");}
		catch(IOException e){Grouptuity.log("Venmo Connection Error! No internet?");}
		catch(JSONException e){Grouptuity.log(e);}
		catch(Exception e){Grouptuity.log("Venmo error!");}
		return retryCode;
	}
	public static String getVenmoUsername(String lookup, String type)
	{
		try
		{
			String[] emails = null, phones = null;
			if(type.equals(LookupDataType.RAW_ID.toString()))
			{
				return lookup;	//without any means to verify a raw username, we have to assume it is valid
			}
			else if(type.equals(LookupDataType.EMAIL.toString()))
			{
				emails = new String[]{lookup};
			}
			else if(type.equals(LookupDataType.PHONE.toString()))
			{
				phones = new String[]{lookup};
			}

			HttpResponse venmoHttpResponse = requestVenmoData(getCommaSeperatedList(emails,false),getCommaSeperatedList(phones,true));
			String venmoResponse = convertStreamToString(venmoHttpResponse.getEntity().getContent());
			JSONObject venmoJson = new JSONObject(venmoResponse);
			JSONArray jsonData = venmoJson.getJSONArray("data");
			if(!jsonData.isNull(0))
				return jsonData.getJSONObject(0).getString("username"); //return the first username
		}
		catch(IllegalStateException e){Grouptuity.log(e);}
		catch(SSLPeerUnverifiedException e){Grouptuity.log("Venmo SSL Connection Error!");}
		catch(IOException e){Grouptuity.log("Venmo Connection Error! No internet?");}
		catch(JSONException e){Grouptuity.log(e);}
		catch(Exception e){Grouptuity.log("Venmo error!");}
		return null;
	}

	private static HttpResponse requestVenmoData(String emails, String phones) throws IllegalStateException, IOException
	{
		HttpResponse response = null;
		try {        
			HttpClient client = new DefaultHttpClient();
			HttpGet request = new HttpGet();
			String emailString = isNullOrEmpty(emails)? "" : "&emails="+emails;
			String phoneString = isNullOrEmpty(phones)? "" : "&phone_numbers="+phones;
			request.setURI(new URI("https://venmo.com/api/v2/user/find?client_id="+Grouptuity.VENMO_APP_ID+"&client_secret="+Grouptuity.VENMO_SECRET+emailString+phoneString));
			response = client.execute(request);
		}
		catch (URISyntaxException e) {Grouptuity.log(e);}
		catch (ClientProtocolException e) {Grouptuity.log(e);}
		return response;
	}
	public static String convertStreamToString(InputStream inputStream) throws IOException
	{
		if (inputStream != null)
		{
			Writer writer = new StringWriter();
			char[] buffer = new char[1024];
			try {
				Reader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"),1024);
				int n;
				while ((n = reader.read(buffer)) != -1) {writer.write(buffer, 0, n);}
			} 
			finally {inputStream.close();}
			return writer.toString();
		} 
		else return "";
	}
}