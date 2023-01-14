package cn.haitaoss;

import com.mysql.cj.ClientPreparedQuery;
import com.mysql.cj.Query;
import com.mysql.cj.jdbc.StatementImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.jdbc.PreparedStatementLogger;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Statement;
import java.util.Properties;

@Slf4j
@Intercepts({
        // 拦截 type 中 的方法签名是 method+args 的方法
        @Signature(type = Executor.class,
                method = "update",
                args = {MappedStatement.class, Object.class}), @Signature(type = Executor.class,
        method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}), @Signature(type = Executor.class,
        method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = StatementHandler.class,
        method = "parameterize",
        args = Statement.class)

})
public class TestPlugin implements Interceptor {
    private Properties properties = new Properties();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        Method method = invocation.getMethod();

        if (Executor.class.isAssignableFrom(method.getDeclaringClass())) {
            log.warn("调用的方法 {}", ((MappedStatement) args[0]).getId());
        }

        Object returnObject = invocation.proceed();

        if (method.getName()
                .equals("parameterize")) {
            Query query;
            if (args[0] instanceof Proxy) {
                query = ((StatementImpl) ((PreparedStatementLogger) Proxy.getInvocationHandler(
                        args[0])).getPreparedStatement()).getQuery();
            } else {
                query = ((StatementImpl) args[0]).getQuery();
            }
            if (query instanceof ClientPreparedQuery) {
                log.warn("最终执行的sql是--->{}", ((ClientPreparedQuery) query).asSql());
            } else {
                log.error("待补充");
            }
        }
        return returnObject;
    }

    @Override
    public void setProperties(Properties properties) {
        this.properties = properties;
    }
}