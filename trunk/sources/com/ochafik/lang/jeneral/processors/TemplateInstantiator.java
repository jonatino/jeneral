package com.ochafik.lang.jeneral.processors;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtTypedElement;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.eval.PartialEvaluator;
import spoon.reflect.eval.SymbolicEvaluationStep;
import spoon.reflect.eval.SymbolicEvaluator;
import spoon.reflect.eval.SymbolicEvaluatorObserver;
import spoon.reflect.eval.SymbolicInstance;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.visitor.CtStackScanner;
import spoon.reflect.visitor.Query;
import spoon.support.reflect.eval.VisitorSymbolicEvaluator;

import com.ochafik.lang.jeneral.annotations.ParamConstructor;
import com.ochafik.lang.jeneral.annotations.Property;
import com.ochafik.lang.jeneral.annotations.Template;
import com.ochafik.lang.jeneral.annotations.TemplatesPrimitives;
import com.ochafik.lang.jeneral.processors.InstantiationUtils.InstantiationResult;
import com.ochafik.lang.jeneral.runtime.Array;
import com.ochafik.lang.jeneral.runtime.Fields;
import com.ochafik.lang.jeneral.runtime.Methods;
import com.ochafik.lang.jeneral.runtime.ReflectionException;
import com.ochafik.lang.jeneral.runtime.TemplateContractViolationException;
import com.ochafik.util.listenable.Pair;
import com.ochafik.util.string.StringUtils;
import com.sun.tools.javac.tree.Tree.Modifiers;

public class TemplateInstantiator extends AbstractSpoonProcessor<CtClass<?>> {
	List<CtParameter<?>> noParams = new ArrayList<CtParameter<?>>();
	Set<CtTypeReference<? extends Throwable>> noExceptions = new TreeSet<CtTypeReference<? extends Throwable>>();
	Set<InstantiationResult> results = new TreeSet<InstantiationResult>();
	Map<String, InstantiationParams> templateClassNameToParams = new HashMap<String, InstantiationParams>();
	
	public TemplateInstantiator(Set<InstantiationParams> params) {
		for (InstantiationParams param : params) {
			templateClassNameToParams.put(param.templateQualifiedName, param);
		}
	}

	public boolean removeTemplateInterface(CtClass<?> ctClass) {
		String tempQualName = InstantiationUtils.getTemplateInterfaceQualifiedName(ctClass.getQualifiedName());
		
		Set<CtTypeReference<?>> superInterfaces = ctClass.getSuperInterfaces();
		for (CtTypeReference<?> superInterface : superInterfaces) {
			if (superInterface.getQualifiedName().equals(tempQualName)) {
				superInterfaces.remove(superInterface);
				return true;
			}
		}
		
		return false;
	}
	
	public CtInterface<?> getTemplateInterface(CtClass<?> ctClass) {
		String tempQualName = InstantiationUtils.getTemplateInterfaceQualifiedName(ctClass.getQualifiedName());
		
		Set<CtTypeReference<?>> superInterfaces = ctClass.getSuperInterfaces();
		for (CtTypeReference<?> superInterface : superInterfaces) {
			if (superInterface.getQualifiedName().equals(tempQualName)) {
				return (CtInterface<?>)superInterface.getDeclaration();
			}
		}
		return null;
	}
	
	public <T extends Annotation> T getAndRemoveAnnotation(CtElement element, Class<T> annotationClass) {
		T a = element.getAnnotation(annotationClass);
		if (a == null)
			return null;
		
		for (CtAnnotation<? extends Annotation> annotation : element.getAnnotations()) {
			if (annotation.getAnnotationType().getQualifiedName().equals(annotationClass.getName())) {
				element.getAnnotations().remove(annotation);
				return a;
			}
		}
		throw new RuntimeException("Could not find annotation to remove from element " + element.getSignature() + " : " + annotationClass.getName());
	}
	
	public String decorateName(CtClass<?> ctClass, InstantiationParams instantiationParams, Map<CtTypeReference<?>, ReplacedTypeInfo> replacedTypeInfos) {
		StringBuilder b = new StringBuilder(ctClass.getSimpleName());
		for (CtTypeReference<?> ref : ctClass.getFormalTypeParameters()) {
			b.append("__");
			Object value = replacedTypeInfos.get(ref);
			if (value != null) {
				if (value instanceof Class) {
					b.append(((Class<?>)value).getSimpleName());
				} else {
					b.append(value);
				}
			}	
		}
		return b.toString();
	}
	
