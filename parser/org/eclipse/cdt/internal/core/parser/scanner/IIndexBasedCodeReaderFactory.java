/*******************************************************************************
 * Copyright (c) 2007, 2008 Wind River Systems, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Markus Schorn - initial API and implementation
 *******************************************************************************/ 
package org.eclipse.cdt.internal.core.parser.scanner;

import org.eclipse.cdt.core.dom.ICodeReaderFactory;

/** 
 * The index based code-reader factory fakes the inclusion of files that are already indexed.
 * When trying to figure out whether a specific header has been included or not, the factory
 * has to be consulted.
 * @since 4.0.1
 */
public interface IIndexBasedCodeReaderFactory extends ICodeReaderFactory {
	/**
	 * Returns whether or not the file has been included.
	 */
	boolean hasFileBeenIncludedInCurrentTranslationUnit(String path);
	
	/**
	 * Create an InclusionContent object for the given location.
     * return an inclusion content or <code>null</code> if the location does not exist.
	 * @see IncludeFileContent
	 */
	public IncludeFileContent getContentForInclusion(String fileLocation);
}