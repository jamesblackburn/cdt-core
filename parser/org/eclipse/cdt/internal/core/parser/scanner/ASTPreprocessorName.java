/*******************************************************************************
 * Copyright (c) 2007 Wind River Systems, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Markus Schorn - initial API and implementation
 *******************************************************************************/ 
package org.eclipse.cdt.internal.core.parser.scanner;

import org.eclipse.cdt.core.dom.ILinkage;
import org.eclipse.cdt.core.dom.ast.ASTNodeProperty;
import org.eclipse.cdt.core.dom.ast.IASTCompletionContext;
import org.eclipse.cdt.core.dom.ast.IASTFileLocation;
import org.eclipse.cdt.core.dom.ast.IASTImageLocation;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTNodeLocation;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.IMacroBinding;
import org.eclipse.cdt.internal.core.dom.Linkage;

/**
 * Models IASTNames as needed for the preprocessor statements and macro expansions.
 * @since 5.0
 */
class ASTPreprocessorName extends ASTPreprocessorNode implements IASTName {
	private final char[] fName;
	private final IBinding fBinding;
	public ASTPreprocessorName(IASTNode parent, ASTNodeProperty property, int startNumber, int endNumber, char[] name, IBinding binding) {
		super(parent, property, startNumber, endNumber);
		fName= name;
		fBinding= binding;
	}

	public IBinding resolveBinding() {
		return fBinding;
	}
	public IBinding getBinding() {
		return fBinding;
	}
	public ILinkage getLinkage() {
		return Linkage.NO_LINKAGE;
	}
	public IASTCompletionContext getCompletionContext() {
		return null;
	}
	public boolean isDeclaration() {
		return false;
	}
	public boolean isDefinition() {
		return false;
	}
	public boolean isReference() {
		return false;
	}
	public char[] toCharArray() {
		return fName;
	}    	
	public String toString() {
		return new String(fName);
	}
	public void setBinding(IBinding binding) {assert false;}
}

class ASTPreprocessorDefinition extends ASTPreprocessorName {
	public ASTPreprocessorDefinition(IASTNode parent, ASTNodeProperty property, int startNumber,
			int endNumber, char[] name, IBinding binding) {
		super(parent, property, startNumber, endNumber, name, binding);
	}

	public boolean isDefinition() {
		return true;
	}
}


class ASTBuiltinName extends ASTPreprocessorDefinition {
	private final IASTFileLocation fFileLocation;

	public ASTBuiltinName(IASTNode parent, ASTNodeProperty property, IASTFileLocation floc, char[] name, IBinding binding) {
		super(parent, property, -1, -1, name, binding);
		fFileLocation= floc;
	}

	public boolean contains(IASTNode node) {
		return node==this;
	}

	public String getContainingFilename() {
		if (fFileLocation == null) {
			return ""; //$NON-NLS-1$
		}
		return fFileLocation.getFileName();
	}

	public IASTFileLocation getFileLocation() {
		return fFileLocation;
	}

	public IASTNodeLocation[] getNodeLocations() {
		if (fFileLocation == null) {
			return new IASTNodeLocation[0];
		}
		return new IASTNodeLocation[]{fFileLocation};
	}

	public String getRawSignature() {
		if (fFileLocation == null) {
			return ""; //$NON-NLS-1$
		}
		return toString();
	}
}

class ASTMacroReferenceName extends ASTPreprocessorName {
	private ImageLocationInfo fImageLocationInfo;
	
	public ASTMacroReferenceName(IASTNode parent, int offset, int endOffset, IMacroBinding macro, ImageLocationInfo imgLocationInfo) {
		super(parent, IASTTranslationUnit.EXPANSION_NAME, offset, endOffset, macro.getNameCharArray(), macro);
		fImageLocationInfo= imgLocationInfo;
	}

	public boolean isReference() {
		return true;
	}

	public IASTImageLocation getImageLocation() {
		if (fImageLocationInfo != null) {
			IASTTranslationUnit tu= getTranslationUnit();
			if (tu != null) {
				LocationMap lr= (LocationMap) tu.getAdapter(LocationMap.class);
				if (lr != null) {
					return fImageLocationInfo.createLocation(lr, fImageLocationInfo);
				}
			}
		}
		return null;
	}	
}