/*******************************************************************************
 * Copyright (c) 2007 Intel Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Intel Corporation - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.core.settings.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.cdt.core.settings.model.ICLanguageSetting;
import org.eclipse.cdt.core.settings.model.ICLanguageSettingEntry;
import org.eclipse.cdt.core.settings.model.ICSettingBase;
import org.eclipse.cdt.core.settings.model.extension.CLanguageData;
import org.eclipse.cdt.core.settings.model.extension.impl.CDefaultLanguageData;
import org.eclipse.cdt.core.settings.model.util.EntryStore;
import org.eclipse.cdt.core.settings.model.util.KindBasedStore;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.content.IContentTypeManager;
import org.eclipse.core.runtime.content.IContentTypeSettings;
import org.eclipse.core.runtime.preferences.IScopeContext;

public class CLanguageSetting extends CDataProxy implements
		ICLanguageSetting {

	CLanguageSetting(CLanguageData data, CDataProxyContainer parent, CConfigurationDescription cfg) {
		super(data, parent, cfg);
	}

	public final int getType() {
		return ICSettingBase.SETTING_LANGUAGE;
	}

//	public IContentType getHeaderContentType() {
//		CLanguageData data = getCLanguageData(false);
//		return data.getHeaderContentType();
//	}

	public String getLanguageId() {
		CLanguageData data = getCLanguageData(false);
		return data.getLanguageId();
	}
	
	public void setLanguageId(String id){
		CLanguageData data = getCLanguageData(true);
		data.setLanguageId(id);
	}
	
	private CLanguageData getCLanguageData(boolean write){
		return (CLanguageData)getData(write);
	}

//TODO:	public ICLanguageSettingEntry[] getSettingEntries() {
//		return getSettingEntries(ICLanguageSettingEntry.ALL);
//	}

	public ICLanguageSettingEntry[] getSettingEntries(int kind) {
		CLanguageData data = getCLanguageData(false);
		return data.getEntries(kind);
	}

	public List getSettingEntriesList(int kind) {
		CLanguageData data = getCLanguageData(false);
		ICLanguageSettingEntry entries[] = data.getEntries(kind);
		int size = entries != null ? entries.length : 0;
		List arrayList = new ArrayList(size);
		for(int i = 0; i < size; i++){
			arrayList.add(entries[i]);
		}
		return arrayList;
	}

	public String[] getSourceContentTypeIds() {
		CLanguageData data = getCLanguageData(false);
		String ids[] = data.getSourceContentTypeIds();
		if(ids != null)
			return ids;
		return CDefaultLanguageData.EMPTY_STRING_ARRAY;
	}

	public int getSupportedEntryKinds() {
		CLanguageData data = getCLanguageData(false);
		return data.getSupportedEntryKinds();
	}

	public boolean supportsEntryKind(int kind) {
		return (getSupportedEntryKinds() & kind) == kind;
	}
	
	public String[] getContentTypeFileSpecs (IContentType type) {
		String[] globalSpecs = type.getFileSpecs(IContentType.FILE_EXTENSION_SPEC); 
		IContentTypeSettings settings = null;
		IProject project = getProject();
		if (project != null) {
			IScopeContext projectScope = new ProjectScope(project);
			try {
				settings = type.getSettings(projectScope);
			} catch (Exception e) {}
			if (settings != null) {
				String[] specs = settings.getFileSpecs(IContentType.FILE_EXTENSION_SPEC);
				if (specs.length > 0) {
					int total = globalSpecs.length + specs.length;
					String[] projSpecs = new String[total];
					int i=0;
					for (int j=0; j<specs.length; j++) {
						projSpecs[i] = specs[j];
						i++;
					}								
					for (int j=0; j<globalSpecs.length; j++) {
						projSpecs[i] = globalSpecs[j];
						i++;
					}								
					return projSpecs;
				}
			}
		}
		return globalSpecs;		
	}
	
	private IProject getProject(){
		return getConfiguration().getProjectDescription().getProject();
	}

/*	public String[] getHeaderExtensions() {
		CLanguageData data = getCLanguageData(false);
		IContentType type = data.getHeaderContentType();
		String[] exts;
		if(type != null) {
			exts = getContentTypeFileSpecs(type);
		} else {
			exts = data.getHeaderExtensions();
			if(exts != null)
				exts = (String[])exts.clone();
			else
				exts = new String[0];
		}
		
		return exts;
	}
*/
	public String[] getSourceExtensions() {
		CLanguageData data = getCLanguageData(false);
		String[] exts = null;
		String[] typeIds = data.getSourceContentTypeIds();
		if(typeIds != null && typeIds.length != 0){
			IContentTypeManager manager = Platform.getContentTypeManager();
			IContentType type;
			if(typeIds.length == 1){
				type = manager.getContentType(typeIds[0]);
				if(type != null)
					exts = getContentTypeFileSpecs(type);
			} else {
				List list = new ArrayList();
				for(int i = 0; i < typeIds.length; i++){
					type = manager.getContentType(typeIds[i]);
					if(type != null) {
						list.addAll(Arrays.asList(getContentTypeFileSpecs(type)));
					}
				}
				exts = (String[])list.toArray(new String[list.size()]);
			}
		} else {
			exts = data.getSourceExtensions();
			if(exts != null)
				exts = (String[])exts.clone();
			else
				exts = new String[0];
		}
		
		if(exts == null)
			exts = CDefaultLanguageData.EMPTY_STRING_ARRAY;
		return exts;
	}
	
