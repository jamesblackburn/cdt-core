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
package org.eclipse.cdt.core.index.provider;

import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.core.runtime.CoreException;

/**
 * An IIndexProvider implementation provides additional indexing information for CDT projects
 * This interface only exists to hold commonality from sub-interfaces.
 */
public interface IIndexProvider {
	/**
	 * This method is called to attach the index provider to the project specified. If the provider determines that it doesn't
	 * and will never provide indexes for the specified project, then it should return false to opt-out of being queried for
	 * that project.
	 * <p>
	 * The method will only be called once per project per eclipse session. This method will be called when a project is deleted
	 * and a new project of the same name added. It also may be called lazily (at the point of first logical index use).
	 * <p>
	 * It is also expected the provider implementation will want to watch some properties of the project to determine
	 * what index content to provide.
	 * @param project
	 * @return
	 */
	public boolean providesFor(ICProject project) throws CoreException;
}