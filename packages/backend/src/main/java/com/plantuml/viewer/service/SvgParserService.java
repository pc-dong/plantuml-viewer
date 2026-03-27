package com.plantuml.viewer.service;

import com.plantuml.viewer.model.DiagramModel;
import com.plantuml.viewer.model.Element;
import com.plantuml.viewer.model.Relation;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SvgParserService {

    private static final Pattern TRANSLATE_PATTERN = Pattern.compile("translate\\(\\s*([\\d.-]+)\\s*,\\s*([\\d.-]+)\\s*\\)");
    private static final double MAX_ENDPOINT_DISTANCE = 500.0;

    public DiagramModel parse(String svg, String diagramType) {
        DiagramModel model = new DiagramModel();
        model.setType(diagramType);

        List<Element> elements = new ArrayList<>();
        List<Relation> relations = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(svg)));

            // Find the root <g> element inside <svg>
            org.w3c.dom.Element root = findFirstDomElement(document.getDocumentElement(), "g");
            if (root == null) {
                model.setElements(elements);
                model.setRelations(relations);
                return model;
            }

            // Process all top-level <g> children
            NodeList children = root.getChildNodes();
            List<Node> elementNodes = new ArrayList<>();
            List<Node> relationNodes = new ArrayList<>();

            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof org.w3c.dom.Element gChild) {
                    String id = gChild.getAttribute("id");
                    if (id.startsWith("elem_") || id.startsWith("cluster_")) {
                        elementNodes.add(child);
                    } else if (id.startsWith("link_")) {
                        relationNodes.add(child);
                    }
                }
            }

            // Parse elements
            for (Node node : elementNodes) {
                Element element = parseElement((org.w3c.dom.Element) node);
                if (element != null) {
                    elements.add(element);
                }
            }

            // Parse relations - match endpoints to nearest elements
            for (Node node : relationNodes) {
                Relation relation = parseRelation((org.w3c.dom.Element) node, elements);
                if (relation != null) {
                    relations.add(relation);
                }
            }

            // Build parent-child relationships for clusters
            buildParentChildRelationships(elements);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse SVG", e);
        }

        model.setElements(elements);
        model.setRelations(relations);
        return model;
    }

    private org.w3c.dom.Element findFirstDomElement(org.w3c.dom.Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof org.w3c.dom.Element el) {
                if (tagName.equals(el.getTagName())) {
                    return el;
                }
            }
        }
        return null;
    }

    private Element parseElement(org.w3c.dom.Element gElement) {
        String gId = gElement.getAttribute("id");
        if (gId.isBlank()) {
            return null;
        }

        Element element = new Element();

        // Determine type from id prefix
        if (gId.startsWith("elem_")) {
            element.setType("class");
        } else if (gId.startsWith("cluster_")) {
            element.setType("group");
        } else {
            element.setType("unknown");
        }

        // Extract ID: use the rect's id if available, otherwise use g id
        String elemId = extractElementId(gElement, gId);
        element.setId(elemId);

        // Extract name from text content
        String name = extractName(gElement);
        element.setName(name);

        // Extract position and size from rect/polygon/path children
        extractPositionAndSize(gElement, element);

        return element;
    }

    private String extractElementId(org.w3c.dom.Element gElement, String gId) {
        // Look for a rect or shape with an id
        NodeList children = gElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof org.w3c.dom.Element el) {
                String tagName = el.getTagName();
                String id = el.getAttribute("id");
                if (!id.isBlank() && (tagName.equals("rect") || tagName.equals("ellipse")
                        || tagName.equals("polygon") || tagName.equals("path"))) {
                    return id;
                }
            }
        }
        // Fallback to g id (strip prefix)
        if (gId.startsWith("elem_")) {
            return gId.substring(5);
        } else if (gId.startsWith("cluster_")) {
            return gId.substring(8);
        }
        return gId;
    }

    private String extractName(org.w3c.dom.Element gElement) {
        List<String> textContents = new ArrayList<>();
        collectTextContent(gElement, textContents);
        if (!textContents.isEmpty()) {
            // The first text is usually the class/package name
            // Filter out very long texts (like method signatures)
            return textContents.stream()
                    .filter(t -> t.length() < 100)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private void collectTextContent(Node node, List<String> texts) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getTextContent().trim();
                if (!text.isEmpty()) {
                    texts.add(text);
                }
            } else if (child instanceof org.w3c.dom.Element el) {
                if ("text".equals(el.getTagName())) {
                    String text = el.getTextContent().trim();
                    if (!text.isEmpty()) {
                        texts.add(text);
                    }
                } else {
                    collectTextContent(child, texts);
                }
            }
        }
    }

    private void extractPositionAndSize(org.w3c.dom.Element gElement, Element element) {
        // Apply transform translate if present
        double translateX = 0;
        double translateY = 0;
        String transform = gElement.getAttribute("transform");
        if (!transform.isBlank()) {
            Matcher matcher = TRANSLATE_PATTERN.matcher(transform);
            if (matcher.find()) {
                translateX = parseDouble(matcher.group(1));
                translateY = parseDouble(matcher.group(2));
            }
        }

        // Find rect element for position and size
        NodeList children = gElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof org.w3c.dom.Element el) {
                String tagName = el.getTagName();

                if ("rect".equals(tagName)) {
                    double x = parseDouble(el.getAttribute("x")) + translateX;
                    double y = parseDouble(el.getAttribute("y")) + translateY;
                    double width = parseDouble(el.getAttribute("width"));
                    double height = parseDouble(el.getAttribute("height"));
                    element.setPosition(new Element.Position(x, y));
                    element.setSize(new Element.Size(width, height));
                    return;
                }

                if ("path".equals(tagName)) {
                    // For clusters (packages), extract bounding info from path
                    String d = el.getAttribute("d");
                    double[] bounds = parsePathBounds(d);
                    if (bounds != null) {
                        element.setPosition(new Element.Position(
                                bounds[0] + translateX, bounds[1] + translateY));
                        element.setSize(new Element.Size(bounds[2], bounds[3]));
                        return;
                    }
                }
            }
        }
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private double[] parsePathBounds(String d) {
        if (d == null || d.isBlank()) {
            return null;
        }
        // Extract M (moveTo), L (lineTo), and A (arc) coordinates
        // Simple approach: find first M coordinates and last L coordinates
        Pattern coordPattern = Pattern.compile("[ML]\\s*([\\d.-]+)\\s*,\\s*([\\d.-]+)");
        Matcher matcher = coordPattern.matcher(d);
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        boolean found = false;

        while (matcher.find()) {
            found = true;
            double x = Double.parseDouble(matcher.group(1));
            double y = Double.parseDouble(matcher.group(2));
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        if (found) {
            return new double[]{minX, minY, maxX - minX, maxY - minY};
        }
        return null;
    }

    private Relation parseRelation(org.w3c.dom.Element gElement, List<Element> elements) {
        String linkId = gElement.getAttribute("id");
        if (linkId.isBlank()) {
            return null;
        }

        Relation relation = new Relation();
        relation.setId(linkId);

        // Extract source and target from link id
        // Formats: link_Source_to_Target OR link_Source_Target
        extractRelationEndpoints(linkId, relation, elements);
        relation.setType("association");

        // Try to find path points
        List<Element.Position> points = new ArrayList<>();
        NodeList children = gElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof org.w3c.dom.Element el) {
                String tagName = el.getTagName();

                if ("path".equals(tagName)) {
                    String pathId = el.getAttribute("id");
                    if (!pathId.isBlank() && !relation.getId().equals(pathId)) {
                        relation.setId(pathId);
                    }
                    extractPathPoints(el.getAttribute("d"), points);
                } else if ("line".equals(tagName)) {
                    double x1 = parseDouble(el.getAttribute("x1"));
                    double y1 = parseDouble(el.getAttribute("y1"));
                    double x2 = parseDouble(el.getAttribute("x2"));
                    double y2 = parseDouble(el.getAttribute("y2"));
                    points.add(new Element.Position(x1, y1));
                    points.add(new Element.Position(x2, y2));
                } else if ("polyline".equals(tagName)) {
                    extractPolylinePoints(el.getAttribute("points"), points);
                } else if ("text".equals(tagName)) {
                    String label = el.getTextContent().trim();
                    if (!label.isEmpty()) {
                        relation.setLabel(label);
                    }
                }
            }
        }

        if (!points.isEmpty()) {
            relation.setPoints(points);
        }

        // Fallback: match endpoints to actual elements by proximity if not yet matched
        if ((relation.getSourceId() == null || relation.getTargetId() == null)
                && !points.isEmpty() && !elements.isEmpty()) {
            matchEndpointsToElements(relation, points, elements);
        }

        return relation;
    }

    private void extractRelationEndpoints(String linkId, Relation relation, List<Element> elements) {
        // Strip "link_" prefix
        if (!linkId.startsWith("link_")) {
            return;
        }
        String remaining = linkId.substring(5);

        // Try format: Source_to_Target first
        int toIdx = remaining.indexOf("_to_");
        if (toIdx > 0) {
            String sourceName = remaining.substring(0, toIdx);
            String targetName = remaining.substring(toIdx + 4);
            String sourceId = findElementIdByName(sourceName, elements);
            String targetId = findElementIdByName(targetName, elements);
            if (sourceId != null) relation.setSourceId(sourceId);
            if (targetId != null) relation.setTargetId(targetId);
            return;
        }

        // Try format: Source_Target — match against known element IDs
        // Sort by ID length descending to match longest first (avoids partial matches)
        List<String> sortedIds = elements.stream()
                .map(Element::getId)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();

        for (String id : sortedIds) {
            if (remaining.startsWith(id + "_")) {
                relation.setSourceId(id);
                String targetPart = remaining.substring(id.length() + 1);
                String targetId = findElementIdByName(targetPart, elements);
                if (targetId != null) {
                    relation.setTargetId(targetId);
                }
                return;
            }
        }
    }

    private String findElementIdByName(String name, List<Element> elements) {
        for (Element el : elements) {
            if (el.getId().equals(name) || el.getName().equals(name)) {
                return el.getId();
            }
        }
        return null;
    }

    private void extractPathPoints(String d, List<Element.Position> points) {
        if (d == null || d.isBlank()) {
            return;
        }
        // Extract all coordinate pairs from SVG path (handles M, L, C, S, Q, T, H, V, A)
        // Match any command letter followed by coordinates, or bare coordinate pairs
        Pattern coordPattern = Pattern.compile("([\\d.-]+)\\s*,\\s*([\\d.-]+)");
        Matcher matcher = coordPattern.matcher(d);
        while (matcher.find()) {
            double x = Double.parseDouble(matcher.group(1));
            double y = Double.parseDouble(matcher.group(2));
            points.add(new Element.Position(x, y));
        }
    }

    private void extractPolylinePoints(String pointsStr, List<Element.Position> points) {
        if (pointsStr == null || pointsStr.isBlank()) {
            return;
        }
        String[] pairs = pointsStr.trim().split("\\s+");
        for (String pair : pairs) {
            String[] coords = pair.split(",");
            if (coords.length == 2) {
                try {
                    double x = Double.parseDouble(coords[0].trim());
                    double y = Double.parseDouble(coords[1].trim());
                    points.add(new Element.Position(x, y));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private void matchEndpointsToElements(Relation relation, List<Element.Position> points,
                                           List<Element> elements) {
        if (points.isEmpty()) {
            return;
        }

        Element.Position start = points.get(0);
        Element.Position end = points.get(points.size() - 1);

        if (relation.getSourceId() == null) {
            String sourceId = findNearestElement(start, elements);
            if (sourceId != null) {
                relation.setSourceId(sourceId);
            }
        }
        if (relation.getTargetId() == null) {
            String targetId = findNearestElement(end, elements);
            if (targetId != null) {
                relation.setTargetId(targetId);
            }
        }
    }

    private String findNearestElement(Element.Position point, List<Element> elements) {
        String nearestId = null;
        double minDistance = MAX_ENDPOINT_DISTANCE;

        for (Element element : elements) {
            Element.Position center = getElementCenter(element);
            if (center == null) {
                continue;
            }
            double distance = Math.hypot(point.getX() - center.getX(), point.getY() - center.getY());
            if (distance < minDistance) {
                minDistance = distance;
                nearestId = element.getId();
            }
        }
        return nearestId;
    }

    private Element.Position getElementCenter(Element element) {
        if (element.getPosition() == null) {
            return null;
        }
        double cx = element.getPosition().getX();
        double cy = element.getPosition().getY();

        if (element.getSize() != null) {
            cx += element.getSize().getWidth() / 2.0;
            cy += element.getSize().getHeight() / 2.0;
        }

        return new Element.Position(cx, cy);
    }

    private void buildParentChildRelationships(List<Element> elements) {
        // For cluster elements, find children that are spatially contained within them.
        // Process clusters from SMALLEST to LARGEST so each element is assigned to its
        // tightest (most specific) containing cluster only.
        Set<String> assignedToParent = new HashSet<>();

        List<Element> groups = elements.stream()
                .filter(e -> "group".equals(e.getType()))
                .filter(e -> e.getPosition() != null && e.getSize() != null)
                .sorted(Comparator.comparingDouble(e -> e.getSize().getWidth() * e.getSize().getHeight()))
                .toList();

        for (Element parent : groups) {
            double px = parent.getPosition().getX();
            double py = parent.getPosition().getY();
            double pw = parent.getSize().getWidth();
            double ph = parent.getSize().getHeight();

            List<String> childIds = new ArrayList<>();
            for (Element child : elements) {
                if (child == parent) continue;
                if (child.getPosition() == null) continue;
                if (assignedToParent.contains(child.getId())) continue;

                double cx = child.getPosition().getX();
                double cy = child.getPosition().getY();

                if (cx >= px && cy >= py && cx <= px + pw && cy <= py + ph) {
                    childIds.add(child.getId());
                    child.setParentId(parent.getId());
                    assignedToParent.add(child.getId());
                }
            }

            if (!childIds.isEmpty()) {
                parent.setChildren(childIds);
            }
        }
    }
}
