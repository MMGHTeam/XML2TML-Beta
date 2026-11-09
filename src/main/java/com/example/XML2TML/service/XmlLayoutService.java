package com.example.XML2TML.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.springframework.stereotype.Service;

@Service
public class XmlLayoutService {
    public record FieldRow(
            int from, int to, int length, String dataType,
            String description, String value, String nodeType,
            String branch, String format, int repeats, int recordLength
    ) {}
    public record SheetLayout(
            String name, String section, String branch,
            String condition, List<FieldRow> rows
    ) {}
    public record Layout(
            String groupId, String groupName,
            Map<String, String> groupAttributes,
            List<SheetLayout> sheets, List<String> xmlLines
    ) {}

    private final Pattern fieldPattern =
            Pattern.compile("^([9X])(?:\\((\\d+)\\))?(?:V(\\d+))?S?$");

    public Layout parse(InputStream input) throws Exception {
        byte[] bytes = input.readAllBytes();
        String xml = new String(bytes, StandardCharsets.UTF_8);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        Element group = doc.getDocumentElement();
        if (!"GROUP".equals(group.getTagName())) throw new IllegalArgumentException("Root element must be GROUP.");

        Map<String, String> attributes = attributes(group);
        List<SheetLayout> sheets = new ArrayList<>();

        for (String sectionName : List.of("TX", "RX")){
            Element section = directChild(group, sectionName);
            if (section == null) {
                continue;
            }
            processSection(sectionName, section, sheets);
        }

        List<String> xmlLines = xml.lines().toList();

        return new Layout(
            group.getAttribute("ID"),
            group.getAttribute("Name"),
            attributes,
            List.copyOf(sheets),
            xmlLines
            );
    }

    private void processSection(String sectionName, Element section, List<SheetLayout> sheets) {
        List<Element> nodes = children(section);
        int selectIndex = -1;
        for (int i = 0; i < nodes.size(); i++) {
            if ("SELECT".equals(nodes.get(i).getTagName())) {
                selectIndex = i;
                break;
            }
        }
        if (selectIndex < 0 ) {
            addLayout(sectionName, sectionName, "", "", nodes, sheets, sectionName);
            return;
        }

        Element select = nodes.get(selectIndex);
        String switchName = select.getAttribute("Switch");

        List<Element> prefix =
                new ArrayList<>(nodes.subList(0, selectIndex));
        List<Element> suffix =
                new ArrayList<>(nodes.subList(selectIndex + 1, nodes.size()));
        for (Element branch : children(select)){
            String tag = branch.getTagName();

            if (!"CASE".equals(tag) && !"CASEELSE".equals(tag)) {
                continue;
            }

            String condition = "CASEELSE".equals(tag)
                    ? "ELSE"
                    : caseCondition(branch);

            List<Element> branchNodes = new ArrayList<>(prefix);
            branchNodes.addAll(children(branch));
            branchNodes.addAll(suffix);

            String branchName = "CASEELSE".equals(tag)
                    ? "CASEELSE"
                    : "CASE" + condition;

            String sheetName = sectionName + " " + branchName;

            String details = "SELECT switch=" + switchName
                           + "; " + branchName + "; " + condition;

            addLayout(sheetName, sectionName, branchName, details,
                    branchNodes, sheets, sheetName);
        }
    }

    private void addLayout(
            String name, String section, String branch,
            String condition, List<Element> nodes, List<SheetLayout> sheets, String parentName
    ){
        List<FieldRow> rows = new ArrayList<>();

        int end = appendNodes(
                nodes, rows, 1, section, branch, sheets, parentName);

        sheets.add(new SheetLayout(
                name, section, branch, condition, List.copyOf(rows)));
    }

