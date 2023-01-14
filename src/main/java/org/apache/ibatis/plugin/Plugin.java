/*
 *    Copyright 2009-2022 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.util.MapUtil;

/**
 * @author Clinton Begin
 */
public class Plugin implements InvocationHandler {

    private final Object target;
    private final Interceptor interceptor;
    private final Map<Class<?>, Set<Method>> signatureMap;

    private Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
        this.target = target;
        this.interceptor = interceptor;
        this.signatureMap = signatureMap;
    }

    public static Object wrap(Object target, Interceptor interceptor) {
        /**
         * 记录的是 key:类 value:method。
         * 含义是，这个类的这些方法需要被拦截(代理)
         * */
        Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);
        Class<?> type = target.getClass();

        /**
         * 递归遍历目标类的接口列表，拿到包含在签名中的接口列表信息
         * 递归拿到 type 的接口列表（递归父类，父类的父类），遍历接口列表，判断如果接口包含在 signatureMap 中，说明该接口的方法需要被增强，
         * 返回其接口包含的接口列表
         * */
        Class<?>[] interfaces = getAllInterfaces(type, signatureMap);
        // 存在需要拦截的接口，就创建代理对象
        if (interfaces.length > 0) {
            return Proxy.newProxyInstance(
                    type.getClassLoader(),
                    // 接口列表
                    interfaces,
                    /**
                     * 增强逻辑 {@link Plugin#invoke(Object, Method, Object[])}
                     * */
                    new Plugin(target, interceptor, signatureMap));
        }
        return target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
            // 通过类型 获取在拦截器的signatureMap中的方法
            Set<Method> methods = signatureMap.get(method.getDeclaringClass());

            // 包含当前调用的方法
            if (methods != null && methods.contains(method)) {
                // 执行拦截器的拦截方法
                return interceptor.intercept(new Invocation(target, method, args));
            }
            // 放行
            return method.invoke(target, args);
        } catch (Exception e) {
            throw ExceptionUtil.unwrapThrowable(e);
        }
    }

    private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
        Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);
        // issue #251
        if (interceptsAnnotation == null) {
            throw new PluginException(
                    "No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());
        }
        Signature[] sigs = interceptsAnnotation.value();
        /**
         * 记录的是 key:类 value:method。
         * 含义是，这个类的这些方法需要被拦截(代理)
         * */
        Map<Class<?>, Set<Method>> signatureMap = new HashMap<>();
        for (Signature sig : sigs) {
            // 从 signatureMap 根据 sig.type() 拿到 value，没有就初始化为 new HashSet<>()
            Set<Method> methods = MapUtil.computeIfAbsent(signatureMap, sig.type(), k -> new HashSet<>());
            try {
                // 根据方法名加参数列表 获取到 Method 实例
                Method method = sig.type().getMethod(sig.method(), sig.args());
                // 添加,表示这个方法需要被拦截
                methods.add(method);
            } catch (NoSuchMethodException e) {
                throw new PluginException(
                        "Could not find method on " + sig.type() + " named " + sig.method() + ". Cause: " + e, e);
            }
        }
        // 返回
        return signatureMap;
    }

    private static Class<?>[] getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) {
        Set<Class<?>> interfaces = new HashSet<>();
        while (type != null) {
            // 遍历接口列表
            for (Class<?> c : type.getInterfaces()) {
                // 签名中包含接口类型
                if (signatureMap.containsKey(c)) {
                    // 记录一下
                    interfaces.add(c);
                }
            }
            // 父类
            type = type.getSuperclass();
        }
        return interfaces.toArray(new Class<?>[0]);
    }

}
