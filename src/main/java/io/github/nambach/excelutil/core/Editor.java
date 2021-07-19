package io.github.nambach.excelutil.core;

import io.github.nambach.excelutil.model.Raw;
import io.github.nambach.excelutil.style.Style;
import lombok.SneakyThrows;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Editor extends BaseEditor {
    private final Workbook workbook;
    private final BaseWriter writer;
    private final BaseReader reader;
    private final Pointer pointer = new Pointer();
    private final Pointer pivot = new Pointer();
    private Sheet currentSheet;
    private Style tempStyle;

    public Editor() {
        this(null);
    }

    @SneakyThrows
    public Editor(InputStream stream) {
        XSSFWorkbook workbook = stream == null ? new XSSFWorkbook() : new XSSFWorkbook(stream);
        this.workbook = workbook;
        this.writer = new BaseWriter(workbook);
        this.reader = new BaseReader();

        // set active sheet as current
        if (stream != null && workbook.getNumberOfSheets() != 0) {
            int index = workbook.getActiveSheetIndex();
            currentSheet = workbook.getSheetAt(index);
        }
    }

    // Sheet navigation
    private void resetPointer() {
        pointer.update(getNextRowIndex(), 0);
        pivot.sync(pointer);
    }

    private int getNextRowIndex() {
        return currentSheet.getLastRowNum() + 1;
    }

    public Editor goToSheet(int index) {
        if (workbook.getNumberOfSheets() == 0) {
            goToSheet("Sheet 1");
        } else if (index < 0) {
            currentSheet = workbook.getSheetAt(0);
        } else if (index + 1 > workbook.getNumberOfSheets()) {
            currentSheet = workbook.getSheetAt(workbook.getNumberOfSheets() - 1);
        } else {
            currentSheet = workbook.getSheetAt(index);
        }
        resetPointer();
        return this;
    }

    public Editor goToSheet(String sheetName) {
        if (workbook.getSheet(sheetName) != null) {
            currentSheet = workbook.getSheet(sheetName);
        } else {
            currentSheet = workbook.createSheet(sheetName);
        }
        resetPointer();
        return this;
    }

    public String getSheetName() {
        return currentSheet == null ? null : currentSheet.getSheetName();
    }

    public int getTotalSheets() {
        return workbook.getNumberOfSheets();
    }

    // Cell navigation
    public Editor goToCell(String address) {
        pointer.update(address);
        pivot.sync(pointer);
        return this;
    }

    public Editor goToCell(int row, int col) {
        pointer.update(row, col);
        pivot.sync(pointer);
        return this;
    }

    public Editor moveRight() {
        pointer.jumpRight(pivot);
        pivot.sync(pointer);
        return this;
    }

    public Editor moveRight(int steps) {
        if (steps > 0) {
            moveRight();
            pointer.moveRight(steps - 1);
            pivot.sync(pointer);
        }
        return this;
    }

    public Editor moveDown() {
        pointer.jumpDown(pivot);
        pivot.sync(pointer);
        return this;
    }

    public Editor moveDown(int steps) {
        if (steps > 0) {
            moveDown();
            pointer.moveDown(steps - 1);
            pivot.sync(pointer);
        }
        return this;
    }

    public Editor enter() {
        pointer.update(getNextRowIndex(), 0);
        pivot.sync(pointer);
        return this;
    }

    public Editor enter(int steps) {
        if (steps > 0) {
            enter();
            pointer.moveDown(steps - 1);
            pivot.sync(pointer);
        }
        return this;
    }

    // For writing
    public Editor useStyle(Style style) {
        this.tempStyle = style;
        return this;
    }

    public Editor writeCell(Function<WriterCell, WriterCell> f) {
        WriterCell cell = new WriterCell(pointer, tempStyle);
        f.apply(cell);
        writer.writeCellInfo(currentSheet, cell);

        // update pivot
        pivot.moveRight(cell.getColSpan() - 1);
        pivot.moveDown(cell.getRowSpan() - 1);
        return this;
    }

    public Editor writeTemplate(Template template) {
        writer.writeTemplate(currentSheet, template);

        // update pointer
        int minRow = template.getCells().stream().map(WriterCell::getRowAt)
                             .reduce(Math::min).orElse(0);
        int minCol = template.getCells().stream().map(WriterCell::getColAt)
                             .reduce(Math::min).orElse(0);
        pointer.update(minRow, minCol);

        // update pivot
        int maxRow = template.getCells().stream().map(c -> c.getRowAt() + c.getRowSpan() - 1)
                             .reduce(Math::max).orElse(0);
        int maxCol = template.getCells().stream().map(c -> c.getColAt() + c.getColSpan() - 1)
                             .reduce(Math::max).orElse(0);
        pivot.update(maxRow, maxCol);
        return this;
    }

    public <T> Editor writeData(DataTemplate<T> template, Collection<T> data) {
        if (data == null) {
            data = Collections.emptyList();
        }
        writer.writeData(currentSheet, template, data, pointer.getRow(), pointer.getCol());

        // update pivot
        pivot.moveRight(template.getMappers().size() - 1);
        int headerRows = template.isNoHeader() ? 0 : 1;
        pivot.moveDown(data.size() - 1 + headerRows);

        return this;
    }

    public ByteArrayInputStream exportToFile() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
            out.close();
            System.out.println("Excel file was successfully saved.");
            return in;
        } catch (Exception e) {
            System.out.println("There some error writing excel file:");
            e.printStackTrace();
            return null;
        }
    }

    // For reading

    public String readString() {
        return getCellAt(currentSheet, pointer).readString();
    }

    public Date readDate() {
        return getCellAt(currentSheet, pointer).readDate();
    }

    public LocalDateTime readLocalDateTime() {
        return getCellAt(currentSheet, pointer).readLocalDateTime();
    }

    public Double readDouble() {
        return getCellAt(currentSheet, pointer).readDouble();
    }

    public Float readFloat() {
        return getCellAt(currentSheet, pointer).readFloat();
    }

    public Long readLong() {
        return getCellAt(currentSheet, pointer).readLong();
    }

    public Integer readInt() {
        return getCellAt(currentSheet, pointer).readInt();
    }

    public Boolean readBoolean() {
        return getCellAt(currentSheet, pointer).readBoolean();
    }

    public <T> List<T> readSection(ReaderConfig<T> config) {
        List<Raw<T>> rawList = reader.readSheet(currentSheet, config, pointer.getRow(), pointer.getCol());
        return rawList.stream().map(Raw::getData).collect(Collectors.toList());
    }

    public <T> List<Raw<T>> readSectionRaw(ReaderConfig<T> config) {
        return reader.readSheet(currentSheet, config, pointer.getRow(), pointer.getCol());
    }

    public Editor configSheet(Function<Config, Config> f) {
        f.apply(new Config(this));
        return this;
    }

    public static class Config {
        private final Editor editor;

        public Config(Editor editor) {
            this.editor = editor;
        }

        public Config freeze(int rows, int cols) {
            if (rows < 0) {
                rows = 0;
            }
            if (cols < 0) {
                cols = 0;
            }
            Sheet sheet = editor.currentSheet;
            sheet.createFreezePane(cols, rows);
            return this;
        }
    }
}
