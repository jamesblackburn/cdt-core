/*******************************************************************************
 * Copyright (c) 2008 Wind River Systems, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Markus Schorn - initial API and implementation
 *******************************************************************************/ 
package org.eclipse.cdt.internal.core.pdom.dom;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.index.IIndexFile;
import org.eclipse.cdt.core.index.IIndexMacro;
import org.eclipse.cdt.core.index.IIndexMacroContainer;
import org.eclipse.cdt.core.parser.util.CharArrayUtils;
import org.eclipse.cdt.internal.core.index.IIndexBindingConstants;
import org.eclipse.cdt.internal.core.index.IIndexFragment;
import org.eclipse.cdt.internal.core.index.IIndexFragmentBinding;
import org.eclipse.cdt.internal.core.index.IIndexScope;
import org.eclipse.cdt.internal.core.pdom.PDOM;
import org.eclipse.cdt.internal.core.pdom.db.Database;
import org.eclipse.core.runtime.CoreException;

/**
 * A container collecting definitions and references for macros.
 * @since 5.0
 */
public class PDOMMacroContainer extends PDOMNamedNode implements IIndexMacroContainer, IIndexFragmentBinding {
	private static final int FIRST_DEF_OFFSET    = PDOMNamedNode.RECORD_SIZE + 0; // size 4
	private static final int FIRST_REF_OFFSET    = PDOMNamedNode.RECORD_SIZE + 4; // size 4
	
	@SuppressWarnings("hiding")
	protected static final int RECORD_SIZE = PDOMNamedNode.RECORD_SIZE + 8;

	public PDOMMacroContainer(PDOM pdom, PDOMLinkage linkage, char[] name) throws CoreException {
		super(pdom, linkage, name);
	}
	
	PDOMMacroContainer(PDOM pdom, int record) {
		super(pdom, record);
	}
		
	@Override
	public int getNodeType() {
		return IIndexBindingConstants.MACRO_CONTAINER;
	}

	@Override
	protected int getRecordSize() {
		return RECORD_SIZE;
	}
	
	public boolean isOrphaned() throws CoreException {
		Database db = pdom.getDB();
		return db.getInt(record + FIRST_DEF_OFFSET) == 0
			&& db.getInt(record + FIRST_REF_OFFSET) == 0;
	}

	public void addDefinition(PDOMMacro name) throws CoreException {
		PDOMMacro first = getFirstDefinition();
		if (first != null) {
			first.setPrevInContainer(name);
			name.setNextInContainer(first);
		}
		setFirstDefinition(name);
	}
	
	public void addReference(PDOMMacroReferenceName name) throws CoreException {
		PDOMMacroReferenceName first = getFirstReference();
		if (first != null) {
			first.setPrevInContainer(name);
			name.setNextInContainer(first);
		}
		setFirstReference(name);
	}
	
	public PDOMMacro getFirstDefinition() throws CoreException {
		int namerec = pdom.getDB().getInt(record + FIRST_DEF_OFFSET);
		return namerec != 0 ? new PDOMMacro(pdom, namerec) : null;
	}
	
	void setFirstDefinition(PDOMMacro macro) throws CoreException {
		int namerec = macro != null ? macro.getRecord() : 0;
		pdom.getDB().putInt(record + FIRST_DEF_OFFSET, namerec);
	}
	
	public PDOMMacroReferenceName getFirstReference() throws CoreException {
		int namerec = pdom.getDB().getInt(record + FIRST_REF_OFFSET);
		return namerec != 0 ? new PDOMMacroReferenceName(pdom, namerec) : null;
	}
	
	void setFirstReference(PDOMMacroReferenceName nextName) throws CoreException {
		int namerec = nextName != null ? nextName.getRecord() : 0;
		pdom.getDB().putInt(record + FIRST_REF_OFFSET, namerec);
	}

	public IIndexMacro[] getDefinitions() throws CoreException {
		PDOMMacro macro;
		List<PDOMMacro> macros= new ArrayList<PDOMMacro>();
		for (macro= getFirstDefinition(); macro != null; macro= macro.getNextInContainer()) {
			macros.add(macro);
		}
		return macros.toArray(new IIndexMacro[macros.size()]);
	}

	@Override
	public void delete(PDOMLinkage linkage) throws CoreException {
		if (linkage != null) {
			linkage.removeMacroContainer(this);
		}
		super.delete(linkage);
	}

	public int getBindingConstant() {
		return IIndexBindingConstants.MACRO_CONTAINER;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cdt.internal.core.index.IIndexFragmentBinding#getFragment()
	 */
	public IIndexFragment getFragment() {
		return pdom;
	}

	public IIndexScope getScope() {
		return null;
	}

	public boolean hasDeclaration() throws CoreException {
		return false;
	}

	public boolean hasDefinition() throws CoreException {
		return pdom.getDB().getInt(record + FIRST_DEF_OFFSET) != 0;
	}

	public IIndexFile getLocalToFile() throws CoreException {
		return null;
	}

	public String[] getQualifiedName() {
		return new String[]{getName()};
	}

	public boolean isFileLocal() throws CoreException {
		return false;
	}

	@Override
	public char[] getNameCharArray() {
		try {
			return super.getNameCharArray();
		} catch (CoreException e) {
			CCorePlugin.log(e);
		}
		return CharArrayUtils.EMPTY;
	}

	public String getName() {
		return new String(getNameCharArray());
	}

	@SuppressWarnings("unchecked")
	public Object getAdapter(Class adapter) {
		if (adapter.isAssignableFrom(PDOMMacroContainer.class)) {
			return this;
		}
		return null;
	}
}