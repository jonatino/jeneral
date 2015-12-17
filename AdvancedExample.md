
```

package test;
import java.util.*;
import java.io.*;

import com.ochafik.lang.templates.*;
import com.ochafik.lang.templates.annotations.*;

import com.ochafik.lang.templates.examples.TArray;

@Template(
	reifiable = true, 
	params = {
		/** Initial size of the array */
		@Param(name = "initialSize", type = Integer.class, defaultValue = 10),
	}
)
@UseTemplates(@UseTemplate(TArray.class, params = { @ParamValue(parameterName = "U") } ))
public abstract class MonTemplate<U, V> implements MonTemplate_Template<U, V> {
	public MonTemplate(int initialSize) {
		array<U> array = new_U_array(initialSize);
		U u1 = new_U(), u2 = new_U();
		if (equal_U(u1, u2)) {
			
		}
		if (compare_U(u1, u2) < 0) {
			
		}
		//U[] array = new_U_Array(initialSize);
		TArray<U> tarray = new_TArray_U(initialSize);
		TArray<U>[] tarrays = new_TArray_U_array(initialSize);
	}
	public MonTemplate() {
		this(initialSize());
	}

	/**
	Inline velo script here
	*/
	@VelocityTemplate(params = { @Param(name = "ok", type = Collection.class) })
	Methods generatedMethods = new Methods(Collections.singleton(1));
	
	@VelocityTemplate(
		source = "com/ochafik/lang/templates/examples/veloscript.vm", 
		params = { @Param(name = "ok", type = Collection.class) })
	OtherMethods generatedMethods = new Methods(Collections.singleton(1));
	
	@Property
	String simpleProperty;
	
	@Property(bound = true)
	String boundProperty;
	
	@Property(writable = false, bound = true)
	String roProperty;
	
	public static void main(String[] args) {
		MonTemplate<Integer, Integer> temp = MonTemplate.template.newInstance(Integer.class, Integer.class, 1);
		Map<String, Object> parameters = temp.getTemplate().getParameters();
	}
}

```