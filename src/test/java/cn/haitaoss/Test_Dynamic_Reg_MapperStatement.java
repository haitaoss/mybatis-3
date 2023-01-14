package cn.haitaoss;

import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-12 21:23
 *
 */
public class Test_Dynamic_Reg_MapperStatement {
    public static void test_dynamic_method() throws Exception {
        InputStream inputStream = Resources.getResourceAsStream("config/test-config.xml");
        // 东西挺多的，会解析 Mapper
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream/*,"enviromentID"*/);

        Class<TestMapper> type = TestMapper.class;

        if (!BaseMapper.class.isAssignableFrom(type)) {
            return;
        }
        String resource = type.getName()
                .replace('.', '/') + ".java (best guess)";
        Configuration configuration = sqlSessionFactory.getConfiguration();

        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, resource);
        assistant.setCurrentNamespace(type.getName());

        Method method = type.getMethod("haitaoList");
        final String mappedStatementId = type.getName() + "." + method.getName();


        Class<MapperMethod.ParamMap> parameterType = MapperMethod.ParamMap.class;

        // 生成 SqlSource
        LanguageDriver langDriver = configuration.getDefaultScriptingLanguageInstance();
        SqlSource sqlSource = langDriver.createSqlSource(configuration,
                "<script> select * from t1 <where> and 1 = 1 </where> </script>", parameterType
        );

        // 生成 ResultMap
        StringBuilder suffix = new StringBuilder();
        for (Class<?> c : method.getParameterTypes()) {
            suffix.append("-");
            suffix.append(c.getSimpleName());
        }
        if (suffix.length() < 1) {
            suffix.append("-void");
        }
        String resultMapId = type.getName() + "." + method.getName() + suffix;

        Class<Map> resultType = Map.class;
        ResultMap resultMap = new ResultMap.Builder(
                configuration, resultMapId, resultType, new ArrayList<>(), null).discriminator(null)
                .build();
        configuration.addResultMap(resultMap);


        // 生成 MappedStatement
        assistant.addMappedStatement(mappedStatementId, sqlSource, StatementType.PREPARED, SqlCommandType.SELECT, null,
                null,
                // ParameterMapID
                null, parameterType, resultMapId, resultType, configuration.getDefaultResultSetType(), false, true,
                // TODO gcode issue #577
                false, NoKeyGenerator.INSTANCE, null, null, configuration.getDatabaseId(), langDriver,
                // ResultSets
                null
        );

        SqlSession sqlSession = sqlSessionFactory.openSession();
        TestMapper mapper = sqlSession.getMapper(TestMapper.class);
        System.out.println(mapper.haitaoList());
    }
}
