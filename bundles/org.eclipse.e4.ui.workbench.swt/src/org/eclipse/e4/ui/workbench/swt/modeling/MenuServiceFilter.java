package org.eclipse.e4.ui.workbench.swt.modeling;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import javax.inject.Inject;
import org.eclipse.core.expressions.EvaluationResult;
import org.eclipse.core.internal.expressions.ReferenceExpression;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.e4.core.contexts.IContextConstants;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.internal.workbench.swt.AbstractPartRenderer;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.MCoreExpression;
import org.eclipse.e4.ui.model.application.ui.menu.MMenu;
import org.eclipse.e4.ui.model.application.ui.menu.MMenuContribution;
import org.eclipse.e4.ui.model.application.ui.menu.MMenuElement;
import org.eclipse.e4.ui.model.application.ui.menu.MPopupMenu;
import org.eclipse.e4.ui.model.application.ui.menu.MRenderedMenu;
import org.eclipse.e4.ui.workbench.IPresentationEngine;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.e4.ui.workbench.modeling.ExpressionContext;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Widget;

public class MenuServiceFilter implements Listener {
	public static boolean DEBUG = false;

	private static void trace(String msg, Widget menu, MMenu menuModel) {
		System.err.println(msg + ": " + menu + ": " + menuModel);
	}

	private static Method aboutToShow;

