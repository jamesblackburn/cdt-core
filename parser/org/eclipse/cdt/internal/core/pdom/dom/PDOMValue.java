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

import org.eclipse.cdt.core.dom.ast.IBinding;
import org.eclipse.cdt.core.dom.ast.IValue;
import org.eclipse.cdt.internal.core.dom.parser.Value;
import org.eclipse.cdt.internal.core.dom.parser.cpp.ICPPUnknownBinding;
import org.eclipse.cdt.internal.core.pdom.db.Database;
import org.eclipse.core.runtime.CoreException;

/**
 * Helper class for storing values in the index.
 */
public class PDOMValue {

	/**
	 * Stores a value and returns the offset of where it was stored.
	 * @throws CoreException 
	 */
	public static int store(Database db, PDOMLinkage linkage, IValue val) throws CoreException {
		if (val == null)
			return 0;
		
		final IBinding[] unknown= val.getUnknownBindings();
		int[] unknownRecs= {};
		if (unknown.length != 0) {
			unknownRecs= new int[unknown.length];
			for (int i = 0; i < unknown.length; i++) {
				PDOMNode node= linkage.addUnknownValue(unknown[i]);
				if (node == null) {
					return store(db, linkage, Value.UNKNOWN);
				}
				unknownRecs[i]= node.getRecord();
			}
		}
		
		final short len= (short) Math.min(unknown.length, (Database.MAX_MALLOC_SIZE-6)/4); 
		final int block= db.malloc(6+4*len);
		final int repRec= db.newString(val.getInternalExpression()).getRecord();
		
		db.putShort(block, len);
		db.putInt(block+2, repRec);
		
		int p= block+6;
		for (int i = 0; i < len; i++) {
			db.putInt(p, unknownRecs[i]);
			p+= 4;
		}
		return block;
	}

	/**
	 * Restores a value from the given record
	 * @throws CoreException 
	 */
	public static IValue restore(Database db, PDOMLinkage linkage, int valRec) throws CoreException {
		if (valRec == 0)
			return null;
		
		final int len= db.getShort(valRec);
		final int repRec = db.getInt(valRec+2);
		final char[] rep= db.getString(repRec).getChars();
		
		if (len == 0)
			return Value.fromInternalRepresentation(rep, ICPPUnknownBinding.EMPTY_UNKNOWN_BINDING_ARRAY);
		
		ICPPUnknownBinding[] unknown= new ICPPUnknownBinding[len];
		int p= valRec+6;
		for (int i = 0; i < unknown.length; i++) {
			int rec= db.getInt(p);
			PDOMNode node= linkage.getNode(rec);
			if (node instanceof ICPPUnknownBinding) {
				unknown[i]= (ICPPUnknownBinding) node;
			} else {
				return Value.UNKNOWN;
			}
			p+= 4;
		}
		return Value.fromInternalRepresentation(rep, unknown);
	}


	/**
	 * Deletes a value stored at the given record.
	 */
	public static void delete(Database db, int valueRec) throws CoreException {
		if (valueRec == 0)
			return;
		final int repRec = db.getInt(valueRec+2);
		db.getString(repRec).delete();
		db.free(valueRec);
	}

}