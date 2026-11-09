
package com.example.XML2TML.ui;

import java.io.ByteArrayInputStream;

import com.example.XML2TML.service.ExcelExportService;
import com.example.XML2TML.service.XmlLayoutService;
import com.example.XML2TML.service.XmlLayoutService.FieldRow;
import com.example.XML2TML.service.XmlLayoutService.Layout;
import com.example.XML2TML.service.XmlLayoutService.SheetLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.PermitAll;

@Route("")
@PageTitle("XML to TML")
@PermitAll
public class MainView extends VerticalLayout {

    private final XmlLayoutService parser;
    private final ExcelExportService exporter;
    private final AuthenticationContext authenticationContext;

    private final VerticalLayout preview = new VerticalLayout();
    private final VerticalLayout downloads = new VerticalLayout();

    public MainView(
            XmlLayoutService parser,
            ExcelExportService exporter,
            AuthenticationContext authenticationContext
    ) {
        this.parser = parser;
        this.exporter = exporter;
        this.authenticationContext = authenticationContext;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H1 title = new H1("XML to TML");
        Paragraph intro = new Paragraph(
                "Upload a GROUP XML definition to generate Excel worksheets "
                        + "for request layouts, response branches and collections.");

        Button logout = new Button("Sign out",
                event -> authenticationContext.logout());

        HorizontalLayout top = new HorizontalLayout(
                new VerticalLayout(title, intro), logout);
        top.setWidthFull();
        top.setAlignItems(Alignment.CENTER);
        top.setFlexGrow(1, top.getComponentAt(0));

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);

        upload.setAcceptedFileTypes(".xml", "text/xml", "application/xml");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(2 * 1024 * 1024);
        upload.setDropLabel(new Span("Drop XML file here or browse"));

        upload.addSucceededListener(event -> {
            try {
                Layout layout = parser.parse(buffer.getInputStream());

                byte[] workbook = exporter.createWorkbook(layout);
                showPreview(layout);
                showDownload(layout, workbook);

                Notification.show(
                        "XML parsed. Generated " + layout.sheets().size()
                                + " layout worksheets.");
            } catch (Exception ex) {
                preview.removeAll();
                downloads.removeAll();

                Notification.show(
                        "Could not process XML: " + ex.getMessage(),
                        7000,
                        Notification.Position.MIDDLE);
            }
        });

        upload.addFileRejectedListener(event ->
                Notification.show(
                        "File rejected: " + event.getErrorMessage(),
                        5000,
                        Notification.Position.MIDDLE));

        add(
                top,
                new H2("1. Upload XML"),
                upload,
                downloads,
                new H2("2. Layout preview"),
                preview
        );
    }

    private void showPreview(Layout layout) {
        preview.removeAll();

        preview.add(new H3(
                layout.groupId() + " — " + layout.groupName()));

        for (SheetLayout sheet : layout.sheets()) {
            preview.add(new H4(
                    sheet.name() + " (" + sheet.rows().size() + " rows)"));

            if (!sheet.condition().isBlank()) {
                preview.add(new Paragraph(sheet.condition()));
            }

            Grid<FieldRow> grid = new Grid<>();
            grid.addColumn(FieldRow::from).setHeader("From");
            grid.addColumn(FieldRow::to).setHeader("To");
            grid.addColumn(FieldRow::length).setHeader("Length");
            grid.addColumn(FieldRow::dataType).setHeader("Data Type");
            grid.addColumn(FieldRow::description).setHeader("Description");
            grid.addColumn(FieldRow::format).setHeader("Format");
            grid.addColumn(FieldRow::repeats).setHeader("Repeats");
            grid.addColumn(FieldRow::recordLength)
                    .setHeader("Record Length");

            grid.setItems(sheet.rows());
            grid.setWidthFull();
            grid.setAllRowsVisible(true);

            preview.add(grid);
        }
    }

    private void showDownload(Layout layout, byte[] workbook) {
        downloads.removeAll();

        String id = layout.groupId().replaceAll(
                "[^A-Za-z0-9._-]", "_");

        StreamResource resource = new StreamResource(
                "TML-" + id + ".xlsx",
                () -> new ByteArrayInputStream(workbook));

        Anchor download = new Anchor(resource, "Download Excel workbook");
        download.getElement().setAttribute("download", true);

        downloads.add(download);
    }
}
