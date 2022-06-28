package io.github.nambach.excelutil.core;

import io.github.nambach.excelutil.util.ReflectUtil;
import io.github.nambach.excelutil.validator.Validator;
import io.github.nambach.excelutil.validator.builtin.TypeValidator;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.beans.PropertyDescriptor;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * A configuration object containing rules for reading
 * data from Excel table and map to DTO class.
 *
 * @param <T> DTO
 */
@Getter(AccessLevel.PACKAGE)
@Setter(AccessLevel.PACKAGE)
public class ReaderConfig<T> {

    private Class<T> tClass;
    private int titleRowIndex = -1;
    private int dataFromIndex = -1;
    private boolean earlyExit;

    private Validator<T> validator;

    // Store value as list to stack up multiple handlers on a same column
    private HandlerMap<T> handlerMap = new HandlerMap<>();

    private BiConsumer<T, ReaderRow> beforeAddItemHandle;

    ReaderConfig(Class<T> tClass) {
        this.tClass = tClass;
    }

    /**
     * Specify the DTO type.
     *
     * @param tClass DTO type
     * @param <T>    DTO
     * @return current config
     */
    public static <T> ReaderConfig<T> fromClass(Class<T> tClass) {
        return new ReaderConfig<>(tClass);
    }

    private Pointer getBaseCoordinate() {
        int baseRow = titleRowIndex >= 0 ? titleRowIndex : dataFromIndex;
        int baseCol = handlerMap.getMinIndex();
        return new Pointer(baseRow, baseCol);
    }

    //   if: dest - src = offset
    // then: dest = offset + src
    ReaderConfig<T> translate(int rowAt, int colAt) {
        Pointer base = getBaseCoordinate();
        int baseRow = base.getRow();
        int baseCol = base.getCol();

        int rowOffset = rowAt - baseRow;
        int colOffset = colAt - baseCol;

        if (rowOffset == 0 && colOffset == 0) {
            return this;
        }

        ReaderConfig<T> copy = new ReaderConfig<>(tClass);

        // translate starting point
        if (titleRowIndex >= 0) {
            copy.titleRowIndex = titleRowIndex + rowOffset;
        }
        if (dataFromIndex >= 0) {
            copy.dataFromIndex = dataFromIndex + rowOffset;
        }

        // translate handler map
        copy.handlerMap = handlerMap.makeCopy();
        copy.handlerMap.shiftIndexMap(colOffset);

        // other data
        copy.earlyExit = earlyExit;
        copy.beforeAddItemHandle = beforeAddItemHandle;
        copy.validator = validator;
        return copy;
    }

    /**
     * Specify where the title row is at (default is 0).
     * Set this value to -1 if there is no title row.
     *
     * @param index title row index
     * @return current config
     */
    public ReaderConfig<T> titleAtRow(int index) {
        this.titleRowIndex = index;
        return this;
    }

    /**
     * Specify where the data row starts from (default is 1).
     *
     * @param index first data row index
     * @return current config
     */
    public ReaderConfig<T> dataFromRow(int index) {
        this.dataFromIndex = index;
        return this;
    }

    public ReaderConfig<T> exitWhenValidationFailed(boolean b) {
        this.earlyExit = b;
        return this;
    }

    /**
     * Map the cell value at a column into target field of DTO.
     *
     * @param index     index of the column (from 0)
     * @param fieldName field name of DTO to map
     * @return current config
     */
    public ReaderConfig<T> column(int index, String fieldName) {
        TypeValidator nullValidator = null;
        return column(index, fieldName, nullValidator);
    }

    public ReaderConfig<T> column(int index, String fieldName, UnaryOperator<TypeValidator> builder) {
        TypeValidator typeValidator = builder.apply(TypeValidator.init());
        return column(index, fieldName, typeValidator);
    }

    public ReaderConfig<T> column(int index, String fieldName, TypeValidator typeValidator) {
        PropertyDescriptor pd = ReflectUtil.getField(fieldName, tClass);
        if (index >= 0 && pd != null) {
            Handler<T> handler = new Handler<T>()
                    .atColumn(index)
                    .field(fieldName)
                    .wrapHandleField(pd)
                    .validate(typeValidator);

            handlerMap.putAt(index, handler);
        }
        return this;
    }

    /**
     * Map the cell value at a column into target field of DTO.
     *
     * @param title     title of the column (provide through {@link ReaderConfig#titleAtRow(int)}
     * @param fieldName field name of DTO to map
     * @return current config
     */
    public ReaderConfig<T> column(String title, String fieldName) {
        TypeValidator nullValidator = null;
        return column(title, fieldName, nullValidator);
    }

