/*******************************************************************************
 * Copyright (c) 2007, 2009 Intel Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Intel Corporation - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.core.settings.model.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.cdt.core.settings.model.ICLanguageSettingEntry;
import org.eclipse.cdt.core.settings.model.util.SettingsSet.SettingLevel;

/**
 * Abstract storage for settings entries
 * 
 * Multi-level storage (in order of decreasing precedence).
 * 
 * For example build uses 3 levels with precedence:
 * Level 0:
 *   Build Entries
 * Level 1:
 *   Environment Entries
 * Level 2:
 *   Discovered Entries
 */
public abstract class AbstractEntryStorage {
	private final int fKind;
	
	private static final String EMPTY_STRING = ""; //$NON-NLS-1$

	public AbstractEntryStorage(int kind){
		fKind = kind;
	}
	
	public int getKind(){
		return fKind;
	}
	
	public List<ICLanguageSettingEntry> getEntries(List<ICLanguageSettingEntry> list){
		SettingsSet settings = initCache();
		if(list == null)
			list = new ArrayList<ICLanguageSettingEntry>();
		
		ICLanguageSettingEntry entries[] = settings.getEntries();
		list.addAll(Arrays.asList(entries));
		return list;
	}
	
	protected void resetDefaults(){
		SettingsSet settings = createEmptySettings();
		SettingLevel[] levels = settings.getLevels();
		for(int i = 0; i < levels.length; i++){
			obtainEntriesFromLevel(i, null);
		}
	}
	
	public void setEntries(ICLanguageSettingEntry entries[]){
		if(entries == null){
			resetDefaults();
			return;
		}
		SettingsSet settings = initCache();
		
		settings.applyEntries(entries);
		
		SettingLevel levels[] = settings.getLevels();
		
		for(int i = 0; i < levels.length; i++){
			obtainEntriesFromLevel(i, levels[i]);
		}
	}
	
	protected SettingsSet initCache(){
		SettingsSet settings = createEmptySettings();
		SettingLevel levels[] = settings.getLevels();
		for(int i = 0; i < levels.length; i++){
			putEntriesToLevel(i, levels[i]);
		}
			
		settings.adjustOverrideState();
			
		return settings;
	}
	
	/**
	 * Puts entries for the given levelNum into the passed into SettingLevel.
	 * The implementation will load these settings from its model
	 * @param levelNum
	 * @param level
	 */
	protected abstract void putEntriesToLevel(int levelNum, SettingLevel level);

	/**
	 * Given a SettingLevel, persists the data stored there at the given levelNum
	 * into the storage's model
	 * @param levelNum
	 * @param level
	 */
	protected abstract void obtainEntriesFromLevel(int levelNum, SettingLevel level);

	/**
	 * Initialize this entry storage SettingsSet
	 * @return Initialized SettingSet
	 */
	protected abstract SettingsSet createEmptySettings();
	
	public static String[] macroNameValueFromValue(String value){
		String nv[] = new String[2];
		int index = value.indexOf('=');
		if(index > 0){
			nv[0] = value.substring(0, index);
			nv[1] = value.substring(index + 1);
		} else {
			nv[0] = value;
			nv[1] = EMPTY_STRING;
		}
		return nv;
	}
}

