/*******************************************************************************
 * Copyright (c) 2006, 2007 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 		QNX - Initial API and implementation
 * 		IBM Corporation
 *      Andrew Ferguson (Symbian)
 *******************************************************************************/
package org.eclipse.cdt.core.browser;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.index.IIndex;
import org.eclipse.cdt.core.index.IIndexBinding;
import org.eclipse.cdt.core.index.IIndexFileLocation;
import org.eclipse.cdt.core.index.IIndexName;
import org.eclipse.cdt.core.index.IndexFilter;
import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.cdt.core.parser.ast.ASTAccessVisibility;
import org.eclipse.cdt.internal.core.browser.util.IndexModelUtil;
import org.eclipse.cdt.internal.core.pdom.dom.PDOMNotImplementedError;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;

/**
 * @author Doug Schaefer
 *
 */
public class IndexTypeInfo implements ITypeInfo {
	private final String[] fqn;
	private final int elementType;
	private final IIndex index;
	private ITypeReference reference; // lazily constructed
	
	public IndexTypeInfo(String[] fqn, int elementType, IIndex index) {
		this.fqn = fqn;
		this.elementType = elementType;
		this.index = index;
	}

	public void addDerivedReference(ITypeReference location) {
		throw new PDOMNotImplementedError();
	}

	public void addReference(ITypeReference location) {
		throw new PDOMNotImplementedError();
	}

	public boolean canSubstituteFor(ITypeInfo info) {
		throw new PDOMNotImplementedError();
	}

	public boolean encloses(ITypeInfo info) {
		throw new PDOMNotImplementedError();
	}

	public boolean exists() {
		throw new PDOMNotImplementedError();
	}

	public int getCElementType() {
		return elementType;
	}

	public ITypeReference[] getDerivedReferences() {
		throw new PDOMNotImplementedError();
	}

	public ITypeInfo[] getEnclosedTypes() {
		throw new PDOMNotImplementedError();
	}

	public ITypeInfo[] getEnclosedTypes(int[] kinds) {
		throw new PDOMNotImplementedError();
	}

	public ITypeInfo getEnclosingNamespace(boolean includeGlobalNamespace) {
		throw new PDOMNotImplementedError();
	}

	public ICProject getEnclosingProject() {
		if(getResolvedReference()!=null) {
			IProject project = reference.getProject();
			if(project!=null) {
				return CCorePlugin.getDefault().getCoreModel().getCModel().getCProject(project.getName());
			}
		}
		return null;
	}

	public ITypeInfo getEnclosingType() {
		// TODO not sure
		return null;
	}

	public ITypeInfo getEnclosingType(int[] kinds) {
		throw new PDOMNotImplementedError();
	}

	public String getName() {
		return fqn[fqn.length-1];
	}

	public IQualifiedTypeName getQualifiedTypeName() {
		return new QualifiedTypeName(fqn);
	}

	public ITypeReference[] getReferences() {
		throw new PDOMNotImplementedError();
	}

	public ITypeReference getResolvedReference() {
		if(reference==null) {
			try {
				index.acquireReadLock();

				char[][] cfqn = new char[fqn.length][];
				for(int i=0; i<fqn.length; i++)
					cfqn[i] = fqn[i].toCharArray();

				IIndexBinding[] ibs = index.findBindings(cfqn, new IndexFilter() {
					public boolean acceptBinding(IBinding binding) {
						return IndexModelUtil.bindingHasCElementType(binding, new int[]{elementType});
					}
				}, new NullProgressMonitor());
				if(ibs.length>0) {
					IIndexName[] names = index.findNames(ibs[0], IIndex.FIND_DEFINITIONS);
					if(names.length>0) {
						IIndexFileLocation ifl = names[0].getFile().getLocation();
						String fullPath = ifl.getFullPath();
						if(fullPath!=null) {
							IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(fullPath));
							if(file!=null) {
								reference = new TypeReference(
										file, file.getProject(), names[0].getNodeOffset(), names[0].getNodeLength()
								);
							}
						} else {
							IPath path = URIUtil.toPath(ifl.getURI());
							if(path!=null) {
								reference = new TypeReference(
										path, null, names[0].getNodeOffset(), names[0].getNodeLength()
								);
							}
						}
					}
				}
			} catch(CoreException ce) {
				CCorePlugin.log(ce);				
			} catch (InterruptedException ie) {
				CCorePlugin.log(ie);
			} finally {
				index.releaseReadLock();
			}
		}
		return reference;
	}

	public ITypeInfo getRootNamespace(boolean includeGlobalNamespace) {
		throw new PDOMNotImplementedError();
	}

	public ITypeInfo[] getSubTypes() {
		throw new PDOMNotImplementedError();
	}

	public ASTAccessVisibility getSuperTypeAccess(ITypeInfo subType) {
		throw new PDOMNotImplementedError();
	}

	public ITypeInfo[] getSuperTypes() {
		throw new PDOMNotImplementedError();
	}

	public boolean hasEnclosedTypes() {
		throw new PDOMNotImplementedError();
	}

	public boolean hasSubTypes() {
		throw new PDOMNotImplementedError();
	}

	public boolean hasSuperTypes() {
		throw new PDOMNotImplementedError();
	}

	public boolean isClass() {
		throw new PDOMNotImplementedError();
	}

	public boolean isEnclosed(ITypeInfo info) {
		throw new PDOMNotImplementedError();
	}

	public boolean isEnclosed(ITypeSearchScope scope) {
		throw new PDOMNotImplementedError();
	}

	public boolean isEnclosedType() {
		throw new PDOMNotImplementedError();
	}

	public boolean isEnclosingType() {
		throw new PDOMNotImplementedError();
	}

	public boolean isReferenced(ITypeSearchScope scope) {
		throw new PDOMNotImplementedError();
	}

	public boolean isUndefinedType() {
		throw new PDOMNotImplementedError();
	}

	public void setCElementType(int type) {
		throw new PDOMNotImplementedError();
	}

	public int compareTo(Object arg0) {
		throw new PDOMNotImplementedError();
	}

}