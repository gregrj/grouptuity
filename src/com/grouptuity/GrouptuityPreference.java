package com.grouptuity;

public class GrouptuityPreference<E>
{
	final public String key;
	final public E defaultValue;
	final private PreferenceType type;
	private BooleanPreference booleanPreference;
	private StringPreference stringPreference;
	private IntegerPreference integerPreference;
	private FloatPreference floatPreference;
	private LongPreference longPreference;

	public GrouptuityPreference(String str, E defValue)
	{
		key = str;
		defaultValue = defValue;
		if(defaultValue instanceof Boolean)
			type = PreferenceType.BOOLEAN;
		else if(defaultValue instanceof Float)
			type = PreferenceType.FLOAT;
		else if(defaultValue instanceof Integer)
			type = PreferenceType.INTEGER;
		else if(defaultValue instanceof Long)
			type = PreferenceType.LONG;
		else
			type = PreferenceType.STRING;
		Grouptuity.preferencesHashMap.put(key,this);
		refresh();
	}

	public void refresh()
	{
		if(Grouptuity.preferences==null)
		{
			switch(type)
			{
				case BOOLEAN:	booleanPreference = new BooleanPreference((Boolean)defaultValue);break;
				case FLOAT:		floatPreference = new FloatPreference((Float)defaultValue);break;
				case INTEGER:	integerPreference = new IntegerPreference((Integer)defaultValue);break;
				case LONG:		longPreference = new LongPreference((Long)defaultValue);break;
				default:		stringPreference = new StringPreference((String)defaultValue);break;
			}
		}
		else if(Grouptuity.preferences.contains(key))
		{
			switch(type)
			{
				case BOOLEAN:	booleanPreference = new BooleanPreference(Grouptuity.preferences.getBoolean(key,(Boolean)defaultValue));break;
				case FLOAT:		floatPreference = new FloatPreference(Grouptuity.preferences.getFloat(key,(Float)defaultValue));break;
				case INTEGER:	integerPreference = new IntegerPreference(Grouptuity.preferences.getInt(key,(Integer)defaultValue));break;
				case LONG:		longPreference = new LongPreference(Grouptuity.preferences.getLong(key,(Long)defaultValue));break;
				default:		stringPreference = new StringPreference(Grouptuity.preferences.getString(key,(String)defaultValue));break;
			}
		}
		else
			setValue(defaultValue);
	}
	
	@SuppressWarnings("unchecked")
	public E getValue()
	{
		switch(type)
		{
			case BOOLEAN:	return (E) booleanPreference.value;
			case FLOAT:		return (E) floatPreference.value;
			case INTEGER:	return (E) integerPreference.value;
			case LONG:		return (E) longPreference.value;
			default:		return (E) stringPreference.value;
		}
	}
	public void setValueWithoutSaving(E newValue)
	{
		switch(type)
		{
			case BOOLEAN:	booleanPreference = new BooleanPreference((Boolean)newValue);break;
			case FLOAT:		floatPreference = new FloatPreference((Float)newValue);break;
			case LONG:		longPreference = new LongPreference((Long)newValue);break;
			case INTEGER:	integerPreference = new IntegerPreference((Integer)newValue);break;
			default:		stringPreference = new StringPreference((String)newValue);break;
		}
	}
	public void setValue(E newValue)
	{
		switch(type)
		{
			case BOOLEAN:	booleanPreference = new BooleanPreference((Boolean)newValue);Grouptuity.preferences.edit().putBoolean(key,booleanPreference.value).commit();break;
			case FLOAT:		floatPreference = new FloatPreference((Float)newValue);Grouptuity.preferences.edit().putFloat(key,floatPreference.value).commit();break;
			case LONG:		longPreference = new LongPreference((Long)newValue);Grouptuity.preferences.edit().putLong(key,longPreference.value).commit();break;
			case INTEGER:	integerPreference = new IntegerPreference((Integer)newValue);Grouptuity.preferences.edit().putInt(key,integerPreference.value).commit();break;
			default:		stringPreference = new StringPreference((String)newValue);Grouptuity.preferences.edit().putString(key,stringPreference.value).commit();break;
		}
		Grouptuity.backupManager.dataChanged();
	}
	public String toString()
	{
		switch(type)
		{
			case BOOLEAN:	return booleanPreference.value.toString();
			case FLOAT:		return floatPreference.value.toString();
			case INTEGER:	return integerPreference.value.toString();
			case LONG:		return longPreference.value.toString();
			default:		return stringPreference.value;
		}
	}

	private enum PreferenceType{BOOLEAN,STRING,INTEGER,FLOAT,LONG;}
	private class BooleanPreference{private Boolean value;private BooleanPreference(Boolean b){value = b;}}
	private class FloatPreference{private Float value;private FloatPreference(Float f){value = f;}}
	private class IntegerPreference{private Integer value;private IntegerPreference(Integer i){value = i;}}
	private class LongPreference{private Long value;private LongPreference(Long l){value = l;}}
	private class StringPreference{private String value;private StringPreference(String str){value = str;}}
}