    public ReaderConfig<T> column(String title, String fieldName, UnaryOperator<TypeValidator> builder) {
        TypeValidator typeValidator = builder.apply(TypeValidator.init());
        return column(title, fieldName, typeValidator);
    }

    public ReaderConfig<T> column(String title, String fieldName, TypeValidator typeValidator) {
        PropertyDescriptor pd = ReflectUtil.getField(fieldName, tClass);
        if (title != null && pd != null) {
            if (titleRowIndex < 0) {
                throw new RuntimeException("Index of title row must be provided through .titleAtRow(int); " +
                                           "'index=" + titleRowIndex + "' found instead.");
            }
            Handler<T> handler = new Handler<T>()
                    .atColumn(title)
                    .field(fieldName)
                    .wrapHandleField(pd)
                    .validate(typeValidator);

            handlerMap.put(title, handler);
        }
        return this;
    }

    /**
     * Specify a handler function to process data cell at a column.
     *
     * @param func a function that builds {@link Handler}
     * @return current config
     */
    public ReaderConfig<T> handler(UnaryOperator<Handler<T>> func) {
        Handler<T> handler = new Handler<>();
        func.apply(handler);
        Integer indexAt = handler.getColAt();
        Integer indexFrom = handler.getColFrom();
        String title = handler.getColTitle();
        if (indexAt == null && indexFrom == null && title == null) {
            throw new RuntimeException("Handler must have a column index with .atColumn(int) or .fromColumn(int)," +
                                       " or a column title with .atColumn(String)");
        } else if (indexAt != null) {
            handlerMap.putAt(indexAt, handler);
        } else if (indexFrom != null) {
            handlerMap.putFrom(indexFrom, handler);
        } else {
            if (titleRowIndex < 0) {
                throw new RuntimeException("Index of title row must be provided via .titleAtRow(int)");
            }
            handlerMap.put(title, handler);
        }

        return this;
    }

    public ReaderConfig<T> beforeAddingItem(BiConsumer<T, ReaderRow> handler) {
        this.beforeAddItemHandle = ReflectUtil.safeWrap(handler);
        return this;
    }

    public ReaderConfig<T> validator(Validator<T> validator) {
        this.validator = validator;
        return this;
    }

    void handleBeforeAdd(T object, ReaderRow readerRow) {
        if (beforeAddItemHandle != null) {
            beforeAddItemHandle.accept(object, readerRow);
        }
    }

    /**
     * Read data from Excel and convert to list of data.
     *
     * @param stream     byte stream
     * @param sheetIndex index of sheet to read
     * @return list of DTO
     */
    public Result<T> readSheet(InputStream stream, int sheetIndex) {
        Pointer base = getBaseCoordinate();
        try (Editor editor = new Editor(stream)) {
            return editor
                    .goToSheet(sheetIndex)
                    .goToCell(base.getRow(), base.getCol())
                    .readSection(this);
        }
    }

    /**
     * Read data from Excel and convert to list of data.
     *
     * @param stream byte stream
     * @return list of DTO
     */
    public Result<T> readSheet(InputStream stream) {
        return readSheet(stream, 0);
    }

    /**
     * Read data from Excel and convert to list of data.
     *
     * @param stream    byte stream
     * @param sheetName name of sheet to read
     * @return list of DTO
     */
    public Result<T> readSheet(InputStream stream, String sheetName) {
        Pointer base = getBaseCoordinate();
        try (Editor editor = new Editor(stream)) {
            int index = editor.getPoiWorkbook().getSheetIndex(sheetName);
            return editor
                    .goToSheet(index)
                    .goToCell(base.getRow(), base.getCol())
                    .readSection(this);
        }
    }

    /**
     * If no error, read data from Excel and convert to list of data, otherwise throw an exception.
     *
     * @param stream            byte stream
     * @param sheetIndex        index of sheet to read
     * @param exceptionFunction function that produces an exception to be thrown
     * @param <X>               type of the exception to be thrown
     * @return list of DTO
     * @throws X                    if reading has errors
     * @throws NullPointerException if reading has errors and the exception supplying function is {@code null}
     */
    public <X extends Throwable> List<T> readSheetOrThrow(
            InputStream stream,
            int sheetIndex,
            Function<List<RowError>, ? extends X> exceptionFunction
    ) throws X {
        final Result<T> unsafeResult = readSheet(stream, sheetIndex);
        if (unsafeResult.hasErrors()) {
            throw exceptionFunction.apply(unsafeResult.getErrors());
        } else {
            return unsafeResult;
        }
    }

