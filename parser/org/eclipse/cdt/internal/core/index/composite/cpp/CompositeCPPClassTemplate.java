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
package org.eclipse.cdt.internal.core.index.composite.cpp;

import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassTemplate;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassTemplatePartialSpecialization;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPClassType;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPSpecialization;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPTemplateParameter;
import org.eclipse.cdt.internal.core.dom.parser.cpp.ICPPInternalTemplateInstantiator;
import org.eclipse.cdt.internal.core.index.IIndexFragmentBinding;
import org.eclipse.cdt.internal.core.index.composite.ICompositesFactory;

public class CompositeCPPClassTemplate extends CompositeCPPClassType implements
ICPPClassTemplate, ICPPInternalTemplateInstantiator{

	public CompositeCPPClassTemplate(ICompositesFactory cf, ICPPClassType ct) {
		super(cf, ct);
	}
	
	public ICPPClassTemplatePartialSpecialization[] getPartialSpecializations()
			throws DOMException {
		ICPPClassTemplatePartialSpecialization[] result= ((ICPPClassTemplate)rbinding).getPartialSpecializations();
		for(int i=0; i<result.length; i++) {
			result[i]= (ICPPClassTemplatePartialSpecialization) cf.getCompositeBinding((IIndexFragmentBinding)result[i]);
		}
		return result;
	}

	public ICPPTemplateParameter[] getTemplateParameters() throws DOMException {
		ICPPTemplateParameter[] result= ((ICPPClassTemplate)rbinding).getTemplateParameters();
		for(int i=0; i<result.length; i++) {
			result[i]= (ICPPTemplateParameter) cf.getCompositeBinding((IIndexFragmentBinding) result[i]);
		}
		return result;
	}

	public ICPPSpecialization deferredInstance(IType[] arguments) {
		return InternalTemplateInstantiatorUtil.deferredInstance(arguments, cf, rbinding);
	}

	public ICPPSpecialization getInstance(IType[] arguments) {
		return InternalTemplateInstantiatorUtil.getInstance(arguments, cf, rbinding);
	}

	public IBinding instantiate(IType[] arguments) {
		return InternalTemplateInstantiatorUtil.instantiate(arguments, cf, rbinding);
	}

}