	class ReplacedTypeInfo {
		public final Class<?> classReplacement;
		public final CtTypeReference<?> paramRefReplacement;
		public final CtTypeReference<?> paramObjectRefReplacement;
		public final CtTypeReference<?> paramArrayRefReplacement;
		public ReplacedTypeInfo(CtTypeReference<?> formalParamRef, Object formalParamValue) {
			if (!(formalParamValue instanceof Class))
				throw new RuntimeException("Expected class value for template parameter " + formalParamRef.getSimpleName()+". Found " + formalParamValue + " instead.");
			classReplacement = (Class<?>)formalParamValue;
			paramRefReplacement = Type().createReference(classReplacement);
			if (classReplacement.isPrimitive()) {
				paramObjectRefReplacement = Type().createReference(getPrimitiveWrapper(classReplacement));
			} else {
				paramObjectRefReplacement = paramRefReplacement;
			}
			paramArrayRefReplacement = Type().createArrayReference(paramRefReplacement);
		}
	}
	
	@SuppressWarnings("unchecked")
	public void process(CtClass<?> ctClass) {
		for (CtMethod m : ctClass.getMethods()) {
			m = null;
		}
		Template templateAnnotation = getAndRemoveAnnotation(ctClass, Template.class);
		if (templateAnnotation == null)
			return;
		
		String templateClassName = ctClass.getQualifiedName();
		InstantiationParams instantiationParams = templateClassNameToParams.get(templateClassName);
		InstantiationResult result = new InstantiationResult(instantiationParams);
		
		handleProperties(ctClass);
		
		List<CtMethod<?>> constructorContracts = getAndRemoveConstructorContracts(ctClass);
		if (!removeTemplateInterface(ctClass))
			System.out.println("Could not find template super interface declaration !");
	
		Map<CtTypeReference<?>, ReplacedTypeInfo> replacedTypeInfos = buildReplacementsInfoAndRemoveProvidedFormalTypes(ctClass, instantiationParams);
		
		replaceClassTMethods(ctClass, replacedTypeInfos);
		replaceParamMethodsAndOperators(ctClass, replacedTypeInfos);
		replaceArrayMethods(ctClass, replacedTypeInfos);
		replaceTClassGetterAndArrayCreator(ctClass, replacedTypeInfos);
		replaceParamConstructors(ctClass, replacedTypeInfos, constructorContracts);
		replaceTAndTArrayTypes(ctClass, replacedTypeInfos, result);
		//replaceMethodsInvokingMethods(ctClass);
		
		new EvaluationVisitor().scan(ctClass);
		/*for (CtMethod meth : ctClass.getMethods()) {
			try {
				//CtBlock evaluatedMethod = evaluator.evaluate(meth, meth.getBody());
				SymbolicInstance<?> evaled = evaluator.evaluate(meth.getBody());
				System.out.println("evaled = " +evaled);
				//meth.setBody(evaluatedMethod);
//				evaluatedMethod = Eval().createPartialEvaluator().evaluate(meth, meth.getBody());
//				meth.setBody(evaluatedMethod);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		*/
		//removeDeadBlocks(ctClass);
		
		decorateInstantiationClassName(ctClass, instantiationParams, replacedTypeInfos);
		
		result.qualifiedName = ctClass.getQualifiedName();
		result.sourceCode = ctClass.getPackage() + "\n" + ctClass;
		
		results.add(result);
	}
	
	//class MyEval extends 
	class EvaluationVisitor extends CtScanner {
		
		SymbolicEvaluator evaluator = Eval().createSymbolicEvaluator();
		
		@Override
		protected void exit(CtElement e) {
			/*super.exit(e);
			if (elementStack.isEmpty())
				for (Map.Entry<CtElement, CtElement> replacement : replacements.entrySet()) {
					replacement.getKey().replace(replacement.getValue());
				}*/
		}
		boolean acceptableEvalReturnValue(Class<?> expression) {
			//if (expression instanceof )
			return expression == Class.class || expression.isPrimitive() || expression == String.class;
		}
		boolean acceptableEvalReturnValue(CtTypedElement<?> e) {
			if (e instanceof CtLiteral)
				return true;
			return acceptableEvalReturnValue(e.getType());
		}
		boolean acceptableEvalReturnValue(CtTypeReference<?> c) {
			if (c == null)
				return false;
			
			String n = c.getQualifiedName();
			return c.isPrimitive() || 
				n.equals(Class.class.getName()) || 
				n.equals(String.class.getName());
		}
		
		boolean hasArgumentsWithAcceptableTypesForEval(CtInvocation<?> invocation) {
			CtExecutableReference<?> exe = invocation.getExecutable();
			if (!exe.getModifiers().contains(ModifierKind.STATIC) && acceptableEvalReturnValue(exe.getType()))
				return false;
			
			for (CtExpression<?> argument : invocation.getArguments())
				if (!acceptableEvalReturnValue(argument))
					return false;
			
			return true;
		}
		
		Map<CtElement, CtElement> replacements = new HashMap<CtElement, CtElement>();
		
		//CtTypeReference<?> stringType = Type().createReference(String.class);
		
		//CtElement replacement;
		
