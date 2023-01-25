package cn.haitaoss;


import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.PreparedStatement;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * @author lww
 * @date 2020-09-01 00:13
 */
@Slf4j
@Intercepts({@Signature(type = Executor.class,
        method = "update",
        args = {MappedStatement.class, Object.class}), @Signature(type = Executor.class,
        method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})})
public class PrintSqlInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        Object parameter = null;
        if (invocation.getArgs().length > 1) {
            parameter = invocation.getArgs()[1];
        }
        String sqlId = mappedStatement.getId();
        BoundSql boundSql = mappedStatement.getBoundSql(parameter);
        Configuration configuration = mappedStatement.getConfiguration();
        long start = System.currentTimeMillis();
        Object returnValue = invocation.proceed();
        long time = System.currentTimeMillis() - start;
        showSql(configuration, boundSql, time, sqlId);
        return returnValue;
    }

    private static void showSql(Configuration configuration, BoundSql boundSql, long time, String sqlId) {
        Object parameterObject = boundSql.getParameterObject();
        // 拿到 ? 占位符 对应的参数信息
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        //替换空格、换行、tab缩进等
        String sql = boundSql.getSql()
                .replaceAll("[\\s]+", " ");

        /**
         * 直接抄的源码
         * {@link DefaultParameterHandler#setParameters(PreparedStatement)}
         * */
        if (parameterMappings != null) {
            // 遍历参数信息
            for (int i = 0; i < parameterMappings.size(); i++) {
                ParameterMapping parameterMapping = parameterMappings.get(i);
                if (parameterMapping.getMode() != ParameterMode.OUT) {
                    Object value;
                    String propertyName = parameterMapping.getProperty();
                    if (boundSql.hasAdditionalParameter(propertyName)) { // issue #448 ask first for additional params
                        // 先从附加参数中取
                        value = boundSql.getAdditionalParameter(propertyName);
                    } else if (parameterObject == null) {
                        // 这个就是方法的执行参数，没有说明压根就没参数
                        value = null;
                    } else if (configuration.getTypeHandlerRegistry()
                            .hasTypeHandler(parameterObject.getClass())) {
                        // 存在对应的类型处理器，不需要管
                        value = parameterObject;
                    } else {
                        // 读取属性
                        MetaObject metaObject = configuration.newMetaObject(parameterObject);
                        value = metaObject.getValue(propertyName);
                    }
                    sql = sql.replaceFirst("\\?", getParameterValue(value));
                }
            }
        }


        logs(time, sql, sqlId);
    }

    private static String getParameterValue(Object obj) {
        String value;
        if (obj instanceof String) {
            value = "'" + obj.toString() + "'";
        } else if (obj instanceof Date) {
            DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.CHINA);
            value = "'" + formatter.format(new Date()) + "'";
        } else {
            if (obj != null) {
                value = obj.toString();
            } else {
                value = "";
            }
        }
        return value.replace("$", "\\$");
    }

    private static void logs(long time, String sql, String sqlId) {
        StringBuilder sb = new StringBuilder().append(" Time：")
                .append(time)
                .append(" ms - ID：")
                .append(sqlId)
                .append("\n")
                .append("Execute SQL：")
                .append(sql)
                .append("\n");
        log.info(sb.toString());
    }
}