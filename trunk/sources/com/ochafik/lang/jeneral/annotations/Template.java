/*
   Copyright 2008 Olivier Chafik

   Licensed under the Apache License, Version 2.0 (the License);
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an AS IS BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

   This file comes from the Jeneral project (Java Reifiable Generics & Class Templates)

       http://jeneral.googlecode.com/.
*/
package com.ochafik.lang.jeneral.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.rmi.NoSuchObjectException;

import org.apache.commons.lang.UnhandledException;

/**
 * @see Property
 * A template class gets a template interface generated automatically for it.<br/>
 * It must implement its autogenerated template interface with the same generic parameters (if any).<br/>
 * <br/>
 * Template classes must be declared abstract.<br/>
 * In order to instantiate a template of class MyTemplate, call MyTemplate.template.newInstance(args...).<br/>
 * <br/>
 * There is one newInstance method for each public constructor in MyTemplate. 
 * Each of these newInstance methods will have the same throws clause as its target constructor.
 * It also keeps the arguments of its target constructor, but may add other arguments :<br/>
 * <ul>
 * <li>if the template class has generic parameters, the argument list of all newInstance methods
 * 		will start by the classes of generic parameters.
 * </li><li>if the template class has fields annotated with com.ochafik.lang.jeneral.annotations.Property with the addToConstructors value set to true, 
 * 		then the list of every newInstance method will end with arguments for these fields.
 * </li></ul>
 * For instance let's look at the following template class definition :
 * <pre><code>
 * @Template class MyTemplate&lt;T&gt; implements MyTemplate_&lt;T&gt; {
 * 		@Property(addToConstructors = true)
 * 		int constructedValue;
 * 
 * 		@Property
 * 		int otherValue;
 * 
 * 		public MyTemplate() throws IOException {
 * 			...
 * 		}
 * 		public MyTemplate(boolean b) {
 * 			...
 * 		}
 * }
 * </code></pre>
 * 
 * The following newInstance methods will be available :
 * <ul>
 * <li>		MyTemplate&lt;T&gt; MyTemplate.template.newInstance(Class&lt;T&gt; t, boolean b, int constructedValue);
 * </li><li>MyTemplate&lt;T&gt; MyTemplate.template.newInstance(Class&lt;T&gt; t, int constructedValue) throws IOException;
 * </li></ul>
 * @author Olivier Chafik
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE})
public @interface Template {
	Param[] params() default {};
	
	/// Reifiable templates have a "Factory" inner class which various newInstance methods allow for pure-generics instantiation of the template.
	boolean reifiable() default true;
	
	/// If true, any template class referenced within the template class will be instantiated if its generic parameters contain one of the template's parameters 
	boolean cascadesInstantiations() default true;
	
	/// Ways to implement missing interface methods (or abstract methods)
	public enum DefaultImplementationStrategy {
		/// implement missing interface methods with methods that throw UnsupportedOperationException exceptions
		ThrowingImplementation,
		
		/// return 0 or null when the return type is void, do nothing otherwise
		NeutralReturn, 
		
		/// do not implement missing interface methods
		NoImplementation 
	}
	
	/// How to implement any missing interfaces
	DefaultImplementationStrategy implementMissing() 
		default DefaultImplementationStrategy.NoImplementation;
}