package cn.haitaoss;

import org.apache.ibatis.binding.MapperProxy;
import org.apache.ibatis.binding.MapperProxyFactory;
import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.executor.BatchExecutor;
import org.apache.ibatis.executor.ReuseExecutor;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.executor.resultset.ResultSetWrapper;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.PropertyParser;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.w3c.dom.Node;

import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.sql.Statement;
import java.util.*;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2022-12-05 20:23
 *
 */
public class Test {

    private static SqlSessionFactory sqlSessionFactory;
    private static SqlSession session;

    /**
     * 任务：
     * 1. 不写xml只写接口的使用方式
     *      ok org.apache.ibatis.builder.xml.XMLConfigBuilder#parse()
     * 2. 动态注册 mapper
     *      ok
     * 3. 执行自定义sql
     *
     * 4. 打印完整sql的功能
     *     清楚插件的应用时机，就知道如何写了。
     *
     * 5. 整合到Spring
     *      ok
     * 6. 直接反射修改 SqlSource 内容，实现sql的修改
     *      行得通
     *
     * */

    public static void main(String[] args) throws Exception {
        String resource = "config/test-config.xml";
        InputStream inputStream = Resources.getResourceAsStream(resource);
        // 东西挺多的，会解析 Mapper
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream/*,"enviromentID"*/);

        test_resultSet_mapping();
        // test_batch_executor();
        // 配置信息，可以看到具体配置了啥东西,还可以动态添加 Plugin、Mapper
        //        Configuration configuration = sqlSessionFactory.getConfiguration();

