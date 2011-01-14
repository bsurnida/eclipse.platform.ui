/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.ui.model.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.model.application.MApplicationElement;
import org.eclipse.e4.ui.model.application.ui.MContext;
import org.eclipse.e4.ui.model.application.ui.MUIElement;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EGenericType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.ETypeParameter;

public class ModelUtils {
	public static EClassifier getTypeArgument(EClass eClass,
			EGenericType eGenericType) {
		ETypeParameter eTypeParameter = eGenericType.getETypeParameter();

		if( eTypeParameter != null ) {
			for (EGenericType eGenericSuperType : eClass.getEAllGenericSuperTypes()) {
				EList<ETypeParameter> eTypeParameters = eGenericSuperType
						.getEClassifier().getETypeParameters();
				int index = eTypeParameters.indexOf(eTypeParameter);
				if (index != -1
						&& eGenericSuperType.getETypeArguments().size() > index) {
					return getTypeArgument(eClass, eGenericSuperType
							.getETypeArguments().get(index));
				}
			}
			return null;
		} else {
			return eGenericType.getEClassifier();
		}
	}

	public static MApplicationElement findElementById(MApplicationElement element, String id) {
		if (id == null || id.length() == 0)
			return null;
		// is it me?
		if (id.equals(element.getElementId()))
			return element;
		// Recurse if this is a container
		EList<EObject> elements = ((EObject) element).eContents();
		for (EObject childElement : elements) {
			if (!(childElement instanceof MApplicationElement))
				continue;
			MApplicationElement result = findElementById((MApplicationElement) childElement, id);
			if (result != null)
				return result;
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	public static List<MApplicationElement> merge(MApplicationElement container, EStructuralFeature feature, List<MApplicationElement> elements, String positionInList) {
		EObject eContainer = (EObject) container;
		
		if( feature.isMany() ) {
			List<MApplicationElement> copy = new ArrayList<MApplicationElement>(elements);
			
			List list = (List)eContainer.eGet(feature);
			boolean flag = true;
			if( positionInList != null && positionInList.trim().length() != 0 ) {
				int index = -1;
				if( positionInList.startsWith("first") ) {
					index = 0;
				} else if( positionInList.startsWith("index:") ) {
					index = Integer.parseInt(positionInList.substring("index:".length()));	
				} else if( positionInList.startsWith("before:") || positionInList.startsWith("after:") ) {
					String elementId;
					boolean before;
					if( positionInList.startsWith("before:") ) {
						elementId = positionInList.substring("before:".length());
						before = true;
					} else {
						elementId = positionInList.substring("after:".length());
						before = false;
					}
					
					int tmpIndex = -1;
					for( int i = 0; i < list.size(); i++ ) {
						if( elementId.equals(((MApplicationElement)list.get(i)).getElementId()) ) {
							tmpIndex = i;
							break;
						}
					}
					
					if( tmpIndex != -1 ) {
						if( before ) {
							index = tmpIndex;
						} else {
							index = tmpIndex + 1;
						}
					} else {
						System.err.println("Could not find element with Id '"+elementId+"'");
					}
				} else {
					System.err.println("Not a valid list position.");
				}
				
				
				if( index >= 0 && list.size() > index ) {
					flag = false;
					list.addAll(index, elements);
				}
			}
			
			// If there was no match append it to the list
			if( flag ) {
				list.addAll(elements);
			}
			
			return copy;
		} else {
			if( elements.size() >= 1 ) {
				if( elements.size() > 1 ) {
					//FIXME Pass the logger
					System.err.println("The feature is single valued but a list of values is passed in.");
				}
				MApplicationElement e = elements.get(0);
				eContainer.eSet(feature, e);
				return Collections.singletonList(e);
			}
		}
		
		return Collections.emptyList();
	}

	public static IEclipseContext getContainingContext(MUIElement element) {
		MUIElement curParent = null;
		if (element.getCurSharedRef() != null)
			curParent = element.getCurSharedRef().getParent();
		else
			curParent = (MUIElement) ((EObject) element).eContainer();

		while (curParent != null) {
			if (curParent instanceof MContext) {
				return ((MContext) curParent).getContext();
			}

			if (curParent.getCurSharedRef() != null)
				curParent = curParent.getCurSharedRef().getParent();
			else
				curParent = (MUIElement) ((EObject) curParent).eContainer();
		}

		return null;
	}

}
