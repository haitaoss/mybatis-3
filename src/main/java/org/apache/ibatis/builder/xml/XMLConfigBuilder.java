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
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.binding.MapperRegistry;
import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

    private boolean parsed;
    private final XPathParser parser;
    private String environment;
    private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

    public XMLConfigBuilder(Reader reader) {
        this(reader, null, null);
    }

    public XMLConfigBuilder(Reader reader, String environment) {
        this(reader, environment, null);
    }

    public XMLConfigBuilder(Reader reader, String environment, Properties props) {
        this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    public XMLConfigBuilder(InputStream inputStream) {
        this(inputStream, null, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment) {
        this(inputStream, environment, null);
    }

    public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
        this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
    }

    private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
        // 实例化 Configuration ，实例化的过程中会注册 很多别名
        super(new Configuration());
        ErrorContext.instance().resource("SQL Mapper Configuration");
        this.configuration.setVariables(props);
        this.parsed = false;
        this.environment = environment;
        this.parser = parser;
    }

    public Configuration parse() {
        // 已经解析过了，就直接报错，不支持二次解析
        if (parsed) {
            throw new BuilderException("Each XMLConfigBuilder can only be used once.");
        }
        // 标记解析了
        parsed = true;
        // 解析 configuration 节点
        parseConfiguration(parser.evalNode("/configuration"));
        return configuration;
    }

    private void parseConfiguration(XNode root) {
        try {
            /**
             * 解析 <properties/> 标签，解析结果设置成 {@link Configuration#variables}
             * */
            // issue #117 read properties first
            propertiesElement(root.evalNode("properties"));
            /**
             * 解析 <settings/> 内容成 Properties 对象
             * */
            Properties settings = settingsAsProperties(root.evalNode("settings"));
            loadCustomVfs(settings);
            loadCustomLogImpl(settings);
            /**
             * 解析 <typeAliases/> 注册别名到 {@link Configuration#typeAliasRegistry}
             *
             * 注：可以在类上使用 @Alias("aliasX") 指定默认别名
             * */
            typeAliasesElement(root.evalNode("typeAliases"));
            /**
             * 解析 <plugins/> 注册插件到 {@link Configuration#interceptorChain}
             * */
            pluginElement(root.evalNode("plugins"));
            /**
             * 解析 <objectFactory/> 到 {@link Configuration#objectFactory}
             * */
            objectFactoryElement(root.evalNode("objectFactory"));
            /**
             * 解析 <objectWrapperFactory/> 到 {@link Configuration#objectWrapperFactory}
             * */
            objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));
            /**
             * 解析 <reflectorFactory/> 到 {@link Configuration#reflectorFactory}
             * */
            reflectorFactoryElement(root.evalNode("reflectorFactory"));
            // 将 settings 里面的属性设置到 configuration 对象中
            settingsElement(settings);
            /**
             * 解析 <environments/> 到 {@link Configuration#environment}
             *
             * 注：只会解析使用的 environmentID,比如：
             *  1. new SqlSessionFactoryBuilder().build(inputStream,"enviromentID");
             *  2. <environments default="development"/>
             * */
            // read it after objectFactory and objectWrapperFactory issue #631
            environmentsElement(root.evalNode("environments"));
            /**
             * 解析 <databaseIdProvider/> 成 {@link Configuration#databaseId}
             *
             * 注：会根据 {@link Configuration#environment} 拿到数据源对应的 productName
             * */
            databaseIdProviderElement(root.evalNode("databaseIdProvider"));
            /**
             * 解析 <typeHandlers/> 注册到 {@link BaseBuilder#typeHandlerRegistry}
             *
             * 注：默认会在 {@link TypeHandlerRegistry#TypeHandlerRegistry(Configuration)} 实例化时 注册默认的 typeHandle
             * */
            typeHandlerElement(root.evalNode("typeHandlers"));
            /**
             * 主要是分成三种情况：
             *      - 包扫描：遍历包下的类(递归)，是接口才解析。解析是先根据类全名找xml文件，找到就进行xml的解析，然后解析类上的注解
             *      - xml解析：解析xml
             *      - 类解析：解析是先根据类全名找xml文件，找到就进行xml的解析，然后解析类上的注解
             *
             * 最直观的结果是
             *  1. 将 增删改查 映射成 MappedStatement 并注册到 {@link Configuration#mappedStatements}
             *      而 MappedStatement 包含了这两个东西（重要的）：
             *          - {@link SelectKeyGenerator} 可以用来在执行sql前和执行sql后做处理
             *          - {@link SqlSource}          这个就是具体的sql
             *
             *  2. 间接注册代理对象到Configuration中 ，{@link Configuration#mapperRegistry} 的 {@link MapperRegistry#knownMappers}
             * */
            mapperElement(root.evalNode("mappers"));
        } catch (Exception e) {
            throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
        }
    }

    private Properties settingsAsProperties(XNode context) {
        if (context == null) {
            return new Properties();
        }
        Properties props = context.getChildrenAsProperties();
        // Check that all settings are known to the configuration class
        MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
        for (Object key : props.keySet()) {
            if (!metaConfig.hasSetter(String.valueOf(key))) {
                throw new BuilderException(
                        "The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
            }
        }
        return props;
    }

    private void loadCustomVfs(Properties props) throws ClassNotFoundException {
        String value = props.getProperty("vfsImpl");
        if (value != null) {
            String[] clazzes = value.split(",");
            for (String clazz : clazzes) {
                if (!clazz.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Class<? extends VFS> vfsImpl = (Class<? extends VFS>) Resources.classForName(clazz);
                    configuration.setVfsImpl(vfsImpl);
                }
            }
        }
    }

    private void loadCustomLogImpl(Properties props) {
        Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
        configuration.setLogImpl(logImpl);
    }

    private void typeAliasesElement(XNode parent) {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                // 按照包名注册
                if ("package".equals(child.getName())) {
                    String typeAliasPackage = child.getStringAttribute("name");
                    configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
                } else {
                    // 一个个注册
                    String alias = child.getStringAttribute("alias");
                    String type = child.getStringAttribute("type");
                    try {
                        Class<?> clazz = Resources.classForName(type);
                        if (alias == null) {
                            typeAliasRegistry.registerAlias(clazz);
                        } else {
                            typeAliasRegistry.registerAlias(alias, clazz);
                        }
                    } catch (ClassNotFoundException e) {
                        throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
                    }
                }
            }
        }
    }

    private void pluginElement(XNode parent) throws Exception {
        if (parent != null) {
            // 遍历子标签
            for (XNode child : parent.getChildren()) {
                // 拿到 interceptor 的全类名
                String interceptor = child.getStringAttribute("interceptor");
                // 将子标签解析成 Properties 对象
                Properties properties = child.getChildrenAsProperties();
                // 实例化
                Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor().newInstance();
                // 将 Properties 设置 给 interceptorInstance
                interceptorInstance.setProperties(properties);
                // 添加到 configuration
                configuration.addInterceptor(interceptorInstance);
            }
        }
    }

    private void objectFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties properties = context.getChildrenAsProperties();
            ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            factory.setProperties(properties);
            configuration.setObjectFactory(factory);
        }
    }

    private void objectWrapperFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            configuration.setObjectWrapperFactory(factory);
        }
    }

    private void reflectorFactoryElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            configuration.setReflectorFactory(factory);
        }
    }

    private void propertiesElement(XNode context) throws Exception {
        if (context != null) {
            // 作为默认属性
            Properties defaults = context.getChildrenAsProperties();
            String resource = context.getStringAttribute("resource");
            String url = context.getStringAttribute("url");
            // 两个属性 只允许写一个
            if (resource != null && url != null) {
                throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
            }
            if (resource != null) {
                // 添加属性信息
                defaults.putAll(Resources.getResourceAsProperties(resource));
            } else if (url != null) {
                // 添加属性信息
                defaults.putAll(Resources.getUrlAsProperties(url));
            }
            // 变量 加到属性
            Properties vars = configuration.getVariables();
            if (vars != null) {
                // 扩展原有的属性
                defaults.putAll(vars);
            }
            parser.setVariables(defaults);
            // 将扩展的属性信息 设置到 configuration 中
            configuration.setVariables(defaults);
        }
    }

    private void settingsElement(Properties props) {
        configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
        configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
        configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
        configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
        configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
        configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
        configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
        configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
        configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
        configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
        configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
        configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
        configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
        configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
        configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
        configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
        configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
        configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
        configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
        configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
        configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
        configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
        configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
        configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
        configuration.setLogPrefix(props.getProperty("logPrefix"));
        configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
        configuration.setShrinkWhitespacesInSql(booleanValueOf(props.getProperty("shrinkWhitespacesInSql"), false));
        configuration.setArgNameBasedConstructorAutoMapping(booleanValueOf(props.getProperty("argNameBasedConstructorAutoMapping"), false));
        configuration.setDefaultSqlProviderType(resolveClass(props.getProperty("defaultSqlProviderType")));
        configuration.setNullableOnForEach(booleanValueOf(props.getProperty("nullableOnForEach"), false));
    }

    private void environmentsElement(XNode context) throws Exception {
        if (context != null) {
            if (environment == null) {
                // 拿到默认的
                environment = context.getStringAttribute("default");
            }
            // 遍历子标签
            for (XNode child : context.getChildren()) {
                String id = child.getStringAttribute("id");
                // 是 environment.equals(id)
                if (isSpecifiedEnvironment(id)) {
                    // 解析 <transactionManager/>
                    TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
                    // 解析 <dataSource/>
                    DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
                    DataSource dataSource = dsFactory.getDataSource();
                    Environment.Builder environmentBuilder = new Environment.Builder(id)
                            .transactionFactory(txFactory)
                            .dataSource(dataSource);
                    // 设置到 configuration
                    configuration.setEnvironment(environmentBuilder.build());
                    break;
                }
            }
        }
    }

    private void databaseIdProviderElement(XNode context) throws Exception {
        DatabaseIdProvider databaseIdProvider = null;
        if (context != null) {
            String type = context.getStringAttribute("type");
            // awful patch to keep backward compatibility
            if ("VENDOR".equals(type)) {
                type = "DB_VENDOR";
            }
            Properties properties = context.getChildrenAsProperties();
            /**
             * 会解析别名。
             *
             * 别名 DB_VENDOR 是在 {@link Configuration#Configuration()} 时注册的，对应的class是 {@link VendorDatabaseIdProvider}
             *
             * */
            databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor().newInstance();
            // 设置属性
            databaseIdProvider.setProperties(properties);
        }
        Environment environment = configuration.getEnvironment();
        if (environment != null && databaseIdProvider != null) {
            /**
             * 这样子 String productName = Connection.getMetaData().getDatabaseProductName() 可以拿到数据源的 供应商名称
             *
             * 如果有 property 子标签，会比较 productName.equals(name) 拿到 mysql_alias 作为最终的 databaseId
             * <databaseIdProvider type="DB_VENDOR">
             *         <property name="MySQL" value="mysql_alias"/>
             * </databaseIdProvider>
             * */
            String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
            // 设置
            configuration.setDatabaseId(databaseId);
        }
    }

    private TransactionFactory transactionManagerElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties props = context.getChildrenAsProperties();
            // 会查找是否有别名
            TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a TransactionFactory.");
    }

    private DataSourceFactory dataSourceElement(XNode context) throws Exception {
        if (context != null) {
            String type = context.getStringAttribute("type");
            Properties props = context.getChildrenAsProperties();
            // 会解析别名
            DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
            factory.setProperties(props);
            return factory;
        }
        throw new BuilderException("Environment declaration requires a DataSourceFactory.");
    }

    private void typeHandlerElement(XNode parent) {
        if (parent != null) {
            // 遍历子标签
            for (XNode child : parent.getChildren()) {
                if ("package".equals(child.getName())) {
                    String typeHandlerPackage = child.getStringAttribute("name");
                    /**
                     * 递归查找包中类型是TypeHandler的Class 进行注册
                     *
                     * 可以在自定义的TypeHandler 使用这两个注解，表示支持的转换类型是啥
                     * @MappedTypes // JavaType
                     * @MappedJdbcTypes
                     * class x implement TypeHandler{}
                     *
                     * 或者是这样子。可以通过 TypeReference<T> 的泛型类型来表示 JavaType
                     * @MappedJdbcTypes
                     * class x extends TypeReference<String> implement TypeHandler{}
                     * */
                    typeHandlerRegistry.register(typeHandlerPackage);
                } else {
                    String javaTypeName = child.getStringAttribute("javaType");
                    String jdbcTypeName = child.getStringAttribute("jdbcType");
                    String handlerTypeName = child.getStringAttribute("handler");
                    Class<?> javaTypeClass = resolveClass(javaTypeName);
                    JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
                    Class<?> typeHandlerClass = resolveClass(handlerTypeName);
                    // 一个个注册
                    if (javaTypeClass != null) {
                        if (jdbcType == null) {
                            typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
                        } else {
                            typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
                        }
                    } else {
                        typeHandlerRegistry.register(typeHandlerClass);
                    }
                }
            }
        }
    }

    private void mapperElement(XNode parent) throws Exception {
        if (parent != null) {
            for (XNode child : parent.getChildren()) {
                // 将包下的类注册成Mapper
                if ("package".equals(child.getName())) {
                    String mapperPackage = child.getStringAttribute("name");
                    // 将包下的类，注册成Mapper。注册Mapper逻辑是 先根据类全名找xml文件，找到就进行xml的解析，然后解析类上的注解
                    configuration.addMappers(mapperPackage);
                } else {
                    String resource = child.getStringAttribute("resource");
                    String url = child.getStringAttribute("url");
                    String mapperClass = child.getStringAttribute("class");
                    // 三个属性只允许设置一个
                    if (resource != null && url == null && mapperClass == null) {
                        ErrorContext.instance().resource(resource);
                        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
                            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());
                            /**
                             * 解析xml
                             * 主要是两步：
                             * 1. 将 mapper.xml 解析成对应的实例，并存到 Configuration 对象中
                             *       {@link Configuration#cacheRefMap}
                             *       {@link Configuration#incompleteCacheRefs}
                             *       {@link Configuration#caches}
                             *       {@link Configuration#resultMaps}
                             *       {@link Configuration#sqlFragments}
                             *       {@link Configuration#mappedStatements}
                             *          {@link MappedStatement#sqlSource}
                             *          {@link MappedStatement#keyGenerator}
                             *          {@link MappedStatement#cache}
                             *          ...
                             * 2. 注册Mapper {@link Configuration#addMapper(Class)} 其实就是下面的方式
                             *      2.1 判断namespace是不是一个类，存在这个类，就解析类里面的注解成对应的实例，然后添加一个 MapperProxyFactory，
                             *          用来生成接口的代理对象，在执行代理对象的方法时，就会根据方法签名查找到 MappedStatement ，然后执行其中的sql
                             * */
                            mapperParser.parse();
                        }
                    } else if (resource == null && url != null && mapperClass == null) {
                        ErrorContext.instance().resource(url);
                        try (InputStream inputStream = Resources.getUrlAsStream(url)) {
                            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
                            // 同上
                            mapperParser.parse();
                        }
                    } else if (resource == null && url == null && mapperClass != null) {
                        Class<?> mapperInterface = Resources.classForName(mapperClass);
                        /**
                         * 注册成 Mapper。同上
                         *
                         * 1. 是接口才处理
                         *      `type.isInterface()`
                         * 2. 已经添加过就报错。这是为了防止重复的添加。
                         *
                         * 3. 生成一个 MapperProxyFactory 对象，用来生成接口的代理对象
                         *      `{@link MapperRegistry#knownMappers}.put(type, new MapperProxyFactory<>(type));`
                         *
                         * 4. 构造 MapperAnnotationBuilder 用来将 接口中的注解，映射成 MappedStatement、Cache等等
                         *      `new MapperAnnotationBuilder(config, type).parse();`
                         * */
                        configuration.addMapper(mapperInterface);
                    } else {
                        throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
                    }
                }
            }
        }
    }

    private boolean isSpecifiedEnvironment(String id) {
        if (environment == null) {
            throw new BuilderException("No environment specified.");
        }
        if (id == null) {
            throw new BuilderException("Environment requires an id attribute.");
        }
        return environment.equals(id);
    }

}