        // test_first_cache();
        // test_second_cache();
        //  test_plugin();
        // test_mapper();
        // test_dynamic_reg_Mapper();
        // test_dynamic_method();
    }

    private static void test_reuse_executor() {
        SqlSession sqlSession1 = sqlSessionFactory.openSession();
        SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.REUSE);
        /**
         * 执行sql是复用一个 Statement
         * {@link ReuseExecutor#doUpdate(MappedStatement, Object)}
         * */
        sqlSession.insert("insert", "haitao_insert");
        sqlSession.insert("insert", "haitao_insert2");
        sqlSession.insert("insert", "haitao_insert3");
        /**
         * 提交才会关闭 Statement
         * */
        sqlSession.commit(); // 需要提交才会真正的执行sql
        System.out.println("=================");
        System.out.println(sqlSession1.getMapper(TestMapper.class).list());
    }

    private static void test_resultSet_mapping() {
        /**
         * {@link DefaultResultSetHandler#handleResultSets(Statement)}
         * {@link DefaultResultSetHandler#handleResultSet(ResultSetWrapper, ResultMap, List, ResultMapping)}
         * {@link DefaultResultSetHandler#handleRowValues(ResultSetWrapper, ResultMap, ResultHandler, RowBounds, ResultMapping)}
         * {@link DefaultResultSetHandler#handleRowValuesForSimpleResultMap(ResultSetWrapper, ResultMap, ResultHandler, RowBounds, ResultMapping)}
         * {@link DefaultResultSetHandler#getRowValue(ResultSetWrapper, ResultMap, String)}
         * {@link DefaultResultSetHandler#applyAutomaticMappings(ResultSetWrapper, ResultMap, MetaObject, String)}
         *      {@link DefaultResultSetHandler#createAutomaticMappings(ResultSetWrapper, ResultMap, MetaObject, String)}
         * */
        Configuration configuration = sqlSessionFactory.getConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        // configuration.setMapUnderscoreToCamelCase(false);

        SqlSession sqlSession = sqlSessionFactory.openSession();
        TestMapper mapper = sqlSession.getMapper(TestMapper.class);
        List<Person> people = mapper.list3();
        System.out.println("people = " + people);
    }

    private static void test_batch_executor() {
        SqlSession sqlSession1 = sqlSessionFactory.openSession();
        SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
        /**
         * 只是 Statement.addBatch(sql) 并没有执行sql
         * {@link BatchExecutor#doUpdate(MappedStatement, Object)}
         * */
        sqlSession.insert("insert", "haitao_insert");
        sqlSession.insert("insert", "haitao_insert2");
        sqlSession.insert("insert", "haitao_insert3");
        /**
         * 提交才会执行sql
         * */
        sqlSession.commit(); // 需要提交才会真正的执行sql
        System.out.println("=================");
        System.out.println(sqlSession1.getMapper(TestMapper.class).list());
    }

    public static void config() throws Exception {
        // 方式一：直接new
        Configuration configuration = new Configuration();
        configuration.setEnvironment(
                new Environment("default", new JdbcTransactionFactory(), new PooledDataSource())); // 伪代码而已，没有配置数据库连接信息

        // 方式二：通过读取配置文件，自动配置好各种属性
        XMLConfigBuilder xmlConfigBuilder = new XMLConfigBuilder(new FileInputStream("config/mybatis-config.xml"));
        Configuration configuration2 = xmlConfigBuilder.parse();
    }

    public static void test_dynamic_reg_Mapper() throws Exception {
        Configuration configuration = sqlSessionFactory.getConfiguration();

        // 方式一
        configuration.addMapper(TestMapper.class);
        /*// 方式二
        configuration.addMappers("cn");
        // 方式三
        String url = "cn/haitaoss/TestMapper.xml";
        FileInputStream fileInputStream = new FileInputStream(url);
        XMLMapperBuilder mapperParser = new XMLMapperBuilder(
                fileInputStream, configuration, url, configuration.getSqlFragments());
        mapperParser.parse();*/
    }


    public static void test_mapper() {
        session = sqlSessionFactory.openSession();
        /**
         * 代理对象的创建 {@link MapperProxyFactory#newInstance(SqlSession)}
         * 增强逻辑     {@link MapperProxy#invoke(Object, Method, Object[])}
         * */
        TestMapper mapper = session.getMapper(TestMapper.class);
        mapper.listByName("haitao");
        mapper.listByName2("haitao2");
    }

    public static void test_plugin() {
        session = sqlSessionFactory.openSession();

        HashMap<String, Object> map = new HashMap<>();
        map.put("name", "haitao");
        map.put("data", Arrays.asList("haitao", "haitao2"));

        session.selectList("cn.haitaoss.TestMapper.list2", map);
        session.update("update", "haitao_upate");
        session.delete("delete", "haitao_delete");
        session.insert("insert", "haitao_insert");
    }

    @org.junit.jupiter.api.Test
    public void test_Node() throws Exception {

        /*
         toInclude = source.getOwnerDocument().importNode(toInclude, true);
        // 替换原来的内容，将 source 替换成 toInclude
      source.getParentNode().replaceChild(toInclude, source);
      // todo 没看懂，这个api是做啥的
      while (toInclude.hasChildNodes()) {
        toInclude.getParentNode().insertBefore(toInclude.getFirstChild(), toInclude);
      }
      toInclude.getParentNode().removeChild(toInclude);
        * */
        String resource = "config/test-dom/demo1.xml";
        InputStream inputStream = Resources.getResourceAsStream(resource);
        XPathParser config = new XPathParser(inputStream);

        String resource2 = "config/test-dom/demo2.xml";
        InputStream inputStream2 = Resources.getResourceAsStream(resource2);
        XPathParser config2 = new XPathParser(inputStream2);

        Node source = config2.evalNode("//select")
                .getNode();
        Node toInclude = config.evalNode("//sql")
                .getNode();

        if (toInclude.getOwnerDocument() != source.getOwnerDocument()) {
            toInclude = source.getOwnerDocument()
                    .importNode(toInclude, true);
        }
        source.getParentNode()
                .replaceChild(toInclude, source);
        while (toInclude.hasChildNodes()) {
            toInclude.getParentNode()
                    .insertBefore(toInclude.getFirstChild(), toInclude);
        }
        toInclude.getParentNode()
                .removeChild(toInclude);

        System.out.println(source);
    }

    public void test_util() {
        ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
        // 递归查找包 cn 中 是 Object类型的class
        resolverUtil.find(new ResolverUtil.IsA(Object.class), "cn");
        Set<Class<? extends Class<?>>> handlerSet = resolverUtil.getClasses();


        // 占位符的解析，默认是替换 ${}
        PropertyParser.parse("name", new Properties());
    }


}
