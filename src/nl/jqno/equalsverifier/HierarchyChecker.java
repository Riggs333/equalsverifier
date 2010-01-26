/*
 * Copyright 2009 Jan Ouwens
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.jqno.equalsverifier;

import static nl.jqno.equalsverifier.util.Assert.assertFalse;
import static nl.jqno.equalsverifier.util.Assert.assertTrue;
import static nl.jqno.equalsverifier.util.Assert.fail;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.EnumSet;

import nl.jqno.equalsverifier.util.Instantiator;

class HierarchyChecker<T> {
	private final Class<T> klass;
	private final Instantiator<T> instantiator;
	private final EnumSet<Feature> features;
	private final Class<? extends T> redefinedSubclass;
	private final boolean klassIsFinal;
	
	private T reference;
	private T other;

	public HierarchyChecker(Instantiator<T> instantiator, EnumSet<Feature> features, Class<? extends T> redefinedSubclass) {
		if (features.contains(Feature.WEAK_INHERITANCE_CHECK) && redefinedSubclass != null) {
			fail("withRedefinedSubclass and weakInheritanceCheck are mutually exclusive.");
		}
		
		this.instantiator = instantiator;
		this.klass = instantiator.getKlass();
		this.features = EnumSet.copyOf(features);
		this.redefinedSubclass = redefinedSubclass;
		
		klassIsFinal = Modifier.isFinal(klass.getModifiers());
	}
	
	public void check() {
		generateExamples();
		
		checkSuperclass();
		checkSubclass();
		
		checkRedefinedSubclass();
		if (!features.contains(Feature.WEAK_INHERITANCE_CHECK)) {
			checkFinalEqualsMethod();
		}
	}
	
	private void checkSuperclass() {
		Class<? super T> superclass = klass.getSuperclass();
		if (redefinedSubclass != null || superclass == Object.class) {
			return;
		}

		Object equalSuper = instantiateSuperclass(superclass);
		
		if (features.contains(Feature.REDEFINED_SUPERCLASS)) {
			assertFalse("Redefined superclass: " + reference + " may not equal " + equalSuper + ", but it does.",
					reference.equals(equalSuper));
		}
		else {
			T shallow = instantiator.cloneFrom(reference);
			instantiator.shallowScramble(shallow);
			
			assertTrue("Symmetry: " + reference + " does not equal " + equalSuper + ".",
					reference.equals(equalSuper) && equalSuper.equals(reference));
			
			assertTrue("Transitivity: " + reference + " and " + shallow +
					" both equal " + equalSuper + ", which implies they equal each other.",
					reference.equals(shallow) || reference.equals(equalSuper) != equalSuper.equals(shallow));
			
			assertTrue("Superclass: hashCode for " + reference + " should be equal to hashCode for " + equalSuper + ".",
					reference.hashCode() == equalSuper.hashCode());
		}
	}
	
	@SuppressWarnings("unchecked")
	private <S> Object instantiateSuperclass(Class<S> superclass) {
		Instantiator superInstantiator = Instantiator.forClass(superclass);
		return superInstantiator.cloneFrom(reference);
	}

	private void checkSubclass() {
		if (klassIsFinal) {
			return;
		}
		
		T equalSub = instantiator.cloneToSubclass(reference);
		assertTrue("Subclass: " + reference + " is not equal to an instance of a trivial subclass with equal fields. (Consider making the class final.)",
				reference.equals(equalSub));
	}
	
	private void checkRedefinedSubclass() {
		if (klassIsFinal || redefinedSubclass == null) {
			return;
		}
		
		if (methodIsFinal("equals", Object.class)) {
			fail("Subclass: " + klass.getSimpleName() + " has a final equals method; don't need to supply a redefined subclass.");
		}

		T redefinedSub = instantiator.cloneToSubclass(reference, redefinedSubclass);
		assertFalse("Subclass: " + reference + " equals " + redefinedSub + ".",
				reference.equals(redefinedSub));
	}
	
	private void checkFinalEqualsMethod() {
		if (klassIsFinal || redefinedSubclass != null) {
			return;
		}
		
		assertTrue("Subclass: equals is not final. Supply an instance of a redefined subclass using withRedefinedSubclass if equals cannot be final.",
				methodIsFinal("equals", Object.class));
		assertTrue("Subclass: hashCode is not final. Supply an instance of a redefined subclass using withRedefinedSubclass if hashCode cannot be final.",
				methodIsFinal("hashCode"));
	}

	private boolean methodIsFinal(String methodName, Class<?>... parameterTypes) {
		try {
			Method method = klass.getMethod(methodName, parameterTypes);
			return Modifier.isFinal(method.getModifiers());
		}
		catch (SecurityException e) {
			throw new AssertionError("Security error: cannot access equals method for class " + klass);
		}
		catch (NoSuchMethodException e) {
			throw new AssertionError("Impossible: class " + klass + " has no equals method.");
		}
		
	}
	
	private void generateExamples() {
		reference = instantiator.instantiate();
		instantiator.scramble(reference);
		other = instantiator.instantiate();
		instantiator.scramble(other);
		instantiator.scramble(other);
	}
}