    /**
     * If no error, read data from Excel and convert to list of data, otherwise throw an exception.
     *
     * @param stream            byte stream
     * @param exceptionFunction function that produces an exception to be thrown
     * @param <X>               type of the exception to be thrown
     * @return list of DTO
     * @throws X                    if reading has errors
     * @throws NullPointerException if reading has errors and the exception supplying function is {@code null}
     */
    public <X extends Throwable> List<T> readSheetOrThrow(
            InputStream stream,
            Function<List<RowError>, ? extends X> exceptionFunction
    ) throws X {
        return readSheetOrThrow(stream, 0, exceptionFunction);
    }

    /**
     * If no error, read data from Excel and convert to list of data, otherwise throw an exception.
     *
     * @param stream            byte stream
     * @param sheetName         name of sheet to read
     * @param exceptionFunction function that produces an exception to be thrown
     * @param <X>               type of the exception to be thrown
     * @return list of DTO
     * @throws X                    if reading has errors
     * @throws NullPointerException if reading has errors and the exception supplying function is {@code null}
     */
    public <X extends Throwable> List<T> readSheetOrThrow(
            InputStream stream,
            String sheetName,
            Function<List<RowError>, ? extends X> exceptionFunction
    ) throws X {
        final Result<T> unsafeResult = readSheet(stream, sheetName);
        if (unsafeResult.hasErrors()) {
            throw exceptionFunction.apply(unsafeResult.getErrors());
        } else {
            return unsafeResult;
        }
    }

    /**
     * Read data from multiple sheets and convert to a map of list of data.
     *
     * @param stream       byte stream
     * @param sheetIndexes indexes of sheet to read
     * @return map of list of DTO, having key map is the sheet name
     */
    public Map<String, Result<T>> readSheets(InputStream stream, int... sheetIndexes) {
        Objects.requireNonNull(sheetIndexes);

        Set<Integer> indexes = new HashSet<>();
        for (int sheetIndex : sheetIndexes) {
            indexes.add(sheetIndex);
        }

        Pointer base = getBaseCoordinate();
        try (Editor editor = new Editor(stream)) {
            Map<String, Result<T>> result = new LinkedHashMap<>();
            for (int i = 0; i < editor.getTotalSheets(); i++) {
                if (!indexes.contains(i)) {
                    continue;
                }
                Result<T> list = editor
                        .goToSheet(i)
                        .goToCell(base.getRow(), base.getCol())
                        .readSection(this);
                result.put(editor.getSheetName(), list);
            }
            return result;
        }
    }

    /**
     * Read data from multiple sheets and convert to a map of list of data.
     *
     * @param stream     byte stream
     * @param sheetNames indexes of sheet to read
     * @return map of list of DTO, having key map is the sheet name
     */
    public Map<String, Result<T>> readSheets(InputStream stream, String... sheetNames) {
        Objects.requireNonNull(sheetNames);
        Set<String> names = new HashSet<>(Arrays.asList(sheetNames));

        Pointer base = getBaseCoordinate();
        try (Editor editor = new Editor(stream)) {
            Map<String, Result<T>> result = new LinkedHashMap<>();
            for (String name : names) {
                int i = editor.getPoiWorkbook().getSheetIndex(name);
                if (i < 0) {
                    continue;
                }
                Result<T> list = editor
                        .goToSheet(i)
                        .goToCell(base.getRow(), base.getCol())
                        .readSection(this);
                result.put(editor.getSheetName(), list);
            }
            return result;
        }
    }

    /**
     * Read data from all sheets and convert to a map of list of data.
     *
     * @param stream byte stream
     * @return map of list of DTO, having key map is the sheet name
     */
    public Map<String, Result<T>> readAllSheets(InputStream stream) {
        Pointer base = getBaseCoordinate();
        try (Editor editor = new Editor(stream)) {
            Map<String, Result<T>> result = new LinkedHashMap<>();
            for (int i = 0; i < editor.getTotalSheets(); i++) {
                Result<T> list = editor
                        .goToSheet(i)
                        .goToCell(base.getRow(), base.getCol())
                        .readSection(this);
                result.put(editor.getSheetName(), list);
            }
            return result;
        }
    }
}