/*
	private Map fillNameToEntryMap(ICLanguageSettingEntry entries[], Map map){
		if(map == null)
			map = new HashMap();
		
		for(int i = 0; i < entries.length; i++){
			ICLanguageSettingEntry entry = entries[i];
			map.put(entry.getName(), entry);
		}
		return map;
	}

	
	private class SettingChangeInfo implements ICSettingsChangeInfo {
		CLanguageData fData;
		ICLanguageSettingEntry fNewEntries[];
		int fKind;
		ICLanguageSettingEntryInfo fAddedInfo[];
		ICLanguageSettingEntry fRemoved[];
		
		SettingChangeInfo(int kind, ICLanguageSettingEntry newEntries[], CLanguageData data){
			fNewEntries = newEntries;
			fData = data;
			fKind = kind;
		}

		SettingChangeInfo(int kind, ICLanguageSettingEntryInfo addedEntriesInfo[], ICLanguageSettingEntry removed[], CLanguageData data){
			fAddedInfo = addedEntriesInfo;
			fRemoved = removed;
			fData = data;
			fKind = kind;
		}

		public ICLanguageSettingEntryInfo[] getAddedEntriesInfo() {
			// TODO Auto-generated method stub
			return null;
		}

		public ICLanguageSettingEntry[] getEntries() {
			if(fNewEntries == null){
				ICLanguageSettingEntry oldEntries[] = fData.getSettingEntries(fKind);
				List list = new ArrayList();
				for(int i = 0; i < oldEntries.length; i++){
					ICLanguageSettingEntry entry = oldEntries[i];
					if(entry.getKind() != fKind)
						continue;
					
					list.add(entry);
				}
				
				
			}
			return fNewEntries;
		}

		public int getKind() {
			return fKind;
		}

		public ICLanguageSettingEntry[] getRemovedEntries() {
			// TODO Auto-generated method stub
			return null;
		}
		
	}

	public void changeEntries(ICLanguageSettingEntryInfo[] added, ICLanguageSettingEntry[] removed) {
		CLanguageData data = getCLanguageData(true);
		Map map = null; 
		if(added != null && added.length > 0){
			map = sortEntries(added, true, map);
		}
		if(removed != null && removed.length > 0){
			map = sortEntries(removed, false, map);
		}
		
		if(map != null){
			for(Iterator iter = map.entrySet().iterator(); iter.hasNext();){
				Map.Entry entry = (Map.Entry)iter.next();
				int kind = ((Integer)entry.getKey()).intValue();
				List lists[] = (List[])entry.getValue();
				List aList = lists[0];
				List rList = lists[1];
				ICLanguageSettingEntry sortedAdded[] = aList != null ?
						(ICLanguageSettingEntry[])aList.toArray(new ICLanguageSettingEntry[aList.size()]) 
							: null;
				ICLanguageSettingEntry sortedRemoved[] = rList != null ?
						(ICLanguageSettingEntry[])rList.toArray(new ICLanguageSettingEntry[rList.size()]) 
							: null;
				
				data.changeEntries(kind, sortedAdded, sortedRemoved);
			}
		}
	}
	
	private Map sortEntries(ICLanguageSettingEntry entries[], boolean added, Map map){
		if(map == null)
			map = new HashMap();
		
		int index = added ? 0 : 1;
		for(int i = 0; i < entries.length; i++){
			ICLanguageSettingEntry entry = entries[i];
			if(entry != null){
				Integer iKind = new Integer(entry.getKind());
				List[] addedRemovedListArr = (List[])map.get(iKind);
				if(addedRemovedListArr == null){
					addedRemovedListArr = new List[2];
					map.put(iKind, addedRemovedListArr);
				}
				List list = addedRemovedListArr[index];
				if(list == null){
					list = new ArrayList();
					addedRemovedListArr[index] = list;
				}
				list.add(entry);
			}
		}
		return map;
	}
*/
	public void setSettingEntries(int kind, ICLanguageSettingEntry[] entries) {
		CLanguageData data = getCLanguageData(true);
		EntryStore store = new EntryStore();
//		KindBasedStore nameSetStore = new KindBasedStore();
		int eKind;
		if(entries != null){
			for(int i = 0; i < entries.length; i++){
				ICLanguageSettingEntry entry = entries[i];
				eKind = entry.getKind();
				if((kind & eKind) != 0 && (data.getSupportedEntryKinds() & eKind) != 0){
					store.addEntry(entry);
				}
			}
		} 
		
		setSettingEntries(kind, data, store);
	}
	
	private int[] flagsToArray(int flags){
		int arr[] = new int[32];
		int num = 0;
		for(int i = 1; i != 0; i = i << 1){
			if((flags & i) != 0)
				arr[num++] = i;
		}
		if(num == arr.length)
			return arr;
		else if(num == 0)
			return new int[0];
		int result[] = new int[num];
		System.arraycopy(arr, 0, result, 0, num);
		return result;
	}

	public void setSettingEntries(int kind, List list) {
		CLanguageData data = getCLanguageData(true);
		EntryStore store = new EntryStore();
//		KindBasedStore nameSetStore = new KindBasedStore();
		int eKind;
		for(Iterator iter = list.iterator(); iter.hasNext();){
			ICLanguageSettingEntry entry = (ICLanguageSettingEntry)iter.next();
			eKind = entry.getKind();
			if((kind & eKind) != 0 && (data.getSupportedEntryKinds() & eKind) != 0){
				store.addEntry(entry);
			}
		}
		
		setSettingEntries(kind, data, store);
	}
	
	private void setSettingEntries(int kind, CLanguageData data, EntryStore store){
		int oredk = getSupportedEntryKinds();
		int kinds[] = flagsToArray(oredk);

//		int kinds[] = KindBasedStore.getSupportedKinds();
		for(int i = 0; i < kinds.length; i++){
			ICLanguageSettingEntry sortedEntries[] = store.getEntries(kinds[i]);
			if((kind & kinds[i]) != 0)
				data.setEntries(kinds[i], sortedEntries);
		}
	}

/*	private boolean shouldAdd(ICLanguageSettingEntry entry){
		int kind = entry.getKind();
		Set set = (Set)store.get(kind);
		if(set == null){
			set = new HashSet();
			store.put(kind, set);
		}
		return set.add(entry.getName());
	}
*/
//TODO:	public ICLanguageSettingEntry[] getResolvedSettingEntries() {
		// TODO Auto-generated method stub
//		return getSettingEntries();
//	}

	public ICLanguageSettingEntry[] getResolvedSettingEntries(int kind) {
		// TODO Auto-generated method stub
		return getSettingEntries(kind);
	}

	public void setSourceContentTypeIds(String[] ids) {
		CLanguageData data = getCLanguageData(true);
		
		data.setSourceContentTypeIds(ids);
	}

	public void setSourceExtensions(String[] exts) {
		CLanguageData data = getCLanguageData(true);
		
		data.setSourceExtensions(exts);
	}
	
}