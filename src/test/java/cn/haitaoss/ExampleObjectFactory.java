package cn.haitaoss;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;

import java.util.Collection;
import java.util.List;
import java.util.Properties;

@Slf4j
public class ExampleObjectFactory extends DefaultObjectFactory {
    @Override
    public <T> T create(Class<T> type) {
        log.info("ExampleObjectFactory.create--->");
        return super.create(type);
    }

    @Override
    public <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
        log.info("ExampleObjectFactory.create(args)--->");
        return super.create(type, constructorArgTypes, constructorArgs);
    }

    @Override
    public void setProperties(Properties properties) {
        log.info("ExampleObjectFactory.setProperties--->");
        super.setProperties(properties);
    }

    @Override
    public <T> boolean isCollection(Class<T> type) {
        log.info("ExampleObjectFactory.isCollection--->");
        return Collection.class.isAssignableFrom(type);
    }
}