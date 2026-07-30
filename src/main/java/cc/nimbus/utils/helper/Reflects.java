package cc.nimbus.utils.helper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Reflects {
    private static final MethodHandles.Lookup LOOKUP=MethodHandles.lookup();
    private static final Map<String,Class<?>> CLASSES=new ConcurrentHashMap<>();
    private static final Map<Field,MethodHandle> GETTERS=new ConcurrentHashMap<>(),SETTERS=new ConcurrentHashMap<>();
    private static final Map<Method,MethodHandle> METHODS=new ConcurrentHashMap<>();
    private Reflects(){}
    public static Class<?> classForName(String name){return CLASSES.computeIfAbsent(name,key->{for(ClassLoader loader:new ClassLoader[]{Thread.currentThread().getContextClassLoader(),Reflects.class.getClassLoader(),ClassLoader.getSystemClassLoader()})try{return Class.forName(key,false,loader);}catch(ClassNotFoundException ignored){}throw new IllegalArgumentException("Class not found: "+key);});}
    public static Class<?> classForNameOrNull(String name){try{return classForName(name);}catch(RuntimeException ignored){return null;}}
    public static MethodHandle methodHandle(Method method){return METHODS.computeIfAbsent(method,value->{try{value.setAccessible(true);return LOOKUP.unreflect(value);}catch(IllegalAccessException error){throw new IllegalStateException(error);}});}
    public static <T>T newInstance(Class<T> type){try{Constructor<T> constructor=type.getDeclaredConstructor();constructor.setAccessible(true);return constructor.newInstance();}catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot instantiate "+type.getName(),error);}}
    public static Object newInstance(Class<?> type,Class<?>[] parameterTypes,Object[] args){try{Constructor<?> constructor=type.getDeclaredConstructor(parameterTypes);constructor.setAccessible(true);return constructor.newInstance(args);}catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot instantiate "+type.getName(),error);}}
    public static Field findField(Class<?> type,String name){for(Class<?> current=type;current!=null;current=current.getSuperclass())try{Field field=current.getDeclaredField(name);field.setAccessible(true);return field;}catch(NoSuchFieldException ignored){}throw new IllegalArgumentException("Field not found: "+type.getName()+'.'+name);}
    private static MethodHandle getter(Field field){return GETTERS.computeIfAbsent(field,value->{try{return LOOKUP.unreflectGetter(value);}catch(IllegalAccessException error){throw new IllegalStateException(error);}});}
    private static MethodHandle setter(Field field){return SETTERS.computeIfAbsent(field,value->{try{return LOOKUP.unreflectSetter(value);}catch(IllegalAccessException error){throw new IllegalStateException(error);}});}
    public static Object getField(Object target,String name){try{return getter(findField(target.getClass(),name)).invoke(target);}catch(Throwable error){throw new IllegalStateException(error);}}
    public static void setField(Object target,String name,Object value){try{setter(findField(target.getClass(),name)).invoke(target,value);}catch(Throwable error){throw new IllegalStateException(error);}}
    public static Object staticGetField(Class<?> type,String name){try{return getter(findField(type,name)).invoke();}catch(Throwable error){throw new IllegalStateException(error);}}
    public static void staticSetField(Class<?> type,String name,Object value){try{setter(findField(type,name)).invoke(value);}catch(Throwable error){throw new IllegalStateException(error);}}
    public static void setFieldValue(Object target,Object value,String...names){RuntimeException last=null;for(String name:names)try{setField(target,name,value);return;}catch(RuntimeException error){last=error;}throw new IllegalStateException("No matching field: "+Arrays.toString(names),last);}
    @SuppressWarnings("unchecked") public static <T>T getFieldValue(Object target,String...names){RuntimeException last=null;for(String name:names)try{return(T)getField(target,name);}catch(RuntimeException error){last=error;}throw new IllegalStateException("No matching field: "+Arrays.toString(names),last);}
    public static Method findMethod(Class<?> type,String name,Class<?>...parameterTypes){for(Class<?> current=type;current!=null;current=current.getSuperclass())try{Method method=current.getDeclaredMethod(name,parameterTypes);method.setAccessible(true);return method;}catch(NoSuchMethodException ignored){}throw new IllegalArgumentException("Method not found: "+type.getName()+'.'+name);}
    public static Method getMethod(Class<?> type,String[] names,Class<?>...parameterTypes){for(String name:names)try{return findMethod(type,name,parameterTypes);}catch(IllegalArgumentException ignored){}return null;}
    public static Object invokeMethod(Object target,String[] names,Class<?>[] types,Object...args){Method method=getMethod(target.getClass(),names,types);if(method==null)throw new IllegalStateException("No matching method: "+Arrays.toString(names));return invokeHandle(methodHandle(method),target,args);}
    public static Object invoke(Object target,String name,Class<?>[] types,Object[] args){return invokeHandle(methodHandle(findMethod(target.getClass(),name,types)),target,args);}
    public static Object staticInvoke(Class<?> type,String name,Class<?>[] types,Object[] args){try{return methodHandle(findMethod(type,name,types)).invokeWithArguments(args);}catch(Throwable error){throw new IllegalStateException(error);}}
    private static Object invokeHandle(MethodHandle handle,Object target,Object[] args){try{Object[] values=new Object[args.length+1];values[0]=target;System.arraycopy(args,0,values,1,args.length);return handle.invokeWithArguments(values);}catch(Throwable error){throw new IllegalStateException(error);}}
}
