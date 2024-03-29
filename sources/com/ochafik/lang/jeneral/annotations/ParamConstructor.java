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

public @interface ParamConstructor {
	/// Neutral value is zero for primitive types and null for non-primitive types
	boolean returnNeutralValue() default false;
	
	/// Calling a factory method corresponding to an optional constructor might raise a NoSuchConstructorException it the constructor is found not to exist when its corresponding factory method is invoked.
	boolean optional() default false;
	
	public static class NoSuchConstructorException extends Exception {
		private static final long serialVersionUID = 3302422751750045358L;
		public NoSuchConstructorException(String text) { super(text); }
		public NoSuchConstructorException(String text, Throwable t) { super(text, t); }
	}
}
