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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import org.eclipse.cdt.core.dom.ast.IASTFileLocation;

/**
 * Base class for all location contexts that can contain children. 
 * <p>
 * @since 5.0
 */
class LocationCtxContainer extends LocationCtx {
	/**
	 * The total length of all children in terms of sequence numbers.
	 */
	private int fChildSequenceLength;

	private ArrayList fChildren;
	private char[] fSource;
	private int[] fLineOffsets;
	
	public LocationCtxContainer(LocationCtxContainer parent, char[] source, int parentOffset, int parentEndOffset, int sequenceNumber) {
		super(parent, parentOffset, parentEndOffset, sequenceNumber);
		fSource= source;
	}
	
	public Collection getChildren() {
		return fChildren == null ? Collections.EMPTY_LIST : fChildren;
	}

	public void addChild(LocationCtx locationCtx) {
		if (fChildren == null) {
			fChildren= new ArrayList();
		}
		fChildren.add(locationCtx);
	}

	public char[] getSource(int offset, int length) {
		offset= Math.max(0, Math.min(offset, fSource.length));
		length= Math.max(0, Math.min(length, fSource.length-offset));
		char[] result= new char[length];
		System.arraycopy(fSource, offset, result, 0, length);
		return result;
	}

	public final int getSequenceLength() {
		return fSource.length + fChildSequenceLength;
	}
	
	public final int getSequenceNumberForOffset(int offset, boolean checkChildren) {
		int result= fSequenceNumber + fChildSequenceLength + offset;
		if (checkChildren && fChildren != null) {
			for (int i= fChildren.size()-1; i >= 0; i--) {
				final LocationCtx child= (LocationCtx) fChildren.get(i);
				if (child.fEndOffsetInParent > offset) {	// child was inserted behind the offset, adjust sequence number
					result-= child.getSequenceLength();
				}
				else {
					return result;
				}
			}
		}
		return result;
	}
	
	public void addChildSequenceLength(int childLength) {
		fChildSequenceLength+= childLength;
	}

	public final LocationCtx findSurroundingContext(int sequenceNumber, int length) {
		int testEnd= length > 1 ? sequenceNumber+length-1 : sequenceNumber;
		final LocationCtx child= findChildLessOrEqualThan(sequenceNumber, false);
		if (child != null && child.fSequenceNumber+child.getSequenceLength() > testEnd) {
			return child.findSurroundingContext(sequenceNumber, length);
		}
		return this;
	}

	public final LocationCtxMacroExpansion findSurroundingMacroExpansion(int sequenceNumber, int length) {
		int testEnd= length > 1 ? sequenceNumber+length-1 : sequenceNumber;
		final LocationCtx child= findChildLessOrEqualThan(sequenceNumber, true);
		if (child != null && child.fSequenceNumber+child.getSequenceLength() > testEnd) {
			return child.findSurroundingMacroExpansion(sequenceNumber, length);
		}
		return null;
	}

	public IASTFileLocation findMappedFileLocation(int sequenceNumber, int length) {
		// try to delegate to a child.
		int testEnd= length > 1 ? sequenceNumber+length-1 : sequenceNumber;
		final LocationCtx child= findChildLessOrEqualThan(sequenceNumber, false);
		if (child != null && child.fSequenceNumber+child.getSequenceLength() > testEnd) {
			return child.findMappedFileLocation(sequenceNumber, length);
		}
		return super.findMappedFileLocation(sequenceNumber, length);
	}

	public boolean collectLocations(int sequenceNumber, final int length, ArrayList locations) {
		final int endSequenceNumber= sequenceNumber+length;
		if (fChildren != null) {
			int childIdx= Math.max(0, findChildIdxLessOrEqualThan(sequenceNumber, false));
			for (; childIdx < fChildren.size(); childIdx++) {
				final LocationCtx child= (LocationCtx) fChildren.get(childIdx);

				// create the location between start and the child
				if (sequenceNumber < child.fSequenceNumber) {
					// compute offset backwards from the child's offset
					final int offset= child.fEndOffsetInParent - (child.fSequenceNumber - sequenceNumber);
					// it the child is not affected, we are done.
					if (endSequenceNumber <= child.fSequenceNumber) {
						addFileLocation(offset, endSequenceNumber-sequenceNumber, locations);
						return true;
					}
					addFileLocation(offset, child.fOffsetInParent-offset, locations);
					sequenceNumber= child.fSequenceNumber;
				}

				// let the child create locations
				final int childEndSequenceNumber= child.fSequenceNumber + child.getSequenceLength();
				if (sequenceNumber < childEndSequenceNumber) {
					if (child.collectLocations(sequenceNumber, endSequenceNumber-sequenceNumber, locations)) {
						return true;
					}
					sequenceNumber= childEndSequenceNumber;
				}
			}
		}

		// create the location after the last child.
		final int myEndNumber = fSequenceNumber + getSequenceLength();
		final int offset= fSource.length - (myEndNumber - sequenceNumber);
		if (endSequenceNumber <= myEndNumber) {
			addFileLocation(offset, endSequenceNumber-sequenceNumber, locations);
			return true;
		}
		addFileLocation(offset, fSource.length-offset, locations);
		return false;
	}
	
	private ArrayList addFileLocation(int offset, int length, ArrayList sofar) {
		IASTFileLocation loc= createFileLocation(offset, length);
		if (loc != null) {
			sofar.add(loc);
		}
		return sofar;
	}

	ASTFileLocation createFileLocation(int start, int length) {
		return null;
	}

	final int findChildIdxLessOrEqualThan(int sequenceNumber, boolean beforeReplacedChars) {
		if (fChildren == null) {
			return -1;
		}
		int upper= fChildren.size();
		int lower= 0;
		while (upper > lower) {
			int middle= (upper+lower)/2;
			LocationCtx child= (LocationCtx) fChildren.get(middle);
			int childSequenceNumber= child.fSequenceNumber;
			if (beforeReplacedChars) {
				childSequenceNumber-= child.fEndOffsetInParent-child.fOffsetInParent; 
			}
			if (childSequenceNumber <= sequenceNumber) {
				lower= middle+1;
			}
			else {
				upper= middle;
			}
		}
		return lower-1;
	}

	final LocationCtx findChildLessOrEqualThan(final int sequenceNumber, boolean beforeReplacedChars) {
		final int idx= findChildIdxLessOrEqualThan(sequenceNumber, beforeReplacedChars);
		return idx >= 0 ? (LocationCtx) fChildren.get(idx) : null;
	}

	public void getInclusions(ArrayList result) {
		if (fChildren != null) {
			for (Iterator iterator = fChildren.iterator(); iterator.hasNext();) {
				LocationCtx ctx= (LocationCtx) iterator.next();
				if (ctx.getInclusionStatement() != null) {
					result.add(new ASTInclusionNode(ctx));
				}
				else {
					ctx.getInclusions(result);
				}
			}
		}
	}
	
	public int getLineNumber(int offset) {
		if (fLineOffsets == null) {
			fLineOffsets= computeLineOffsets();
		}
		int idx= Arrays.binarySearch(fLineOffsets, offset);
		if (idx < 0) {
			return -idx;
		}
		return idx+1;
	}

	private int[] computeLineOffsets() {
		ArrayList offsets= new ArrayList();
		for (int i = 0; i < fSource.length; i++) {
			if (fSource[i] == '\n') {
				offsets.add(new Integer(i));
			}
		}
		int[] result= new int[offsets.size()];
		for (int i = 0; i < result.length; i++) {
			result[i]= ((Integer) offsets.get(i)).intValue();
			
		}
		return result;
	}
}