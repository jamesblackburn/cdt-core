/*******************************************************************************
 * Copyright (c) 2007 Symbian Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Andrew Ferguson (Symbian) - Initial implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.core.index.composite.c;

import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IEnumeration;
import org.eclipse.cdt.core.dom.ast.IEnumerator;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.index.IIndexBinding;
import org.eclipse.cdt.internal.core.index.IIndexFragmentBinding;
import org.eclipse.cdt.internal.core.index.IIndexType;
import org.eclipse.cdt.internal.core.index.composite.ICompositesFactory;

class CompositeCEnumeration extends CompositeCBinding implements IIndexBinding, IEnumeration, IIndexType {
	public CompositeCEnumeration(ICompositesFactory cf, IIndexFragmentBinding rbinding) {
		super(cf, rbinding);
	}

	public IEnumerator[] getEnumerators() throws DOMException {
		IEnumerator[] result = ((IEnumeration)rbinding).getEnumerators();
		for(int i=0; i<result.length; i++)
			result[i] = (IEnumerator) cf.getCompositeBinding((IIndexFragmentBinding) result[i]);
		return result;
	}

	public boolean isSameType(IType type) {
		return ((IEnumeration)rbinding).isSameType(type);
	}

	public Object clone() {fail(); return null;}
}