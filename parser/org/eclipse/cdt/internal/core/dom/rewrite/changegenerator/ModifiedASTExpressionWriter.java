/*******************************************************************************
 * Copyright (c) 2008 Institute for Software, HSR Hochschule fuer Technik  
 * Rapperswil, University of applied sciences and others
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0 
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html  
 *  
 * Contributors: 
 * Institute for Software - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.core.dom.rewrite.changegenerator;

import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTExpressionList;
import org.eclipse.cdt.core.dom.ast.IASTInitializer;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.cpp.CPPASTVisitor;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTNewExpression;
import org.eclipse.cdt.internal.core.dom.rewrite.ASTModification;
import org.eclipse.cdt.internal.core.dom.rewrite.ASTModificationStore;
import org.eclipse.cdt.internal.core.dom.rewrite.ASTModification.ModificationKind;
import org.eclipse.cdt.internal.core.dom.rewrite.astwriter.ExpressionWriter;
import org.eclipse.cdt.internal.core.dom.rewrite.astwriter.MacroExpansionHandler;
import org.eclipse.cdt.internal.core.dom.rewrite.astwriter.Scribe;
import org.eclipse.cdt.internal.core.dom.rewrite.commenthandler.NodeCommentMap;

public class ModifiedASTExpressionWriter extends ExpressionWriter {

	private final ASTModificationHelper modificationHelpder;

	public ModifiedASTExpressionWriter(Scribe scribe, CPPASTVisitor visitor,
			MacroExpansionHandler macroHandler, ASTModificationStore modStore, NodeCommentMap commentMap) {
		super(scribe, visitor, macroHandler, commentMap);
		this.modificationHelpder = new ASTModificationHelper(modStore);
	}

	@Override
	protected void writeExpressions(IASTExpressionList expList,
			IASTExpression[] expressions) {
		IASTExpression[] modifiedExpressions = modificationHelpder.createModifiedChildArray(expList, expressions);
		super.writeExpressions(expList, modifiedExpressions);
	}

	@Override
	protected IASTExpression getNewInitializer(ICPPASTNewExpression newExp) {

		
		IASTExpression initializer = newExp.getNewInitializer();
		
		if(initializer != null){
			for(ASTModification childModification : modificationHelpder.modificationsForNode(initializer)){
				switch(childModification.getKind()){
				case REPLACE:
					if(childModification.getNewNode() instanceof IASTInitializer){
						return (IASTExpression)childModification.getNewNode();
					}
					break;
				case INSERT_BEFORE:
					throw new UnhandledASTModificationException(childModification);
					
				case APPEND_CHILD:
					throw new UnhandledASTModificationException(childModification);
				}
			}
		}
		else
		{
			for(ASTModification parentModification : modificationHelpder.modificationsForNode(newExp)){
				if(parentModification.getKind() == ModificationKind.APPEND_CHILD){
					IASTNode newNode = parentModification.getNewNode();
					if(newNode instanceof IASTInitializer){
						return (IASTExpression) newNode;
					}
				}
			}
		}
		return initializer;
	}

	@Override
	protected IASTExpression[] getNewTypeIdArrayExpressions(
			ICPPASTNewExpression newExp, IASTExpression[] expressions) {
		IASTExpression[] modifiedExpressions = modificationHelpder.createModifiedChildArray(newExp, expressions);
		return modifiedExpressions;
	}
	
	
}