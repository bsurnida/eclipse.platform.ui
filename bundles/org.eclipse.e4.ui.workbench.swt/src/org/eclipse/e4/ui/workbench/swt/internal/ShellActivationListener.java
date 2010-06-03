/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.ui.workbench.swt.internal;

import org.eclipse.e4.core.contexts.IContextConstants;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.bindings.EBindingService;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.basic.MWindow;
import org.eclipse.e4.ui.services.EContextService;
import org.eclipse.e4.workbench.ui.internal.E4Workbench;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

/**
 * An SWT listener for listening for activation events of shells that aren't
 * associated with an MWindow.
 */
class ShellActivationListener implements Listener {

	private static final String ECLIPSE_CONTEXT_DIALOG_ID = "org.eclipse.e4.ui.dialogContext"; //$NON-NLS-1$
	private static final String ECLIPSE_CONTEXT_PREV_CHILD = "org.eclipse.e4.ui.dialogContext.prevChild"; //$NON-NLS-1$

	private MApplication application;

	ShellActivationListener(MApplication application) {
		this.application = application;
	}

	public void handleEvent(Event event) {
		if (!(event.widget instanceof Shell)) {
			return;
		}

		Shell shell = (Shell) event.widget;
		Object obj = shell.getData(AbstractPartRenderer.OWNING_ME);
		if (obj instanceof MWindow) {
			return;
		}

		switch (event.type) {
		case SWT.Activate:
			activate(shell);
			break;
		case SWT.Deactivate:
			deactivate(shell);
			break;
		}
	}

	private void activate(Shell shell) {
		IEclipseContext parentContext = getParentContext(shell);
		IEclipseContext shellContext = getShellContext(shell, parentContext);

		Object tmp = parentContext.getLocal(IContextConstants.ACTIVE_CHILD);
		shell.setData(ECLIPSE_CONTEXT_PREV_CHILD, tmp);
		parentContext.set(IContextConstants.ACTIVE_CHILD, shellContext);
	}

	private void deactivate(Shell shell) {
		final IEclipseContext tmp = (IEclipseContext) shell
				.getData(ECLIPSE_CONTEXT_PREV_CHILD);
		if (tmp == null) {
			return;
		}
		shell.setData(ECLIPSE_CONTEXT_PREV_CHILD, null);
		final IEclipseContext parentContext = getParentContext(shell);
		parentContext.set(IContextConstants.ACTIVE_CHILD, tmp);
	}

	private IEclipseContext getShellContext(final Shell shell,
			IEclipseContext parentContext) {
		IEclipseContext shellContext = (IEclipseContext) shell
				.getData(ECLIPSE_CONTEXT_DIALOG_ID);
		if (shellContext != null) {
			return shellContext;
		}
		final IEclipseContext context = parentContext
				.createChild(EBindingService.DIALOG_CONTEXT_ID);

		context.set(E4Workbench.LOCAL_ACTIVE_SHELL, shell);

		// set the context into the widget for future retrieval
		shell.setData(ECLIPSE_CONTEXT_DIALOG_ID, context);

		EContextService contextService = context.get(EContextService.class);
		contextService.activateContext(EBindingService.DIALOG_CONTEXT_ID);

		shell.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				deactivate(shell);
				context.dispose();
			}
		});

		return context;
	}

	private IEclipseContext getParentContext(Shell shell) {
		IEclipseContext shellContext = (IEclipseContext) shell
				.getData(ECLIPSE_CONTEXT_DIALOG_ID);
		if (shellContext != null) {
			return shellContext.getParent();
		}
		Shell current = null;
		Shell parent = (Shell) shell.getParent();
		while (parent != null) {
			current = parent;
			Object obj = current.getData(AbstractPartRenderer.OWNING_ME);
			if (obj instanceof MWindow) {
				return ((MWindow) obj).getContext();
			}
			obj = current.getData(ECLIPSE_CONTEXT_DIALOG_ID);
			if (obj != null) {
				return (IEclipseContext) obj;
			}
			parent = (Shell) parent.getParent();
		}
		return application.getContext();
	}
}