	public static Method getAboutToShow() {
		if (aboutToShow == null) {
			try {
				aboutToShow = MenuManager.class
						.getDeclaredMethod("handleAboutToShow");
				aboutToShow.setAccessible(true);
			} catch (SecurityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchMethodException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return aboutToShow;
	}

	@Inject
	private MApplication application;

	@Inject
	private IPresentationEngine renderer;

	@Inject
	EModelService modelService;

	private HashMap<Menu, Runnable> pendingCleanup = new HashMap<Menu, Runnable>();

	public void handleEvent(Event event) {
		if (!(event.widget instanceof Menu)) {
			return;
		}
		final Menu menu = (Menu) event.widget;
		Object obj = menu.getData(AbstractPartRenderer.OWNING_ME);
		if (obj == null && menu.getParentItem() != null) {
			obj = menu.getParentItem().getData(AbstractPartRenderer.OWNING_ME);
		}
		if (obj instanceof MRenderedMenu) {
			handlerRenderedMenu(event, menu, (MRenderedMenu) obj);
		} else if (obj instanceof MPopupMenu) {
			handleContextMenu(event, menu, (MPopupMenu) obj);
		} else if (obj instanceof MMenu) {
			handleMenu(event, menu, (MMenu) obj);
		}
	}

	private void handleMenu(final Event event, final Menu menu,
			final MMenu menuModel) {
		switch (event.type) {
		case SWT.Show:
			if (DEBUG) {
				trace("handleMenu.Show", menu, menuModel);
			}
			cleanUp(menu);
			showMenu(event, menu, menuModel);
			break;
		case SWT.Hide:
			if (DEBUG) {
				trace("handleMenu.Hide", menu, menuModel);
			}
			// TODO we'll clean up on show
			break;
		case SWT.Dispose:
			if (DEBUG) {
				trace("handleMenu.Dispose", menu, menuModel);
			}
			cleanUp(menu);
			break;
		}
	}

	private void showMenu(final Event event, final Menu menu,
			final MMenu menuModel) {
		final IEclipseContext parentContext = modelService
				.getContainingContext(menuModel);

		final ArrayList<MMenuElement> menuContributionsToRemove = new ArrayList<MMenuElement>();
		ExpressionContext eContext = new ExpressionContext(parentContext);
		addMenuContributions(menuModel, menuModel.getElementId(),
				menuContributionsToRemove, eContext);

		// create a cleanup routine for the Hide or next Show
		pendingCleanup.put(menu, new Runnable() {
			public void run() {
				if (!menu.isDisposed()) {
					unrender(menuModel);
				}
				removeMenuContributions(menuModel, menuContributionsToRemove);
			}
		});
		render(menu, menuModel);
	}

	private void handleContextMenu(final Event event, final Menu menu,
			final MPopupMenu menuModel) {
		switch (event.type) {
		case SWT.Show:
			if (DEBUG) {
				trace("handleContextMenu.Show", menu, menuModel);
			}
			cleanUp(menu);
			showPopup(event, menu, menuModel);
			break;
		case SWT.Hide:
			if (DEBUG) {
				trace("handleContextMenu.Hide", menu, menuModel);
			}
			// TODO we'll clean up on show
			break;
		case SWT.Dispose:
			if (DEBUG) {
				trace("handleContextMenu.Dispose", menu, menuModel);
			}
			cleanUp(menu);
			break;
		}
	}

	private void showPopup(final Event event, final Menu menu,
			final MPopupMenu menuModel) {
		final IEclipseContext popupContext = menuModel.getContext();
		final IEclipseContext parentContext = popupContext.getParent();
		final IEclipseContext originalChild = (IEclipseContext) parentContext
				.getLocal(IContextConstants.ACTIVE_CHILD);
		parentContext.set(IContextConstants.ACTIVE_CHILD, popupContext);

		final ArrayList<MMenuElement> menuContributionsToRemove = new ArrayList<MMenuElement>();
		ExpressionContext eContext = new ExpressionContext(popupContext);
		addMenuContributions(menuModel, menuModel.getElementId(),
				menuContributionsToRemove, eContext);
		for (String tag : menuModel.getTags()) {
			if (tag.startsWith("popup:") && tag.length() > 6) {
				addMenuContributions(menuModel, tag.substring(6),
						menuContributionsToRemove, eContext);
			}
		}

		// create a cleanup routine for the Hide or next Show
		pendingCleanup.put(menu, new Runnable() {
			public void run() {
				if (!menu.isDisposed()) {
					unrender(menuModel);
				}
				removeMenuContributions(menuModel, menuContributionsToRemove);
				parentContext
						.set(IContextConstants.ACTIVE_CHILD, originalChild);
			}
		});
		render(menu, menuModel);
	}

	private void render(final Menu menu, final MMenu menuModel) {
		if (DEBUG) {
			trace("render", menu, menuModel);
		}
		for (MMenuElement element : menuModel.getChildren()) {
			renderer.createGui(element, menu);
		}
	}

	private void unrender(final MMenu menuModel) {
		if (DEBUG) {
			trace("unrender", (Widget) menuModel.getWidget(), menuModel);
		}
		for (MMenuElement element : menuModel.getChildren()) {
			renderer.removeGui(element);
		}
	}

	private void addMenuContributions(final MMenu menuModel, final String id,
			final ArrayList<MMenuElement> menuContributionsToRemove,
			final ExpressionContext eContext) {
		ArrayList<MMenuContribution> toContribute = new ArrayList<MMenuContribution>();
		for (MMenuContribution menuContribution : application
				.getMenuContributions()) {
			String parentID = menuContribution.getParentID();
			boolean popup = parentID.equals("popup")
					&& (menuModel instanceof MPopupMenu);
			if (!popup && !parentID.equals(id)
					|| !menuContribution.isToBeRendered()) {
				continue;
			}
			if (isVisible(menuContribution, eContext)) {
				toContribute.add(menuContribution);
			}
		}
		for (MMenuContribution menuContribution : toContribute) {
			// TODO place the menu contribution "in" the model, instead of
			// at the end
			for (MMenuElement item : menuContribution.getChildren()) {
				MMenuElement copy = (MMenuElement) EcoreUtil
						.copy((EObject) item);
				if (DEBUG) {
					trace("addMenuContribution " + copy,
							(Widget) menuModel.getWidget(), menuModel);
				}
				menuContributionsToRemove.add(copy);
				menuModel.getChildren().add(copy);
			}
		}
	}

	private void removeMenuContributions(final MMenu menuModel,
			final ArrayList<MMenuElement> menuContributionsToRemove) {
		for (MMenuElement item : menuContributionsToRemove) {
			if (DEBUG) {
				trace("removeMenuContributions " + item,
						(Widget) menuModel.getWidget(), menuModel);
			}
			menuModel.getChildren().remove(item);
		}
	}

	private void handlerRenderedMenu(final Event event, final Menu menu,
			final MRenderedMenu menuModel) {
		// Do nothing here for the moment, except process any cleanups
		switch (event.type) {
		case SWT.Show:
			if (DEBUG) {
				trace("handlerRenderedMenu.Show", menu, menuModel);
			}
			cleanUp(menu);
			showRenderedMenu(event, menu, menuModel);
			break;
		case SWT.Hide:
			if (DEBUG) {
				trace("handlerRenderedMenu.Hide", menu, menuModel);
			}
			// TODO don't care
			break;
		case SWT.Dispose:
			if (DEBUG) {
				trace("handlerRenderedMenu.Dispose", menu, menuModel);
			}
			cleanUp(menu);
			break;
		}
	}

	private void showRenderedMenu(final Event event, final Menu menu,
			final MRenderedMenu menuModel) {
		if (!(menuModel.getContributionManager() instanceof MenuManager)) {
			return;
		}

		MenuManager manager = (MenuManager) menuModel.getContributionManager();
		if (DEBUG) {
			trace("showRenderedMenu: " + manager, menu, menuModel);
		}
		Method handleAboutToShow = getAboutToShow();
		try {
			handleAboutToShow.invoke(manager);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (menuModel.getChildren().size() == 1
				&& menuModel.getChildren().get(0) instanceof MPopupMenu) {
			showPopup(event, menu, (MPopupMenu) menuModel.getChildren().get(0));
		} else {
			showMenu(event, menu, menuModel);
		}
		event.type = SWT.None;
		event.doit = false;
	}

	private void cleanUp(final Menu menu) {
		if (DEBUG) {
			trace("cleanUp", menu, null);
		}
		if (pendingCleanup.isEmpty()) {
			return;
		}
		Runnable cleanUp = pendingCleanup.remove(menu);
		if (cleanUp != null) {
			if (DEBUG) {
				trace("cleanUp.run()", menu, null);
			}
			cleanUp.run();
		}
	}

	private boolean isVisible(MMenuContribution menuContribution,
			ExpressionContext eContext) {
		if (menuContribution.getVisibleWhen() == null) {
			return true;
		}
		MCoreExpression exp = (MCoreExpression) menuContribution
				.getVisibleWhen();
		ReferenceExpression ref = new ReferenceExpression(
				exp.getCoreExpressionId());
		try {
			return ref.evaluate(eContext) != EvaluationResult.FALSE;
		} catch (CoreException e) {
			e.printStackTrace();
		}
		return false;
	}
}