package cn.haitaoss;

import org.apache.ibatis.binding.BoundAuthorMapper;
import org.apache.ibatis.binding.BoundBlogMapper;
import org.apache.ibatis.builder.xml.XMLConfigBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSourceFactory;
import org.apache.ibatis.domain.blog.Author;
import org.apache.ibatis.domain.blog.Blog;
import org.apache.ibatis.domain.blog.Post;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import java.io.FileInputStream;
import java.util.Properties;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-11 22:13
 *
 */
public class Test_Configuration {
    public static void test_auto_config() throws Exception {
        // 方式二：通过读取配置文件，自动配置好各种属性
        XMLConfigBuilder xmlConfigBuilder = new XMLConfigBuilder(new FileInputStream("config/mybatis-config.xml"));
        Configuration configuration = xmlConfigBuilder.parse();
    }

    public static void test_manual_config() throws Exception {
        Properties props = new Properties();
        props.put("username", "root");
        props.put("password", "root");
        props.put("driver", "com.mysql.cj.jdbc.Driver");
        props.put("url",
                "jdbc:mysql://localhost:3306/d1?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowMultiQueries=true");

        UnpooledDataSourceFactory unpooledDataSourceFactory = new UnpooledDataSourceFactory();
        unpooledDataSourceFactory.setProperties(props);

        Environment environment = new Environment("development", new JdbcTransactionFactory(),
                unpooledDataSourceFactory.getDataSource()
        );

        Configuration configuration = new Configuration(environment);
        configuration.setLazyLoadingEnabled(true);
        // 注册别名
        configuration.getTypeAliasRegistry()
                .registerAlias(Blog.class);
        configuration.getTypeAliasRegistry()
                .registerAlias(Post.class);
        configuration.getTypeAliasRegistry()
                .registerAlias(Author.class);
        // 添加Mapper
        configuration.addMapper(BoundBlogMapper.class);
        configuration.addMapper(BoundAuthorMapper.class);
        // 添加插件
        configuration.addInterceptor(new TestPlugin());

        SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
        // 获取 SqlSessionFactory
        SqlSessionFactory factory = builder.build(configuration);
    }
}
