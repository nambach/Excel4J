package io.github.nambach.excelutil.validator.builtin;

import io.github.nambach.excelutil.validator.Constraint;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Getter(AccessLevel.PACKAGE)
public class Validator {

    static final Constraint NotNull = new Constraint("[Validator] not null",
                                                     Objects::nonNull,
                                                     "Value must not be null.");

    static final ArrayList<Constraint> BasedConstraints = new ArrayList<Constraint>() {{
        add(NotNull);
    }};

    protected Constraint.Set constraints = new Constraint.Set();

    Validator() {
    }

    public static Validator init() {
        return new Validator();
    }

    public static StringValidator string() {
        return new StringValidator();
    }

    public static IntegerValidator integer() {
        return new IntegerValidator();
    }

    public static DecimalValidator decimal() {
        return new DecimalValidator();
    }

    protected boolean containOnlyBased() {
        return constraints.stream().filter(c -> !BasedConstraints.contains(c)).count() == 0;
    }

    public String validate(Object value) {
        return constraints.stream()
                          .filter(constraint -> constraint.notOk(value))
                          .map(Constraint::getMessage)
                          .findFirst().orElse(null);
    }

    public List<String> validateAllConstraints(Object value) {
        return constraints.stream()
                          .filter(constraint -> constraint.notOk(value))
                          .map(Constraint::getMessage)
                          .collect(Collectors.toList());
    }

    public Validator notNull() {
        constraints.add(NotNull);
        return this;
    }

    public Validator notNull(String message) {
        constraints.add(NotNull.withMessage(message));
        return this;
    }

    protected void copy(Validator other) {
        this.constraints.addAll(other.constraints);
    }

    public StringValidator isString() {
        StringValidator validator = new StringValidator();
        validator.copy(this);
        return validator;
    }

    public IntegerValidator isInteger() {
        IntegerValidator validator = new IntegerValidator();
        validator.copy(this);
        return validator;
    }

    public DecimalValidator isDecimal() {
        DecimalValidator validator = new DecimalValidator();
        validator.copy(this);
        return validator;
    }
}