		@Override
		public <T> void visitCtBinaryOperator(CtBinaryOperator<T> operator) {
			super.visitCtBinaryOperator(operator);
			//if (true) return;
			
			if (operator.getKind() == BinaryOperatorKind.PLUS) {
				CtExpression<?> left = operator.getLeftHandOperand();
				CtExpression<?> right = operator.getRightHandOperand();
				CtTypeReference<?> lt = left.getType(), rt = right.getType();
				
				if ((left instanceof CtLiteral) && (right instanceof CtLiteral)) {
					Object lv = ((CtLiteral<?>)left).getValue(), rv = ((CtLiteral<?>)right).getValue();
					if (lv != null && (lv instanceof String) && rv != null && (rv instanceof String)) {
						CtLiteral<String> result = Code().createLiteral(((String)lv) + ((String)rv));
						//replacements.put(operator, result);
						operator.replace(result);
						//super.visitCtLiteral(result);
					}
				}
			}
		}
		@Override
		public void scan(Collection<? extends CtElement> elements) {
			if ((elements != null)) {
				for (CtElement e : new ArrayList<CtElement>(elements)) {
					scan(e);
				}
			}

		}

		@Override
		public <T> void visitCtInvocation(CtInvocation<T> invocation) {
			super.visitCtInvocation(invocation);
			
			if (!hasArgumentsWithAcceptableTypesForEval(invocation))
				return;
			
			CtExecutableReference<T> exe = invocation.getExecutable();
			System.out.println("Invocation = " + invocation);
			
			List<CtExpression<?>> arguments = invocation.getArguments();
			Class[] argsTypes = new Class[arguments.size()];
			Object[] evaluatedArguments = new Object[arguments.size()];
			for (int i = 0, len = evaluatedArguments.length; i < len; i++) {
				CtExpression<?> argument = arguments.get(i);
				if (!acceptableEvalReturnValue(argument))
					return;
				
				CtTypeReference argTypeRef = argument.getType();
				
				try {
					evaluatedArguments[i] = eval(argument);
					if (argTypeRef != null)
						argsTypes[i] = argTypeRef.getActualClass();
					else if (evaluatedArguments[i] != null)
						argsTypes[i] = evaluatedArguments[i].getClass();
					else
						argsTypes[i] = Object.class;
					
				} catch (Exception e) {
					return;
				}
			}
			
			try {
				Class<?> class1 = Class.forName(exe.getDeclaringType().getQualifiedName());
				if (class1 != null) {
					//Method m = class1.getMethod(exe.getSimpleName(), argsTypes);
					//invocation.replace(Code().createLiteral(m.invoke(null, evaluatedArguments)));
					//invocation.replace(Code().createLiteral(Methods.invokeStatic(class1, exe.getSimpleName(), argsTypes, evaluatedArguments)));
					Method m = Methods.getMethodForArgs(class1, exe.getSimpleName(), evaluatedArguments);
					if ((m.getModifiers() & Modifier.STATIC) != 0) {
						invocation.replace(Code().createLiteral(Methods.invokeStatic(class1, exe.getSimpleName(), evaluatedArguments)));
					} else {
						invocation.replace(Code().createLiteral(Methods.invoke(class1, exe.getSimpleName(), evaluatedArguments)));
					}
					
				}
					
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		private Object eval(CtExpression<?> argument) throws Exception {
			if (argument instanceof CtLiteral) {
				return ((CtLiteral<?>)argument).getValue();
			} else {
				SymbolicInstance<?> evaluatedArgument = evaluator.evaluate(argument);
				CtTypeReference<?> concreteType = evaluatedArgument.getConcreteType();
				if (concreteType != null && (evaluatedArgument instanceof CtLiteral))
					return ((CtLiteral<?>)evaluatedArgument).getValue();
				
				throw new Exception();
			}
		}
		@Override
		public void visitCtIf(CtIf ifElement) {
			//super.visitCtIf(ifElement);
			
			CtExpression<Boolean> condition = ifElement.getCondition();
			if (condition instanceof CtLiteral) {
				replaceByStatementOrBlockStatements(ifElement, Boolean.TRUE.equals(((CtLiteral<?>)condition).getValue()) ?
						ifElement.getThenStatement() : ifElement.getElseStatement());
			}
		}
	}
	
	/// Build info on replaced types
	private Map<CtTypeReference<?>, ReplacedTypeInfo> buildReplacementsInfoAndRemoveProvidedFormalTypes(CtClass<?> ctClass, InstantiationParams instantiationParams) {
		Map<CtTypeReference<?>, ReplacedTypeInfo> replacedTypeInfos = new HashMap<CtTypeReference<?>, ReplacedTypeInfo>();
		List<CtTypeReference<?>> formalParamRefs = ctClass.getFormalTypeParameters();
		for (int i = formalParamRefs.size(); i-- != 0;) {
			CtTypeReference<?> formalParamRef = formalParamRefs.get(i);
			Pair<String, Object> nameAndValue = instantiationParams.templateParameters.get(i);
			if (nameAndValue.getFirst() != null)
				if (!nameAndValue.getFirst().equals(formalParamRef.getSimpleName()))
					throw new RuntimeException("Wrong param name at this position : expected " + formalParamRef.getSimpleName()+ ", found " + nameAndValue.getFirst());
			
			Object formalParamValue = nameAndValue.getSecond();
			if (formalParamValue == null) 
				continue;
			
			formalParamRefs.remove(formalParamRef);
			
			replacedTypeInfos.put(formalParamRef, new ReplacedTypeInfo(formalParamRef, formalParamValue));
		}
		return replacedTypeInfos;
	}

	/// Replace Class<T> methods
	@SuppressWarnings("unchecked")
	private void replaceClassTMethods(CtClass<?> ctClass, Map<CtTypeReference<?>, ReplacedTypeInfo> replacedTypeInfos) {
		//TODO make this work for any kind of classes
		for (CtInvocation<?> invocation : Query.getElements(ctClass, typeFilter(CtInvocation.class))) {
			String exeSimpleName = invocation.getExecutable().getSimpleName();
			List<CtExpression<?>> args = invocation.getArguments();
			CtExpression<?> target = invocation.getTarget();
			if (target != null) {
				/// Replace Array.set(int, T), .get(int) and length()
				int nArgs = args.size();
				CtTypeReference<?> targetFormalParamType = getFormalParamClassType(target.getType());
				if (targetFormalParamType != null) {
					
					ReplacedTypeInfo replacedTypeInfo = replacedTypeInfos.get(targetFormalParamType);
					CtElement parent = invocation.getParent();
					
					if (replacedTypeInfo != null) {
						if (parent instanceof CtInvocation) {
							/// If invocation is itself in an invocation (like getMethod().invoke or getField().get)
							CtInvocation parentInvocation = (CtInvocation)parent;
							List<CtExpression<?>> parentArgs = parentInvocation.getArguments();
							if (parentArgs.size() > 0) {
								CtExpression memberInvocationTarget = parentArgs.get(0);
								String parentExeSimpleName = parentInvocation.getExecutable().getSimpleName();
								if (parentExeSimpleName.equals("get") && exeSimpleName.equals("getField") && nArgs == 1 && parentArgs.size() == 1) {
									CtExpression fieldName = args.get(0);
									if (fieldName instanceof CtLiteral) {
										String fieldNameStr = ((CtLiteral)fieldName).getValue().toString();
										if ((memberInvocationTarget instanceof CtLiteral) && ((CtLiteral)memberInvocationTarget).getValue() == null)
											parentInvocation.replace(newSnippet(getObjectWrapper(replacedTypeInfo.classReplacement).getName() + "." + fieldNameStr));
										else
											parentInvocation.replace(newSnippet(memberInvocationTarget + "." + fieldNameStr));
										continue;
									}
								} else if (parentExeSimpleName.equals("invoke") && exeSimpleName.equals("getMethod") && nArgs == 1) {
									CtExpression methodName = args.get(0);
									if (methodName instanceof CtLiteral) {
										String methodNameStr = ((CtLiteral)methodName).getValue().toString();
										if ((memberInvocationTarget instanceof CtLiteral) && ((CtLiteral)memberInvocationTarget).getValue() == null)
											parentInvocation.replace(newSnippet(getObjectWrapper(replacedTypeInfo.classReplacement).getName() + "." + methodNameStr + "(" + StringUtils.implode(args.subList(1, args.size())) + ")"));
										else
											parentInvocation.replace(newSnippet(memberInvocationTarget + "." + methodNameStr + "(" + StringUtils.implode(args.subList(1, args.size())) + ")"));
										continue;
									}
									continue;
								}
							}
						} 
						if (nArgs == 0) {
							/// Inline evaluation of noarg-methods of Class<T>
							try {
								Method m = Class.class.getMethod(exeSimpleName);
								if (m != null && m.getReturnType().isPrimitive() || m.getReturnType() == String.class) {
									invocation.replace(Code().createLiteral(m.invoke(replacedTypeInfo.classReplacement)));
									continue;
								}
							} catch (Exception e) {
								throw new RuntimeException("Exception during eval of " +replacedTypeInfo+".class." + exeSimpleName + "()", e);
							}
						} /*else if (exeSimpleName.equals("cast") && nArgs == 1) {
							CtExpression casted = args.get(0);
							casted.setType(replacedTypeInfo.paramRefReplacement);
							invocation.replace(casted);
							//invocation.replace(newSnippet("(" + replacedTypeInfo.classReplacement.getName()+")" + args.get(0)));
						}*/
					}
				}
			}
		}
		
		for (CtInvocation<?> invocation : Query.getElements(ctClass, typeFilter(CtInvocation.class))) {
			String exeSimpleName = invocation.getExecutable().getSimpleName();
			List<CtExpression<?>> args = invocation.getArguments();
			CtExpression<?> target = invocation.getTarget();
			if (target != null) {
				/// Replace Array.set(int, T), .get(int) and length()
				int nArgs = args.size();
				CtTypeReference<?> targetFormalParamType = getFormalParamClassType(target.getType());
				if (targetFormalParamType != null) {
					
					ReplacedTypeInfo replacedTypeInfo = replacedTypeInfos.get(targetFormalParamType);
			
					if (exeSimpleName.equals("cast") && nArgs == 1) {
						CtExpression casted = args.get(0);
						casted.setType(replacedTypeInfo.paramRefReplacement);
						invocation.replace(casted);
						//invocation.replace(newSnippet("(" + replacedTypeInfo.classReplacement.getName()+")" + args.get(0)));
					}
				}
			}
		}
		
	}

	/// Replace Methods methods
	@SuppressWarnings("unchecked")
	private void replaceMethodsInvokingMethods(CtClass<?> ctClass) {
		InlinerCache inlinerCache = new InlinerCache(getFactory());
		for (CtInvocation<?> invocation : Query.getElements(ctClass, typeFilter(CtInvocation.class))) {
			Inliner inliner = inlinerCache.getInliner(invocation.getExecutable().getActualMethod());
			if (inliner != null)
				inliner.process(invocation);
		}
	}

	/// Change name of class to decorated instantiation name
	private void decorateInstantiationClassName(CtClass<?> ctClass, InstantiationParams instantiationParams, Map<CtTypeReference<?>, ReplacedTypeInfo> replacedTypeInfos) {
		if (instantiationParams.overriddenSimpleName == null) {
			ctClass.setSimpleName(decorateName(ctClass, instantiationParams, replacedTypeInfos));
		} else {
			ctClass.setSimpleName(instantiationParams.overriddenSimpleName);
		}
		CtTypeReference<?> decoratedRef = ctClass.getReference();
		
		/// Change enclosing class of all template fields access to instantiated type
		for (CtFieldAccess<?> fieldAccess : Query.getElements(ctClass, typeFilter(CtFieldAccess.class))) {
			CtTypeReference<?> ref = fieldAccess.getVariable().getDeclaringType();
			if (ref != null && ref.getQualifiedName().equals(instantiationParams.templateQualifiedName)) {
				fieldAccess.getVariable().setDeclaringType(decoratedRef);
			}
		}
		
		/// Change enclosing class of all template method calls to instantiated type
		for (CtInvocation<?> invocation : Query.getElements(ctClass, typeFilter(CtInvocation.class))) {
			CtTypeReference<?> ref = invocation.getExecutable().getDeclaringType();
			if (ref != null && ref.getQualifiedName().equals(instantiationParams.templateQualifiedName))
				invocation.getExecutable().setDeclaringType(decoratedRef);
		}
		
		ctClass.getModifiers().remove(ModifierKind.ABSTRACT);
		//removeProvidedFormalParameters(ctClass, instantiationParams.templateParameters);
		
	}

	/// Replace all typed elements T and Array<T> by actual param type and T[]
	@SuppressWarnings("unchecked")
	private void replaceTAndTArrayTypes(CtClass<?> ctClass, Map<CtTypeReference<?>, ReplacedTypeInfo> replacedTypeInfos, InstantiationResult instantiationResult) {
		for (CtTypedElement<?> typedElement : Query.getElements(ctClass, typeFilter(CtTypedElement.class))) {
			CtTypeReference<?> typedElementType = typedElement.getType();
			if (typedElementType == null)
				continue;
			
			CtTypeReference typeReplacement = getTorTArrayReplacement(typedElementType, false, replacedTypeInfos);
			if (typeReplacement != null) {
				typedElement.setType(typeReplacement);
				continue;
			}
			
			List<CtTypeReference<?>> typeArguments = typedElementType.getActualTypeArguments();
			List<CtTypeReference<?>> replacedTypeArgs = new ArrayList<CtTypeReference<?>>(typeArguments);
			boolean replacedSome = false;
			for (int i = 0, len = replacedTypeArgs.size(); i < len; i++) {
				typeReplacement = getTorTArrayReplacement(typeArguments.get(i), true, replacedTypeInfos);
				if (typeReplacement != null) {
					replacedTypeArgs.set(i, typeReplacement);
					replacedSome = true;
				}
			}
			if (replacedSome) {
				typedElementType.setActualTypeArguments(replacedTypeArgs);
				InstantiationParams cascadedInstantiation = new InstantiationParams(typedElementType.getQualifiedName());
				//cascadedInstantiation
				instantiationResult.cascadedInstantiations.add(cascadedInstantiation);
			}
		}
	}

	/// Replace T new_T(...)
	private void replaceParamConstructors(CtClass<?> ctClass, Map<CtTypeReference<?>, ReplacedTypeInfo> replacedTypeInfos, List<CtMethod<?>> constructorContracts) {
		for (CtInvocation<?> invocation : Query.getElements(ctClass, typeFilter(CtInvocation.class))) {
			List<CtExpression<?>> args = invocation.getArguments();
		
			for (CtMethod<?> constructorContract : constructorContracts) {
				if (!matchesContract(invocation, constructorContract)) 
					continue;
				
				CtTypeReference<?> typeRef = constructorContract.getType();
				ReplacedTypeInfo replacedTypeInfo = replacedTypeInfos.get(typeRef);
				if (replacedTypeInfo == null)
					throw new RuntimeException("Missing template parameter value for parameter " + typeRef.getSimpleName());
				
				if (replacedTypeInfo.classReplacement.isPrimitive()) {
					if (args.size() == 0) {
						invocation.replace(newSnippet(
								replacedTypeInfo.classReplacement.isAssignableFrom(Integer.TYPE) ? 
								"0" : 
								"(" + replacedTypeInfo.classReplacement.getSimpleName() + ")0")); 
					} else if (args.size() == 1) {
						CtExpression singleArg = args.get(0);
						if (replacedTypeInfo.paramRefReplacement.isAssignableFrom(singleArg.getType()))
							invocation.replace(singleArg);
						else
							invocation.replace(newSnippet("(" + replacedTypeInfo.classReplacement.getSimpleName() + ")" + args.get(0)));
					} else {
						throw new TemplateContractViolationException("Primitive types have only two constructors : a default (zero-valued) one, and one with a single argument that has to be castable to the primitive type.");
					}
				} else {
					ParamConstructor paramConstructorAnnotation = constructorContract.getAnnotation(ParamConstructor.class);
					if (paramConstructorAnnotation.returnNeutralValue())
						invocation.replace(newSnippet("(" + replacedTypeInfo.classReplacement.getName() + ")null"));
					else 
						invocation.replace(newSnippet("new " + replacedTypeInfo.classReplacement.getName() + "(" + StringUtils.implode(invocation.getArguments(), ", ") + ")"));
				}
				break;
			}
		}
		
	}

	/// Replace T() and T(int)
	private void replaceTClassGetterAndArrayCreator(CtClass<?> ctClass, Map<CtTypeReference<?>, ReplacedTypeInfo> replacedTypeInfos) {
		for (CtInvocation<?> invocation : Query.getElements(ctClass, typeFilter(CtInvocation.class))) {
			String exeSimpleName = invocation.getExecutable().getSimpleName();
			List<CtExpression<?>> args = invocation.getArguments();
		
			for (Map.Entry<CtTypeReference<?>, ReplacedTypeInfo> e : replacedTypeInfos.entrySet()) {
				ReplacedTypeInfo replacedTypeInfo = e.getValue();
				
				String formalParamName = e.getKey().getSimpleName();
				if (exeSimpleName.equals(formalParamName)) {
					if (args.size() == 0) {
						/// Replace T() by T.class
						invocation.replace(classAccess(replacedTypeInfo.classReplacement, replacedTypeInfo.paramRefReplacement));
						break;
					} else if (args.size() == 1) {
						/// Replace T(int) by new T[int]
						invocation.replace(newArray(replacedTypeInfo.paramArrayRefReplacement, invocation.getArguments()));
						break;
					}
				}
			}
		}
		
	}

	/// Replace Array<T> methods
	@SuppressWarnings("unchecked")
	private void replaceArrayMethods(CtClass<?> ctClass, Map<CtTypeReference<?>, ReplacedTypeInfo> replacedTypeInfos) {
		for (CtInvocation<?> invocation : Query.getElements(ctClass, typeFilter(CtInvocation.class))) {
			String exeSimpleName = invocation.getExecutable().getSimpleName();
			List<CtExpression<?>> args = invocation.getArguments();
			CtExpression<?> target = invocation.getTarget();
			if (target != null) {
				/// Replace Array.set(int, T), .get(int) and length()
				int nArgs = args.size();
				CtTypeReference<?> targetFormalParamType = getFormalParamArrayType(target.getType());
				if (targetFormalParamType != null) {
					if (exeSimpleName.equals("set") && nArgs == 2) {
						invocation.replace(newAssignment(newArrayAccess(target, (CtExpression<Integer>)args.get(0)), args.get(1)));
						continue;
					} else if (exeSimpleName.equals("get") && nArgs == 1) {
						invocation.replace(newArrayAccess(target, (CtExpression<Integer>)args.get(0)));
						continue;
					} else if (exeSimpleName.equals("length") && nArgs == 0) {
						invocation.replace(newSnippet("(" + target + ").length"));
						continue;
					} else if (exeSimpleName.equals("getClass") && nArgs == 0) {
						ReplacedTypeInfo replacedTypeInfo = replacedTypeInfos.get(targetFormalParamType);
						invocation.replace(newSnippet(replacedTypeInfo.classReplacement.getName() + "[].class"));
						continue;
					} else {
						throw new RuntimeException("Unhandled method in " + Array.class.getName() + ": " + exeSimpleName);
					}
				}
			}
		}
		
	}

	/// Replace T.equals(T) by T == T (if T.isPrimitive()), and T.compareTo(T) by T < T, T >= T.... (where applicable)
	private void replaceParamMethodsAndOperators(CtClass<?> ctClass, Map<CtTypeReference<?>, ReplacedTypeInfo> replacedTypeInfos) {
		for (CtInvocation<?> invocation : Query.getElements(ctClass, typeFilter(CtInvocation.class))) {
			String exeSimpleName = invocation.getExecutable().getSimpleName();
			List<CtExpression<?>> args = invocation.getArguments();
			CtExpression<?> target = invocation.getTarget();
			if (target == null)
				continue;
			
			CtTypeReference<?> targetFormalParamType = target.getType();
			ReplacedTypeInfo replacedTypeInfo = replacedTypeInfos.get(targetFormalParamType);
			if (replacedTypeInfo == null)
				continue;
			
			int nArgs = args.size();
			
			if (exeSimpleName.equals("getClass") && nArgs == 0) {
				invocation.replace(classAccess(replacedTypeInfo.classReplacement, replacedTypeInfo.paramRefReplacement));
				continue;
			}
			
			/// Replace compareTo and equals by operators on primitives
			if (replacedTypeInfo.classReplacement.isPrimitive()) {
				if (exeSimpleName.equals("equals") && nArgs == 1) {
					invocation.replace(Code().createBinaryOperator(target, args.get(0), BinaryOperatorKind.EQ));
					continue;
				} else if (exeSimpleName.equals("compareTo") && nArgs == 1) {
					if (replaceCompareToInBinaryOperatorByDirectPrimitiveOperator(invocation))
						continue;
					
				} else if (exeSimpleName.equals("hashCode") && nArgs == 0) {
					if (isInteger(target))
						invocation.replace(target);
					else
						invocation.replace(newSnippet("(int)" + target));
					continue;
				} else {
					throw new RuntimeException("Unhandled method in " + Array.class.getName() + ": " + exeSimpleName);
				}
			}
		}
	}

	private CtTypeReference<?> getTorTArrayReplacement(CtTypeReference<?> typeRef, boolean replacePrimitivesByWrappers, Map<CtTypeReference<?>, ReplacedTypeInfo> replacedTypeInfos) {
		CtTypeReference<?> formalType = getFormalParamArrayType(typeRef);
		if (formalType != null) {
			/// Replace Array<T>
			ReplacedTypeInfo replacedTypeInfo = replacedTypeInfos.get(formalType);
			if (replacedTypeInfo != null) {
				return replacedTypeInfo.paramArrayRefReplacement;
			}
		}
		
		/// Replace T
		ReplacedTypeInfo replacedTypeInfo = replacedTypeInfos.get(typeRef);
		if (replacedTypeInfo != null) {
			return replacePrimitivesByWrappers ? 
				replacedTypeInfo.paramObjectRefReplacement : 
				replacedTypeInfo.paramRefReplacement;
		}
		
		return null;
	}

	@SuppressWarnings("unchecked")
	private boolean replaceCompareToInBinaryOperatorByDirectPrimitiveOperator(CtInvocation invocation) {
		CtElement parent = invocation.getParent();
		CtExpression target = invocation.getTarget();
		if (parent == null || target == null || !(parent instanceof CtBinaryOperator))
			return false;
		
		CtBinaryOperator parentOp = (CtBinaryOperator)parent;
		CtExpression left = parentOp.getLeftHandOperand(),
			right = parentOp.getRightHandOperand();
		
		List<CtExpression<?>> args = invocation.getArguments();
		if (args.size() != 1)
			return false;
		
		return replaceCompareToInBinaryOperatorByDirectPrimitiveOperator(invocation, target, args.get(0), (CtBinaryOperator)parent, left, right);
	}

	@SuppressWarnings("unchecked")
	private boolean replaceCompareToInBinaryOperatorByDirectPrimitiveOperator(CtInvocation invocation, CtExpression target, CtExpression argument, CtBinaryOperator parent, CtExpression left, CtExpression right) {
		if (right instanceof CtLiteral) {
			CtLiteral rightLiteral = (CtLiteral)right;
			if (new Integer(0).equals(rightLiteral.getValue())) {
				switch (parent.getKind()) {
				case EQ:
				case LE:
				case LT:
				case GE:
				case GT:
				case NE:
					parent.replace(Code().createBinaryOperator(target, argument, parent.getKind()));
					return true;
				}
				
			}
		}
		if (left instanceof CtLiteral) {
			CtLiteral leftLiteral = (CtLiteral)left;
			if (new Integer(0).equals(leftLiteral.getValue())) {
				switch (parent.getKind()) {
				case EQ:
				case LE:
				case LT:
				case GE:
				case GT:
				case NE:
					parent.replace(Code().createBinaryOperator(argument, target, parent.getKind()));
					return true;
				}
				
			}
		}
		
		return false;
	}

	private boolean matchesContract(CtInvocation<?> invocation, CtMethod<?> constructorContract) {
		if (!invocation.getExecutable().getSimpleName().equals(constructorContract.getSimpleName()))
			return false;
		
		List<CtExpression<?>> args = invocation.getArguments();
		int len = args.size();
		List<CtParameter<?>> params = constructorContract.getParameters();
		if (params.size() != len)
			return false;
		for (int i = 0; i < len; i++) {
			if (!args.get(i).getType().equals(params.get(i).getType()))
				return false;
		}
		return true;
	}

	private CtTypeReference<?> getFormalParamArrayType(CtTypeReference<?> type) {
		if (type != null && type.getQualifiedName().equals(Array.class.getName())) {
			List<CtTypeReference<?>> typeArguments = type.getActualTypeArguments();
			if (typeArguments.size() == 1) {
				return typeArguments.get(0);
			}
		}
		return null;
	}
	private CtTypeReference<?> getFormalParamClassType(CtTypeReference<?> type) {
		if (type != null && type.getQualifiedName().equals(Class.class.getName())) {
			List<CtTypeReference<?>> typeArguments = type.getActualTypeArguments();
			if (typeArguments.size() == 1) {
				return typeArguments.get(0);
			}
		}
		return null;
	}

	private List<CtMethod<?>> getAndRemoveConstructorContracts(CtClass<?> ctClass) {
		List<CtMethod<?>> constructorContracts = new ArrayList<CtMethod<?>>();
		for (CtMethod<?> method : ctClass.getMethods()) {
			ParamConstructor paramConstructorAnnotation = method.getAnnotation(ParamConstructor.class);
			if (paramConstructorAnnotation == null)
				continue;
			
			constructorContracts.add(method);
			if (!method.getModifiers().contains(ModifierKind.ABSTRACT)) {
				System.out.println("Param Constructor " + method.getSignature() + " should be abstract !"); 
			}
		}
		ctClass.getMethods().removeAll(constructorContracts);
		return constructorContracts;
	}

	private void handleProperties(CtClass<?> ctClass) {
		for (CtField<?> field : ctClass.getFields()) {
			Property propertyAnno = getAndRemoveAnnotation(field, Property.class);
			if (propertyAnno == null)
				continue;
			
			addGetterAndSetter(field, propertyAnno, ctClass);
			
			if (propertyAnno.construct()) {
				for (CtConstructor<?> ctor : ctClass.getConstructors()) {
					String argName = field.getSimpleName();
					ctor.getParameters().add(newParam(field.getType(), argName));
					List<CtStatement> statements =  ctor.getBody().getStatements();
					int i = 0, n = statements.size();
					int iInsertion = 0;
					for (; i < n; i++) {
						CtStatement st = statements.get(i);
						if (st instanceof CtInvocation) {
							CtInvocation<?> inv = (CtInvocation<?>)st;
							if (inv.getTarget() instanceof CtConstructor) {
								iInsertion = i + 1;
								break;
							}
						}
					}
					ctor.setImplicit(false);
					statements.add(iInsertion, newFieldAssignment(field, argName));
				}
			}
		}
		
	}
	
	@SuppressWarnings("unchecked")
	private void addGetterAndSetter(CtField<?> field, Property propertyAnno, CtClass<?> ctClass) {
		String capitalizedFieldName = capitalize(field.getSimpleName());
		String getterName = "get" + capitalizedFieldName,
			setterName = "set" + capitalizedFieldName;
		
		Set<ModifierKind> pub = Collections.singleton(ModifierKind.PUBLIC);
		
		CtFieldReference fieldRef = Field().createReference(field);
		CtFieldAccess fieldAccess = (CtFieldAccess)Code().createVariableAccess(fieldRef, false);
		
		if (ctClass.getMethod(getterName) == null) {
			CtMethod getter = Method().create(ctClass, pub, field.getType(), getterName, noParams, noExceptions);
			getter.setBody(newBlock(newReturn(fieldAccess)));
		}
		
		if (ctClass.getMethod(setterName, field.getType()) == null) {
			CtParameter param = newParam(field.getType(), field.getSimpleName());
			
			CtMethod setter = Method().create(ctClass, pub, field.getType(), setterName, 
					(List)Arrays.asList(param), noExceptions);
			
			setter.setBody(newBlock(newFieldAssignment(field, param.getSimpleName())));
		}
		
	}

	public Set<InstantiationResult> getResults() {
		return results ;
	}
	
}