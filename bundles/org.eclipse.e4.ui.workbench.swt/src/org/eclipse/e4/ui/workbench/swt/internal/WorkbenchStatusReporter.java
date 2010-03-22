/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/

package org.eclipse.e4.ui.workbench.swt.internal;

import javax.inject.Inject;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.e4.core.services.Logger;
import org.eclipse.e4.core.services.StatusReporter;
import org.eclipse.e4.core.services.annotations.Optional;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.window.IShellProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.osgi.framework.BundleContext;

/**
 *
 */
public class WorkbenchStatusReporter extends StatusReporter {

	@Inject
	IShellProvider shellProvider;
	@Inject
	Logger logger;
	@Inject
	@Optional
	BundleContext bundleContext;
	ErrorDialog dialog;

	public void report(IStatus status, int style, Object... information) {
		int action = style & (IGNORE | LOG | SHOW | BLOCK);
		if (action == 0) {
			if (status.matches(ERROR)) {
				action = SHOW;
			} else {
				action = LOG;
			}
		}
		if (style != IGNORE) {
			// log even if showing a dialog
			log(status);
			if ((action & (SHOW | BLOCK)) != 0) {
				boolean shouldBlock = (action & BLOCK) != 0;
				openDialog(status, shouldBlock, information);
			}
		}
	}

	private void log(IStatus status) {
		if (status.matches(ERROR)) {
			logger.error(status.getException(), status.getMessage());
		} else if (status.matches(WARNING)) {
			logger.warn(status.getException(), status.getMessage());
		} else if (status.matches(INFO)) {
			logger.info(status.getException(), status.getMessage());
		}
	}

	private void openDialog(final IStatus status, boolean shouldBlock,
			Object... information) {
		String[] informationStrings = new String[information.length];
		if (dialog != null && dialog.getShell() != null
				&& !dialog.getShell().isDisposed()) {
			// another dialog is still open, ideally we'd add the current status
			// to that dialog.
			// for now, just log the new problem
			log(status);
			return;
		}
		final Status exceptionStatus = new Status(status.getSeverity(), status
				.getPlugin(), status.getException() == null ? status
				.getMessage() : status.getException().toString(), status
				.getException());
		dialog = new ErrorDialog(shellProvider.getShell(), "Internal Error",
				status.getMessage(),
				status.getException() != null ? exceptionStatus : status, ERROR
						| WARNING | INFO) {
			protected void configureShell(Shell shell) {
				super.configureShell(shell);
				shell.setBackgroundMode(SWT.INHERIT_DEFAULT);
			}
		};
		for (int i = 0; i < information.length; i++) {
			informationStrings[i] = information[i] == null ? "null" : information[i].toString(); //$NON-NLS-1$
		}
		dialog.setBlockOnOpen(shouldBlock);
		dialog.open();
	}

	public IStatus newStatus(int severity, String message, Throwable exception) {
		return new Status(severity, getPluginId(), message, exception);
	}

	protected String getPluginId() {
		return bundleContext == null ? "unknown" : bundleContext.getBundle()
				.getSymbolicName();
	}

}