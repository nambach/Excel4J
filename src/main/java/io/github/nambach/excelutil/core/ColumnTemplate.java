package io.github.nambach.excelutil.core;

import io.github.nambach.excelutil.util.ListUtil;
import io.github.nambach.excelutil.util.ReflectUtil;
import io.github.nambach.excelutil.util.TextUtil;
import lombok.EqualsAndHashCode;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
public class ColumnTemplate<T> extends ArrayList<ColumnMapper<T>> {
    protected Class<T> tClass;

    protected ColumnTemplate(Class<T> tClass) {
        this.tClass = tClass;
    }

    /**
     * Filter the mapping rules to a new template.
     *
     * @param condition a {@link Predicate} that filters out mapping rules that need to keep
     * @return a copied template (which is not modified the original)
     */
    public ColumnTemplate<T> filter(Predicate<ColumnMapper<T>> condition) {
        return internalFilter(new ColumnTemplate<>(tClass), condition);
    }

    protected ColumnTemplate<T> internalFilter(ColumnTemplate<T> clone, Predicate<ColumnMapper<T>> condition) {
        Objects.requireNonNull(condition);
        clone.removeIf(condition.negate());
        return clone;
    }

    /**
     * Combining with other to create a new template that includes all mapping rules of both templates.
     *
     * @param other other {@link ColumnTemplate}
     * @return a copied template (which is not modified the original)
     */
    public ColumnTemplate<T> concat(ColumnTemplate<T> other) {
        if (other == null || other == this) {
            return this;
        }
        ColumnTemplate<T> clone = new ColumnTemplate<>(tClass);
        clone.addAll(this);
        clone.addAll(other);
        return clone;
    }

    /**
     * Configure to map some fields of DTO.
     *
     * @param fieldNames an array of field names
     * @return current template
     */
    public ColumnTemplate<T> includeFields(String... fieldNames) {
        List<ColumnMapper<T>> list = Arrays
                .stream(fieldNames)
                .map(s -> new ColumnMapper<T>().field(s))
                .filter(this::validateMapper)
                .filter(mapper -> this.stream().noneMatch(current -> Objects.equals(current.getFieldName(), mapper.getFieldName())))
                .collect(Collectors.toList());
        this.addAll(list);
        return this;
    }

    /**
     * Configure to map all fields of DTO.
     *
     * @return current template
     */
    public ColumnTemplate<T> includeAllFields() {
        Field[] fields = tClass.getDeclaredFields();
        String[] fieldNames = Arrays.stream(fields).map(Field::getName).toArray(String[]::new);
        return this.includeFields(fieldNames);
    }

    /**
     * Filter out some fields of DTO that don't need to export.
     *
     * @param fieldNames an array of field names
     * @return current template
     */
    public ColumnTemplate<T> excludeFields(String... fieldNames) {
        List<String> fields = ListUtil.fromArray(fieldNames);
        this.removeIf(m -> fields.contains(m.getFieldName()));
        return this;
    }

    /**
     * Configure a {@link ColumnMapper} that define mapping rule to extract DTO data into Excel column.
     *
     * @param builder a function that builds {@link ColumnMapper}
     * @return current template
     */
    public ColumnTemplate<T> column(UnaryOperator<ColumnMapper<T>> builder) {
        ColumnMapper<T> mapper = builder.apply(new ColumnMapper<>());
        if (validateMapper(mapper)) {
            this.add(mapper);
        }
        return this;
    }

    protected boolean validateMapper(ColumnMapper<T> mapper) {
        String fieldName = mapper.getFieldName();
        String title = mapper.getDisplayName();

        if (mapper.getMapper() != null) {
            if (title == null) {
                mapper.setDisplayName(String.format("Column %s", fieldName));
            }
        } else if (fieldName != null) {
            PropertyDescriptor pd = ReflectUtil.getField(fieldName, tClass);
            if (pd == null) {
                return false;
            }

            // Generate title
            if (title == null) {
                mapper.setDisplayName(TextUtil.splitCamelCase(fieldName));
            }
            // Create function
            Function<T, ?> getter = obj -> {
                try {
                    return pd.getReadMethod().invoke(obj);
                } catch (Exception e) {
                    return null;
                }
            };
            mapper.setMapper(getter);
        } else {
            return false;
        }
        return true;
    }

    protected boolean hasDeepLevel() {
        return this.stream().anyMatch(ColumnMapper::isListField);
    }

    protected ColumnMapper<T> getDeepField() {
        return this.stream().filter(ColumnMapper::isListField).findFirst().orElse(null);
    }
}
