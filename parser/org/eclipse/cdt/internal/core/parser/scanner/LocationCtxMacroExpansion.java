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

import java.util.ArrayList;

import org.eclipse.cdt.core.dom.ast.IASTImageLocation;
import org.eclipse.cdt.core.dom.ast.IASTPreprocessorMacroDefinition;
import org.eclipse.cdt.core.dom.ast.IMacroBinding;

/**
 * A location context representing macro expansions.
 * @since 5.0
 */
class LocationCtxMacroExpansion extends LocationCtx {
	private final LocationMap fLocationMap;
	private final int fLength;
	private final ASTMacroReferenceName fName;
	private final ImageLocationInfo[] fLocationInfos;

	public LocationCtxMacroExpansion(LocationMap map, LocationCtxContainer parent, int parentOffset, int parentEndOffset,
			int sequenceNumber, int length, ImageLocationInfo[] imageLocations,	ASTMacroReferenceName expansion) {
		super(parent, parentOffset, parentEndOffset, sequenceNumber);
		fLocationMap= map;
		fLength= length;
		fName= expansion;
		fLocationInfos= imageLocations;
	}

	public int getSequenceLength() {
		return fLength;
	}
	
	public boolean collectLocations(int start, int length, ArrayList locations) {
		final int offset= start-fSequenceNumber;
		assert offset >= 0 && length >= 0;
		
		if (offset+length <= fLength) {
			locations.add(new ASTMacroExpansionLocation(this, offset, length));
			return true;
		}

		locations.add(new ASTMacroExpansionLocation(this, offset, fLength-offset));
		return false;
	}	
	
	public ASTMacroReferenceName getMacroReference() {
		return fName;
	}
	
	public IASTPreprocessorMacroDefinition getMacroDefinition() {
		return fLocationMap.getMacroDefinition((IMacroBinding) fName.getBinding());
	}
	
	public LocationCtxMacroExpansion findSurroundingMacroExpansion(int sequenceNumber, int length) {
		return this;
	}

	public IASTImageLocation getImageLocation(int offset, int length) {
		if (length == 0) {
			return null;
		}
		final int end= offset+length;
		int nextToCheck= offset;
		ImageLocationInfo firstInfo= null;
		ImageLocationInfo lastInfo= null;
		for (int i = 0; i < fLocationInfos.length; i++) {
			ImageLocationInfo info = fLocationInfos[i];
			if (info.fTokenOffsetInExpansion == nextToCheck) {
				if (lastInfo == null) {
					firstInfo= lastInfo= info;
				}
				else if (lastInfo.canConcatenate(info)) {
					lastInfo= info;
				}
				else {
					return null;
				}
				if (++nextToCheck == end) {
					return firstInfo.createLocation(fLocationMap, lastInfo);
				}
			}
			else if (info.fTokenOffsetInExpansion > nextToCheck) {
				return null;
			}
		}
		return null;
	}
}

