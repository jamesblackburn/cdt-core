/*******************************************************************************
 * Copyright (c) 2004, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM - Initial API and implementation
 *    Markus Schorn (Wind River Systems)
 *    Bryan Wilkinson (QNX)
 *    Andrew Ferguson (Symbian)
 *******************************************************************************/
package org.eclipse.cdt.internal.core.dom.parser.cpp.semantics;

import org.eclipse.cdt.core.dom.ast.DOMException;
import org.eclipse.cdt.core.dom.ast.IPointerType;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.ITypedef;

/**
 * The cost of an implicit conversion sequence.
 * 
 * See [over.best.ics] 13.3.3.1.
 */
class Cost {
	//Some constants to help clarify things
	public static final int AMBIGUOUS_USERDEFINED_CONVERSION = 1;
	
	public static final int NO_MATCH_RANK = -1;
	public static final int IDENTITY_RANK = 0;
	public static final int LVALUE_OR_QUALIFICATION_RANK = 0;
	public static final int PROMOTION_RANK = 1;
	public static final int CONVERSION_RANK = 2;
	public static final int DERIVED_TO_BASE_CONVERSION = 3;
	public static final int USERDEFINED_CONVERSION_RANK = 4;
	public static final int ELLIPSIS_CONVERSION = 5;
	public static final int FUZZY_TEMPLATE_PARAMETERS = 6;
	
	public IType source;
	public IType target;
	
	public boolean targetHadReference = false;
	
	public int lvalue;
	public int promotion;
	public int conversion;
	public int qualification;
	public int userDefined;
	public int rank = -1;
	public int detail;
	
	public Cost( IType s, IType t ){
		source = s;
		target = t;
	}

	public int compare( Cost cost ) throws DOMException{
		int result = 0;
		
		if( rank != cost.rank ){
			return cost.rank - rank;
		}
		
		if( userDefined != 0 || cost.userDefined != 0 ){
			if( userDefined == 0 || cost.userDefined == 0 ){
				return cost.userDefined - userDefined;
			} 
			if( (userDefined == AMBIGUOUS_USERDEFINED_CONVERSION || cost.userDefined == AMBIGUOUS_USERDEFINED_CONVERSION) ||
				(userDefined != cost.userDefined ) )
					return 0;
	 
				// else they are the same constructor/conversion operator and are ranked
				//on the standard conversion sequence
	
		}
		
		if( promotion > 0 || cost.promotion > 0 ){
			result = cost.promotion - promotion;
		}
		if( conversion > 0 || cost.conversion > 0 ){
			if( detail == cost.detail ){
				result = cost.conversion - conversion;
			} else {
				result = cost.detail - detail;
			}
		}
		
		if( result == 0 ){
			if( cost.qualification != qualification ){
				return cost.qualification - qualification;
			} else if( (cost.qualification == qualification) && qualification == 0 ){
				return 0;
			} else {
				IPointerType op1, op2;
				IType t1 = cost.target, t2 = target;
				int subOrSuper = 0;
				while( true ){
					op1 = null;
					op2 = null;
					while( true ){
						if( t1 instanceof ITypedef )
                            try {
                                t1 = ((ITypedef)t1).getType();
                            } catch ( DOMException e ) {
                                t1 = e.getProblem();
                            }
                        else {
							if( t1 instanceof IPointerType )		
								op1 = (IPointerType) t1;	
							break;
						}
					}
					while( true ){
						if( t2 instanceof ITypedef )
                            try {
                                t2 = ((ITypedef)t2).getType();
                            } catch ( DOMException e ) {
                                t2 = e.getProblem();
                            }
                        else {
							if( t2 instanceof IPointerType )		
								op1 = (IPointerType) t2;	
							break;
						}
					}
					if( op1 == null || op2 == null )
						break;
					
					int cmp = ( op1.isConst() ? 1 : 0 ) + ( op1.isVolatile() ? 1 : 0 ) -
					          ( op2.isConst() ? 1 : 0 ) + ( op2.isVolatile() ? 1 : 0 );

					if( subOrSuper == 0 )
						subOrSuper = cmp;
					else if( subOrSuper > 0 ^ cmp > 0) {
						result = -1;
						break;
					}

				}
				if( result == -1 ){
					result = 0;
				} else {
					if( op1 == op2 ){
						result = subOrSuper;
					} else {
						result = op1 != null ? 1 : -1; 
					}
				}
			}
		}
		 
		return result;
	}
}