    private int appendNodes(
            List<Element> nodes, List<FieldRow> rows, int position,
            String section, String branch, List<SheetLayout> sheets, String parentName
    ){
        for (Element node : nodes) {
            String tag = node.getTagName();

            if ("FIELD".equals(tag)) {
                String id = node.getAttribute("ID");
                String format = node.getAttribute("Format");
                int length = formatLength(format);

                String offsetText = node.getAttribute("Offset");
                int fillerLength = 0;

                if (!offsetText.isBlank()) {
                    try {
                        fillerLength = Integer.parseInt(offsetText);
                    }   catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid offset ='" + offsetText + "' for field " + id);
                    }
                }

                if (fillerLength > 0) {
                    rows.add(new FieldRow(
                            position,
                            position + fillerLength - 1,
                            fillerLength,
                            "Alphanumeric",
                            "Filler before " + id,
                            "",
                            "FILLER",
                            branch,
                            "X(" + fillerLength + ")",
                            1,
                            0
                    ));
                    position += fillerLength;
                }

                rows.add(new FieldRow(
                        position,
                        position + length - 1,
                        length,
                        dataType(format),
                        id,
                        "",
                        "FIELD",
                        branch,
                        format,
                        1,
                        0
                ));

                position += length;

            } else if ("COLLECTION".equals(tag)) {
                int repeats = positiveInt(
                        node.getAttribute("NumSubParts"), 1,
                        "NumSubParts on " + node.getAttribute("ID"));

                String collectionId = node.getAttribute("ID");
                List<FieldRow> recordRows = new ArrayList<>();

                int recordEnd = appendNodes(
                        children(node), recordRows, 1,
                        section, branch, sheets,
                        parentName + " " + collectionId);

                int recordLength = recordEnd - 1;
                int totalLength = recordLength * repeats;

                String collectionSheetName =
                        parentName + " " + collectionId;

                sheets.add(new SheetLayout(
                        collectionSheetName,
                        section + " COLLECTION",
                        branch,
                        "Collection ID=" + collectionId
                                + "; NumSubParts=" + repeats
                                + "; record length=" + recordLength,
                        List.copyOf(recordRows)
                ));

                rows.add(new FieldRow(
                        position,
                        position + totalLength - 1,
                        totalLength,
                        "Collection",
                        collectionId,
                        "",
                        "COLLECTION",
                        branch,
                        "",
                        repeats,
                        recordLength
                ));

                position += totalLength;

            } else {
                // Supports wrapper elements containing fields.
                // SELECT branches are expanded separately above.
                position = appendNodes(
                        children(node), rows, position,
                        section, branch, sheets, parentName);
            }
        }

        return position;
    }

    private int formatLength(String format) {
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("FIELD Format is missing.");
        }

        if (format.equals("X") || format.equals("9")) return 1;

        if (format.matches("(?i)yyyyMMdd")
                || format.matches("(?i)ddMMyyyy")) {
            return 8;
        }

        Matcher matcher = fieldPattern.matcher(format);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Unsupported field format: " + format);
        }

        int base = matcher.group(2) == null
                ? 1 : Integer.parseInt(matcher.group(2));
        int decimals = matcher.group(3) == null
                ? 0 : Integer.parseInt(matcher.group(3));

        return base + decimals;
    }

    private String dataType(String format) {
        if (format.startsWith("9")) return "Numeric";
        if (format.startsWith("X")) return "Alphanumeric";
        return "Date";
    }

    private int positiveInt(String value, int fallback, String label) {
        if (value == null || value.isBlank()) return fallback;

        try {
            int result = Integer.parseInt(value);
            if (result < 1) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    label + " must be a positive integer.");
        }
    }

    private String caseCondition(Element element) {
        List<String> values = new ArrayList<>();

        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            if (attr.getNodeName().startsWith("ID")) {
                values.add(attr.getNodeName() + "="
                        + attr.getNodeValue());
            }
        }

        return String.join(", ", values);
    }

    private Map<String, String> attributes(Element element) {
        Map<String, String> result = new LinkedHashMap<>();
        NamedNodeMap attrs = element.getAttributes();

        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            result.put(attr.getNodeName(), attr.getNodeValue());
        }

        return Collections.unmodifiableMap(result);
    }

    private Element directChild(Element parent, String tag) {
        for (Element child : children(parent)) {
            if (tag.equals(child.getTagName())) return child;
        }
        return null;
    }

    private List<Element> children(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();

        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element) {
                result.add(element);
            }
        }

        return result;
    }

/*
    private List<FieldRow> readSection(Element group, String sectionName) {
        NodeList sections = group.getElementsByTagName(sectionName);
        if (sections.getLength() == 0) return List.of();
        Element section = (Element) sections.item(0);
        NodeList fields = section.getElementsByTagName("FIELD");
        List<FieldRow> result = new ArrayList<>();
        int offset = 1;
        for (int i = 0; i < fields.getLength(); i++) {
            Element field = (Element) fields.item(i);
            String id = field.getAttribute("ID");
            String format = field.getAttribute("Format");
            int length = formatLength(format);
            String dataType = format.startsWith("9") ? "Numeric" :
                (format.startsWith("X") ? "Alphanumeric" : "Date");
            result.add(new FieldRow(id, format, length, dataType, offset, offset + length - 1));
            offset += length;
        }
        return result;
    }

    static int formatLength(String format) {
        // A bare X represents one alphanumeric character.
        if ("X".equals(format)) {
            return 1;
        }

        var matcher = java.util.regex.Pattern
                .compile("^[9X]\\((\\d+)\\)(?:V(\\d+))?S?$")
                .matcher(format);

        if (matcher.matches()) {
            int base = Integer.parseInt(matcher.group(1));
            int decimals = matcher.group(2) == null
                    ? 0
                    : Integer.parseInt(matcher.group(2));

            return base + decimals;
        }

        if (format.matches("(?i)yyyyMMdd")
                || format.matches("(?i)ddMMyyyy")) {
            return 8;
        }

        throw new IllegalArgumentException(
                "Unsupported field format: " + format
        );
    }

 */
}
