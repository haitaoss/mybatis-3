package cn.haitaoss;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.*;

import java.io.InputStream;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-11 20:43
 *
 */
public class TestMybatisCache {
    private static SqlSessionFactory sqlSessionFactory;
    private static SqlSession session;

    public static void main(String[] args) throws Exception {
        String resource = "config/test-config.xml";
        InputStream inputStream = Resources.getResourceAsStream(resource);
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);

        test_first_cache();
        test_second_cache();
    }

    public static void test_second_cache() {
        // 开启二级缓存，关闭一级缓存，进行验证
        Configuration configuration = sqlSessionFactory.getConfiguration();
        configuration.setCacheEnabled(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        session = sqlSessionFactory.openSession();

        String statement = "cn.haitaoss.TestMapper.list";
        session.selectList(statement);
        session.selectList(statement);
        // 让二级缓存生效
        session.commit();
        // session.close();

        System.out.println("session提交了，二级缓存开始生效。。。。。。。。");
        session.selectList(statement);
        session.selectList(statement);

        System.out.println("开启新session,无所谓，二级缓存会出手");
        SqlSession sqlSession = sqlSessionFactory.openSession();
        sqlSession.selectList(statement);
        sqlSession.selectList(statement);

        System.out.println("执行，insert、update、delete 导致缓存被刷新了，会接着查数据库。。。。。。。。。。");
        // sqlSession.update("update");
        // sqlSession.delete("delete");
        sqlSession.insert("insert");

        sqlSession.selectList(statement);
        // 让二级缓存生效
        sqlSession.close();
        sqlSessionFactory.openSession()
                .selectList(statement);

    }

    public static void test_first_cache() {
        // 关闭二级缓存，开启一级缓存，进行验证
        Configuration configuration = sqlSessionFactory.getConfiguration();
        configuration.setCacheEnabled(false);
        configuration.setLocalCacheScope(LocalCacheScope.SESSION);

        session = sqlSessionFactory.openSession();
        String statement = "cn.haitaoss.TestMapper.list";
        session.selectList(statement);
        session.selectList(statement);
        // 刷新一级缓存
        // session.commit();
        // session.rollback();
        session.clearCache();

        System.out.println("缓存被刷新了，会接着查数据库。。。。。。。。。。");
        session.selectList(statement);
        session.selectList(statement);

        System.out.println("使用不同的session，需要重新查数据库。。。。。。");
        SqlSession sqlSession = sqlSessionFactory.openSession();
        sqlSession.selectList(statement);
        sqlSession.selectList(statement);
    }
}
