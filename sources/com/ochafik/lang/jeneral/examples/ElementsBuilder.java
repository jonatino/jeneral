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
package com.ochafik.lang.jeneral.examples;

import java.io.IOException;

import javax.swing.JLabel;

import com.ochafik.lang.jeneral.annotations.ParamConstructor;
import com.ochafik.lang.jeneral.annotations.Property;
import com.ochafik.lang.jeneral.annotations.Template;
import com.ochafik.lang.jeneral.runtime.Array;
import com.ochafik.lang.jeneral.runtime.Fields;
import com.ochafik.lang.jeneral.runtime.ReflectionException;
 
// Declare that ElementsBuild is a template.
// It has to be abstract and to implement ElementsBuilder_Template, which is autogenerated on the fly in Eclipse, NetBeans or with the apt tool in Sun's JDK
@Template
public abstract class ElementsBuilder<T extends Comparable<T>> implements _ElementsBuilder<T> {
	
	// Generate getters and setters for the 'arg' property, and append it to all ElementBuilder's factory methods
	@Property(construct = true)
	public String arg;
	
	@ParamConstructor 
	public abstract T new_T(T other) throws IOException;
	
	@ParamConstructor
	public abstract T new_T();
	
	@ParamConstructor(returnNeutralValue = true)
	public abstract T neutral_T();
	
	public T buildElement(T other) throws IOException {
		T ret = new_T(other);
		if ((ret.compareTo(other)) < 0)
			return neutral_T();
		
		if (0 > ret.compareTo(other))
			return neutral_T();
		
		try {
			T max = T().cast(T().getField("MAX_VALUE").get(null));
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		try { 
			T max = T().cast(Fields.getStatic(T(), "MAX_VALUE"));
			T max2 = T().cast(Fields.get(max, "MAX_VALUE"));
		} catch (ReflectionException e) {
			e.printStackTrace();
		}
		
		return ret;
	} 
	
	public void test() {
		Array<T> array = T(10);
		Class<?> tArrayClass = array.getClass();
		Class<Array<T>> tTypedArrayClass = (Class<Array<T>>)array.getClass();
		Class<T> tClass = this.T();
		Class<?> tClass2 = new_T().getClass();
		array.set(0, new_T());
		if (T().isPrimitive()) {
			// T is a primitive
			T t = T().cast(0);
		} else {
			T t = null;
		}
	}
}
