package cn.haitaoss;

import org.apache.ibatis.annotations.CacheNamespace;
import org.apache.ibatis.annotations.CacheNamespaceRef;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-09 09:09
 *
 */
@CacheNamespaceRef(name = "cn.haitaoss.TestMapper")
public interface TestMapper extends BaseMapper {
    // 注：使用 <script> 标签，里面的内容就可以想xml的一样，支持动态标签
    @Select("<script> select * from t1 <where> and 1 = 1 </where> </script>")
    // @Select("select * from t1 <where> and 1= 1</where>") // 这是不可以的
    // @Select("select * from t1 where 1= 1")
    List<Map<String, String>> list();

    @Select(" select * from t1 where name = '${name}'")
    List<Map<String, String>> listByName(String name);

    @Select(" select * from t1 where name = #{name} ")
        //    @Select("<script>  select * from t1 where name = #{name} </script>")
    List<Map<String, String>> listByName2(String name);

    @Select(" select t1.name, 'default' AS 'a_ddre_SS' from t1 ")
        //    @Select("<script>  select * from t1 where name = #{name} </script>")
    List<Person> list3();
}
