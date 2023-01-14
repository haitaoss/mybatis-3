package cn.haitaoss;

import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-01-09 09:09
 *
 */
public interface TestMapper2 {
    @Select("select 'x' from dual")
    List<Map<String, String>> list();
}
