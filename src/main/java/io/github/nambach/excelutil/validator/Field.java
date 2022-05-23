package io.github.nambach.excelutil.validator;

import io.github.nambach.excelutil.util.ReflectUtil;
import io.github.nambach.excelutil.validator.builtin.TypeValidator;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;
import java.util.function.UnaryOperator;

@Getter(AccessLevel.PACKAGE)
@Log4j2
public class Field<T> {
    private final Class<T> tClass;
    private Function<T, ?> extractor;
    private String fieldName;
    @Getter(AccessLevel.PUBLIC)
    private TypeValidator typeValidator;

    Field(Class<T> tClass) {
        this.tClass = tClass;
    }

    public Field<T> field(String fieldName) {
        this.fieldName = fieldName;
        return this;
    }

    public Field<T> customExtract(Function<T, ?> extractor) {
        this.extractor = extractor;
        return this;
    }

    public Field<T> validate(TypeValidator validator) {
        this.typeValidator = validator;
        return this;
    }

    public Field<T> validate(UnaryOperator<TypeValidator> builder) {
        this.typeValidator = builder.apply(TypeValidator.init());
        return this;
    }

    @SneakyThrows
    protected void bindField() {
        PropertyDescriptor pd = ReflectUtil.getField(fieldName, tClass);
        if (pd != null) {
            extractor = o -> {
                try {
                    return pd.getReadMethod().invoke(o);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    log.error("Cannot read field '" + fieldName + "' of class '" + tClass.getName() + "'.", e);
                    return null;
                }
            };
        } else {
            throw new Exception(String.format("Could not found field '%s' in class %s", fieldName, tClass.getName()));
        }
    }

    protected Object extract(T object) {
        return extractor.apply(object);
    }
}
