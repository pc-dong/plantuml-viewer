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

            // Route to specialized parser for sequence diagrams
            if ("sequence".equals(diagramType)) {
                parseSequenceDiagram(root, elements, relations);
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

    private void parseSequenceDiagram(org.w3c.dom.Element root,
                                        List<Element> elements, List<Relation> relations) {
        // Sequence diagram SVGs from PlantUML have a flat structure: all primitives
        // (line, text, rect, ellipse, polygon, path) are direct children of a single <g>.
        // We categorize them into participants, lifelines, and messages.

        NodeList children = root.getChildNodes();

        // --- Pass 1: collect all children by type ---
        List<org.w3c.dom.Element> allRects = new ArrayList<>();
        List<org.w3c.dom.Element> allEllipses = new ArrayList<>();
        List<org.w3c.dom.Element> allPaths = new ArrayList<>();
        List<org.w3c.dom.Element> allLines = new ArrayList<>();
        List<org.w3c.dom.Element> allTexts = new ArrayList<>();
        List<org.w3c.dom.Element> allPolygons = new ArrayList<>();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!(child instanceof org.w3c.dom.Element el)) continue;
            switch (el.getTagName()) {
                case "rect" -> allRects.add(el);
                case "ellipse" -> allEllipses.add(el);
                case "path" -> allPaths.add(el);
                case "line" -> allLines.add(el);
                case "text" -> allTexts.add(el);
                case "polygon" -> allPolygons.add(el);
            }
        }

        // --- Pass 2: identify lifelines (dashed lines with small dasharray) ---
        // Lifelines are dashed vertical lines spanning most of the diagram height.
        // We keep a map of lifeline X → participant index for later message matching.
        Map<Double, Integer> lifelineXToParticipant = new TreeMap<>();
        List<LifelineInfo> lifelines = new ArrayList<>();

        for (org.w3c.dom.Element line : allLines) {
            String dasharray = getStyleProperty(line, "stroke-dasharray");
            if (dasharray == null || dasharray.isBlank()) continue;

            double x1 = parseDouble(line.getAttribute("x1"));
            double x2 = parseDouble(line.getAttribute("x2"));
            double y1 = parseDouble(line.getAttribute("y1"));
            double y2 = parseDouble(line.getAttribute("y2"));

            // Lifelines are nearly vertical (x1 ≈ x2)
            if (Math.abs(x1 - x2) > 2.0) continue;
            // Lifelines span a significant vertical distance
            if (Math.abs(y2 - y1) < 50.0) continue;

            double centerX = (x1 + x2) / 2.0;
            lifelineXToParticipant.put(centerX, lifelines.size());
            lifelines.add(new LifelineInfo(centerX, y1, y2));
        }

        // --- Pass 3: identify participant boxes (rect with rx ≈ 2.5) ---
        // Associate each box with a lifeline by proximity of x-center.
        List<ParticipantInfo> participants = new ArrayList<>();

        for (org.w3c.dom.Element rect : allRects) {
            double rx = parseDouble(rect.getAttribute("rx"));
            // Participant boxes have rounded corners (rx ≈ 2.5)
            if (rx < 2.0 || rx > 5.0) continue;

            double x = parseDouble(rect.getAttribute("x"));
            double y = parseDouble(rect.getAttribute("y"));
            double w = parseDouble(rect.getAttribute("width"));
            double h = parseDouble(rect.getAttribute("height"));

            if (w < 1.0 || h < 1.0) continue;

            double centerX = x + w / 2.0;

            // Find nearest lifeline
            Integer lifelineIdx = findNearestLifeline(centerX, lifelineXToParticipant);
            if (lifelineIdx == null) continue;

            participants.add(new ParticipantInfo(centerX, x, y, w, h, lifelineIdx, false));
        }

        // --- Pass 4: identify actor figures (ellipse) ---
        for (org.w3c.dom.Element ellipse : allEllipses) {
            double cx = parseDouble(ellipse.getAttribute("cx"));
            double cy = parseDouble(ellipse.getAttribute("cy"));
            double rx = parseDouble(ellipse.getAttribute("rx"));
            double ry = parseDouble(ellipse.getAttribute("ry"));

            // Actor head is a small circle-like ellipse (rx,ry ≈ 8)
            if (rx < 5.0 || rx > 15.0 || ry < 5.0 || ry > 15.0) continue;

            Integer lifelineIdx = findNearestLifeline(cx, lifelineXToParticipant);
            if (lifelineIdx == null) continue;

            // Check if this actor already added (from a bottom box or duplicate ellipse)
            boolean exists = participants.stream()
                    .anyMatch(p -> p.lifelineIndex == lifelineIdx);
            if (exists) continue;

            participants.add(new ParticipantInfo(cx, cx - rx, cy - ry, rx * 2, ry * 2, lifelineIdx, true));
        }

        // --- Pass 5: associate text labels with participants ---
        // Text elements for participants are positioned near the participant box/figure.
        for (ParticipantInfo p : participants) {
            String name = findTextInBox(p.x, p.y, p.w, p.h, allTexts);
            if (name != null) {
                p.name = name;
            }
        }

        // --- Pass 6: deduplicate participants by lifeline index ---
        // Keep the one with a name, or the topmost one
        Map<Integer, ParticipantInfo> bestParticipant = new LinkedHashMap<>();
        for (ParticipantInfo p : participants) {
            ParticipantInfo existing = bestParticipant.get(p.lifelineIndex);
            if (existing == null || (p.name != null && existing.name == null) || p.y < existing.y) {
                bestParticipant.put(p.lifelineIndex, p);
            }
        }
        participants = new ArrayList<>(bestParticipant.values());

        // Sort by x-position for stable ID assignment
        participants.sort(Comparator.comparingDouble(p -> p.centerX));

        // --- Build Element objects for participants ---
        for (int i = 0; i < participants.size(); i++) {
            ParticipantInfo p = participants.get(i);
            String id = sanitizeParticipantId(p.name, i);

            Element element = new Element();
            element.setId(id);
            element.setType(p.isActor ? "actor" : "participant");
            element.setName(p.name);
            element.setPosition(new Element.Position(p.x, p.y));
            element.setSize(new Element.Size(p.w, p.h));
            element.setMetadata(Map.of("lifelineIndex", p.lifelineIndex));
            elements.add(element);
        }

        // --- Pass 7: identify messages from non-lifeline lines ---
        // Messages are horizontal-ish lines with optional polygon arrowheads.
        Set<Integer> lifelineLineIndices = new HashSet<>();
        int lineIdx = 0;
        for (org.w3c.dom.Element line : allLines) {
            String dasharray = getStyleProperty(line, "stroke-dasharray");
            if (dasharray != null && !dasharray.isBlank()) {
                double x1 = parseDouble(line.getAttribute("x1"));
                double x2 = parseDouble(line.getAttribute("x2"));
                double y1 = parseDouble(line.getAttribute("y1"));
                double y2 = parseDouble(line.getAttribute("y2"));
                if (Math.abs(x1 - x2) <= 2.0 && Math.abs(y2 - y1) >= 50.0) {
                    lifelineLineIndices.add(lineIdx);
                }
            }
            lineIdx++;
        }

        // Collect polygon arrowheads for message association
        List<PolygonInfo> polygonInfos = new ArrayList<>();
        for (org.w3c.dom.Element polygon : allPolygons) {
            double[] centroid = parsePolygonCentroid(polygon.getAttribute("points"));
            if (centroid != null) {
                polygonInfos.add(new PolygonInfo(centroid[0], centroid[1], polygon));
            }
        }

        int msgCounter = 0;
        lineIdx = 0;
        for (org.w3c.dom.Element line : allLines) {
            if (lifelineLineIndices.contains(lineIdx)) {
                lineIdx++;
                continue;
            }

            double x1 = parseDouble(line.getAttribute("x1"));
            double y1 = parseDouble(line.getAttribute("y1"));
            double x2 = parseDouble(line.getAttribute("x2"));
            double y2 = parseDouble(line.getAttribute("y2"));

            // Messages should be mostly horizontal
            double dy = Math.abs(y2 - y1);
            double dx = Math.abs(x2 - x1);
            if (dy > dx * 0.5 && dy > 20.0) {
                // Not horizontal enough — likely a vertical fragment, skip
                lineIdx++;
                continue;
            }

            // Skip very short lines (arrowheads drawn as separate polygons)
            if (dx < 5.0 && dy < 5.0) {
                lineIdx++;
                continue;
            }

            // Skip section divider lines (== heading ==) — they start from x=0 and span the full width
            if (x1 <= 1.0) {
                lineIdx++;
                continue;
            }

            // Determine message type: solid = sync, dashed = return/async
            String dasharray = getStyleProperty(line, "stroke-dasharray");
            boolean isDashed = dasharray != null && !dasharray.isBlank();
            String msgType = isDashed ? "return" : "sync";

            // Find source/target participants by matching line endpoints to lifelines
            Integer sourceIdx = findNearestLifeline(x1, lifelineXToParticipant);
            Integer targetIdx = findNearestLifeline(x2, lifelineXToParticipant);

            if (sourceIdx == null || targetIdx == null) {
                lineIdx++;
                continue;
            }

            // Find label text near the message line
            double midY = (y1 + y2) / 2.0;
            String label = findTextNearMessage(x1, x2, midY, allTexts, 15.0);

            // Find associated polygon arrowhead
            String polygonId = findNearbyPolygon(x1, y1, x2, y2, polygonInfos);

            // Resolve to element IDs
            String sourceId = resolveParticipantId(sourceIdx, participants);
            String targetId = resolveParticipantId(targetIdx, participants);

            if (sourceId == null || targetId == null || sourceId.equals(targetId)) {
                lineIdx++;
                continue;
            }

            Relation relation = new Relation();
            String msgId = "msg_" + msgCounter++;
            relation.setId(polygonId != null ? polygonId : msgId);
            relation.setType(msgType);
            relation.setSourceId(sourceId);
            relation.setTargetId(targetId);
            relation.setLabel(label);

            List<Element.Position> points = new ArrayList<>();
            points.add(new Element.Position(x1, y1));
            points.add(new Element.Position(x2, y2));
            relation.setPoints(points);

            relations.add(relation);
            lineIdx++;
        }

        // --- Pass 8: self-messages (vertical lines from/to the same lifeline) ---
        // Self-messages appear as short lines going down and back to the same lifeline
        lineIdx = 0;
        for (org.w3c.dom.Element line : allLines) {
            if (lifelineLineIndices.contains(lineIdx)) {
                lineIdx++;
                continue;
            }

            double x1 = parseDouble(line.getAttribute("x1"));
            double y1 = parseDouble(line.getAttribute("y1"));
            double x2 = parseDouble(line.getAttribute("x2"));
            double y2 = parseDouble(line.getAttribute("y2"));

            double dx = Math.abs(x2 - x1);
            double dy = Math.abs(y2 - y1);

            // Self-messages: vertical-ish, short horizontal span, going downward
            if (dx > 5.0 && dx < 80.0 && dy > 15.0 && y2 > y1) {
                Integer sourceIdx = findNearestLifeline(x1, lifelineXToParticipant);
                if (sourceIdx == null) {
                    lineIdx++;
                    continue;
                }

                // Check this wasn't already captured as a horizontal message
                String sourceId = resolveParticipantId(sourceIdx, participants);
                if (sourceId == null) {
                    lineIdx++;
                    continue;
                }

                // Only create self-message if source and target are the same participant
                Integer targetIdx = findNearestLifeline(x2, lifelineXToParticipant);
                if (targetIdx == null || !targetIdx.equals(sourceIdx)) {
                    lineIdx++;
                    continue;
                }

                String label = findTextNearMessage(
                        Math.min(x1, x2) - 20, Math.max(x1, x2) + 20,
                        (y1 + y2) / 2.0, allTexts, 20.0);

                Relation relation = new Relation();
                relation.setId("self_msg_" + msgCounter++);
                relation.setType("sync");
                relation.setSourceId(sourceId);
                relation.setTargetId(sourceId);
                relation.setLabel(label);

                List<Element.Position> points = new ArrayList<>();
                points.add(new Element.Position(x1, y1));
                points.add(new Element.Position(x2, y2));
                relation.setPoints(points);

                relations.add(relation);
            }
            lineIdx++;
        }
    }

    /** Extract a CSS property value from an element's style attribute, or fall back to XML attribute. */
    private String getStyleProperty(org.w3c.dom.Element el, String propertyName) {
        // Check XML attribute first (e.g., stroke-dasharray="5.0,5.0")
        String attrVal = el.getAttribute(propertyName);
        if (attrVal != null && !attrVal.isBlank()) return attrVal.trim();
        // Fall back to CSS style attribute (e.g., style="...;stroke-dasharray:5.0,5.0;...")
        String style = el.getAttribute("style");
        if (style == null || style.isBlank()) return null;
        // Search for propertyName: value inside the style string
        String needle = propertyName + ":";
        int idx = style.indexOf(needle);
        if (idx < 0) return null;
        int start = idx + needle.length();
        int end = style.indexOf(';', start);
        if (end < 0) end = style.length();
        String value = style.substring(start, end).trim();
        return value.isBlank() ? null : value;
    }

    private Integer findNearestLifeline(double x, Map<Double, Integer> lifelineXMap) {
        if (lifelineXMap.isEmpty()) return null;
        double bestDist = Double.MAX_VALUE;
        Integer bestIdx = null;
        for (Map.Entry<Double, Integer> entry : lifelineXMap.entrySet()) {
            double dist = Math.abs(x - entry.getKey());
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx = entry.getValue();
            }
        }
        // Only match if reasonably close (within 100px)
        return bestDist < 100.0 ? bestIdx : null;
    }

    /** Find text element strictly inside the given box bounds. */
    private String findTextInBox(double boxX, double boxY, double boxW, double boxH,
                                  List<org.w3c.dom.Element> texts) {
        double boxRight = boxX + boxW;
        double boxBottom = boxY + boxH;
        double boxCenterX = boxX + boxW / 2.0;

        for (org.w3c.dom.Element textEl : texts) {
            String content = textEl.getTextContent().trim();
            // Skip empty, too long, or pure numeric (autonumber) texts
            if (content.isEmpty() || content.length() > 100) continue;
            if (content.matches("\\d+")) continue; // Skip autonumbers like "137"

            double tx = parseDouble(textEl.getAttribute("x"));
            double ty = parseDouble(textEl.getAttribute("y"));

            // Check if text is inside the box (allow slight padding)
            if (tx > boxX - 5 && tx < boxRight + 5 &&
                ty > boxY - 5 && ty < boxBottom + 5) {
                return content;
            }
        }
        return null;
    }

    private String findTextNearMessage(double x1, double x2, double midY,
                                         List<org.w3c.dom.Element> texts,
                                         double yTolerance) {
        String bestText = null;
        double bestDist = Double.MAX_VALUE;
        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);
        double messageWidth = maxX - minX;

        for (org.w3c.dom.Element textEl : texts) {
            String content = textEl.getTextContent().trim();
            // Skip empty, too long (autonumber or section divider), or pure numeric
            if (content.isEmpty() || content.length() > 100) continue;
            if (content.matches("\\d+")) continue; // Skip autonumbers like "137"

            double tx = parseDouble(textEl.getAttribute("x"));
            double ty = parseDouble(textEl.getAttribute("y"));

            // Text must be near the message vertically and within the horizontal span
            double yDist = Math.abs(ty - midY);
            if (yDist > yTolerance) continue;
            if (tx < minX - 30.0 || tx > maxX + 30.0) continue;

            double dist = Math.hypot(tx - (minX + maxX) / 2.0, yDist);
            if (dist < bestDist) {
                bestDist = dist;
                bestText = content;
            }
        }
        return bestText;
    }

    private double[] parsePolygonCentroid(String pointsStr) {
        if (pointsStr == null || pointsStr.isBlank()) return null;
        String[] pairs = pointsStr.trim().split("\\s+");
        double sumX = 0, sumY = 0;
        int count = 0;
        for (String pair : pairs) {
            String[] coords = pair.split(",");
            if (coords.length == 2) {
                try {
                    sumX += Double.parseDouble(coords[0].trim());
                    sumY += Double.parseDouble(coords[1].trim());
                    count++;
                } catch (NumberFormatException ignored) {}
            }
        }
        return count > 0 ? new double[]{sumX / count, sumY / count} : null;
    }

    private String findNearbyPolygon(double x1, double y1, double x2, double y2,
                                      List<PolygonInfo> polygons) {
        // Look for a polygon near the endpoint (x2, y2) — the arrowhead
        for (PolygonInfo p : polygons) {
            double dist = Math.hypot(p.cx - x2, p.cy - y2);
            if (dist < 20.0) {
                return p.element.getAttribute("id");
            }
        }
        return null;
    }

    private String resolveParticipantId(Integer lifelineIndex, List<ParticipantInfo> participants) {
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).lifelineIndex == lifelineIndex) {
                return sanitizeParticipantId(participants.get(i).name, i);
            }
        }
        return null;
    }

    private String sanitizeParticipantId(String name, int index) {
        if (name != null && !name.isBlank()) {
            // Strip surrounding quotes and guillemets
            String cleaned = name.replaceAll("[\"«»]", "").trim();
            cleaned = cleaned.replaceAll("[^a-zA-Z0-9_-]", "_");
            if (!cleaned.isBlank()) {
                return cleaned;
            }
        }
        return "participant_" + index;
    }

    /** Internal holder for collected participant data. */
    private static class ParticipantInfo {
        double centerX, x, y, w, h;
        int lifelineIndex;
        boolean isActor;
        String name;

        ParticipantInfo(double centerX, double x, double y, double w, double h,
                        int lifelineIndex, boolean isActor) {
            this.centerX = centerX;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.lifelineIndex = lifelineIndex;
            this.isActor = isActor;
        }
    }

    /** Internal holder for lifeline data. */
    private static class LifelineInfo {
        double centerX, y1, y2;
        LifelineInfo(double centerX, double y1, double y2) {
            this.centerX = centerX;
            this.y1 = y1;
            this.y2 = y2;
        }
    }

    /** Internal holder for polygon (arrowhead) data. */
    private static class PolygonInfo {
        double cx, cy;
        org.w3c.dom.Element element;
        PolygonInfo(double cx, double cy, org.w3c.dom.Element element) {
            this.cx = cx;
            this.cy = cy;
            this.element = element;
        }
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
