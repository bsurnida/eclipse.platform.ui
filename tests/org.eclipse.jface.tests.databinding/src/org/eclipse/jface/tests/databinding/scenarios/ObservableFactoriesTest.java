/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
/*
 *  Created Oct 21, 2005 by Gili Mendel
 * 
 *  $RCSfile: ObservableFactoriesTest.java,v $
 *  $Revision: 1.1.2.1 $  $Date: 2006/02/13 03:55:25 $ 
 */
package org.eclipse.jface.tests.databinding.scenarios;

import org.eclipse.jface.internal.databinding.api.DataBinding;
import org.eclipse.jface.internal.databinding.api.IDataBindingContext;
import org.eclipse.jface.internal.databinding.api.IObservableFactory;
import org.eclipse.jface.internal.databinding.api.observable.IChangeListener;
import org.eclipse.jface.internal.databinding.api.observable.IObservable;
import org.eclipse.jface.internal.databinding.api.observable.IStaleListener;

public class ObservableFactoriesTest extends ScenariosTestCase {

	interface Root {
	};

	interface None extends Root {
	};

	interface Middle extends None {
	};

	interface StandAlone {
	};

	class RootClass implements Root {
	};

	class NoneClass implements None {
	};

	class MiddleClass implements Middle {
	};

	class AllClass implements StandAlone, Middle, Root {
	};

	class MiddleChild extends MiddleClass {
	};

	interface TestIObservable extends IObservable {
		public Class getType();
	}

	class Factory implements IObservableFactory {
		Class c;

		public Factory(Class c) {
			this.c = c;
		}

		public IObservable createObservable(IDataBindingContext bindingContext,
				Object description) {
			if (c.isInstance(description)) {
				return new TestIObservable() {
					public void dispose() {
					}

					public boolean isDisposed() {
						return false;
					}

					public boolean isStale() {
						return false;
					}

					public void removeChangeListener(
							IChangeListener changeListener) {
					}

					public void addChangeListener(IChangeListener changeListener) {
					}

					public Class getType() {
						return c;
					}

					public void addStaleListener(IStaleListener listener) {
					}

					public void removeStaleListener(IStaleListener listener) {
					}
				};
			}
			return null;
		}
	}

	IObservableFactory root = new Factory(Root.class);

	IObservableFactory middle = new Factory(Middle.class);

	IObservableFactory sa = new Factory(StandAlone.class);

	IObservableFactory factory = new Factory(Object.class);

	private IDataBindingContext myDbc = null;

	protected IDataBindingContext getDbc() {
		if (myDbc == null) {
			myDbc = DataBinding.createContext(new IObservableFactory[0]);
		}
		return myDbc;
	}

	protected Class getFactoryType(Object src) {
		TestIObservable u = (TestIObservable) getDbc().createObservable(src);
		return u.getType();
	}

	public void test_factoryRegistration() {

		getDbc().addObservableFactory(root);
		getDbc().addObservableFactory(middle);

		// Direct mapping
		assertEquals(Root.class, getFactoryType(new RootClass()));
		assertEquals(Middle.class, getFactoryType(new MiddleClass()));

		// Inherent interface
		assertEquals(Root.class, getFactoryType(new NoneClass()));

		// AllClass inherent interface
		assertEquals(Middle.class, getFactoryType(new AllClass()));

		// class inheretence
		assertEquals(Middle.class, getFactoryType(new MiddleChild()));

		// Direct, first interface
		getDbc().addObservableFactory(sa);
		assertEquals(StandAlone.class, getFactoryType(new AllClass()));

		// Class based contribution.
		getDbc().addObservableFactory(factory);
		assertEquals(Object.class, getFactoryType(new AllClass()));

	}

}
