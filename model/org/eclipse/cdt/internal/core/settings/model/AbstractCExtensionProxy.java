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

import org.eclipse.cdt.core.ICExtensionReference;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.internal.core.CConfigBasedDescriptor;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

public abstract class AbstractCExtensionProxy implements ICProjectDescriptionListener{
	private IProject fProject;
	private String fExtId;
	private boolean fIsNewStyle;
	private boolean fInited;
	private String fExtPointId;
	private Object fProvider;

	public AbstractCExtensionProxy(IProject project, String extPointId) {
		fProject = project;
		fExtPointId = extPointId;
		CProjectDescriptionManager.getInstance().addListener(this, CProjectDescriptionEvent.LOADDED | CProjectDescriptionEvent.APPLIED);
	}

	protected final void providerRequested(){
		if(!fInited)
			checkUpdateProvider(CProjectDescriptionManager.getInstance().getProjectDescription(fProject, false), false, false);
	}


	private ICExtensionReference getRef(ICConfigurationDescription cfg, boolean update){
		if(fExtPointId != null){
			try {
				CConfigBasedDescriptor dr = new CConfigBasedDescriptor(cfg);
				ICExtensionReference[] cextensions = dr.get(fExtPointId, update);
				if (cextensions.length > 0) {
					return cextensions[0];
				}
			} catch (CoreException e) {
			}
		}
		return null;
	}
	
	protected IProject getProject(){
		return fProject;
	}
	
	private void checkUpdateProvider(ICProjectDescription des, boolean recreate, boolean rescan){
		Object newProvider = null;
		Object oldProvider = null;

		synchronized(this){
			if(recreate || rescan || !fInited){
				fInited = true;
				ICExtensionReference ref = null;
				boolean newStile = true;
				ICConfigurationDescription cfg = null;
				if(des != null){
					cfg = ((CProjectDescription)des).getIndexConfiguration();
					if(cfg != null){
						ref = getRef(cfg, false);
						newStile = CProjectDescriptionManager.getInstance().isNewStyleCfg(cfg);
					}
				}
				
				if(ref != null){
					if(recreate || !ref.getID().equals(fExtId)){
						try {
							newProvider = ref.createExtension();
							if(!isValidProvider(newProvider))
								newProvider = null;
						} catch (CoreException e) {
						}
					}
				}
					
				if(newProvider == null){
					if(recreate || fProvider == null || newStile != fIsNewStyle){
						newStile = isNewStyleCfg(cfg);
						newProvider = createDefaultProvider(cfg, newStile);
					}
				}
				
				if(newProvider != null){
					if(fProvider != null){
						deinitializeProvider(fProvider);
						oldProvider = fProvider;
					}
					
					fProvider = newProvider;
					if(ref != null)
						fExtId = ref.getID();
					
					fIsNewStyle = newStile;
					
					initializeProvider(fProvider);
				}
			}
		}
		
		if(newProvider != null)
			postProcessProviderChange(newProvider, oldProvider);
	}
	
	protected boolean isNewStyleCfg(ICConfigurationDescription des){
		return CProjectDescriptionManager.getInstance().isNewStyleCfg(des);
	}
	
	protected abstract boolean isValidProvider(Object o);
	
	protected abstract void initializeProvider(Object o);
	
	protected abstract void deinitializeProvider(Object o);
	
	protected abstract Object createDefaultProvider(ICConfigurationDescription cfgDes, boolean newStile);
	
	protected void postProcessProviderChange(Object newProvider, Object oldProvider){
	}

	public void close(){
		CProjectDescriptionManager.getInstance().removeListener(this);
		if(fProvider != null){
			deinitializeProvider(fProvider);
		}
	}

	public void handleEvent(CProjectDescriptionEvent event) {
		if(!fProject.equals(event.getProject()))
			return;
		
		boolean force = false;
		switch(event.getEventType()){
		case CProjectDescriptionEvent.LOADDED:
			force = true;
		case CProjectDescriptionEvent.APPLIED:
			ICProjectDescription des = event.getNewCProjectDescription();
			if(des != null)
				checkUpdateProvider(des, force, true);
			break;
		}
	}
}