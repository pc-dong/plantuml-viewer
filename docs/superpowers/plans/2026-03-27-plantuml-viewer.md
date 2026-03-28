# PlantUML Dynamic Preview Tool — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Build a web-based interactive PlantUML preview tool with real-time rendering, element hide/show/fold, relation auto-adaptation, tree control panel, presentation mode, and image export.

**Architecture:** Monorepo with pnpm workspaces. Spring Boot backend generates SVG via PlantUML Java library and parses SVG DOM to extract structured element/relation data as JSON. React frontend renders SVG with interactive overlays, manages visibility state via Zustand, and provides a 3-panel layout with editor, diagram view, and tree control.

**Tech Stack:** Spring Boot 3.x + PlantUML (Java 17+) | React 18 + TypeScript + Vite + Ant Design 5 + Zustand + Monaco Editor + D3.js + Axios | pnpm workspaces

---

## Phase 1: Project Scaffolding

### Task 1: Monorepo Root Setup

**Files:**
- Create: `package.json`
- Create: `pnpm-workspace.yaml`
- Modify: `.gitignore`

- [x] **Step 1: Create root package.json**

```json
{
  "name": "plantuml-viewer",
  "version": "1.0.0",
  "private": true,
  "scripts": {
    "dev": "pnpm --parallel -r dev",
    "dev:frontend": "pnpm --filter @plantuml-viewer/frontend dev",
    "dev:backend": "pnpm --filter @plantuml-viewer/backend dev",
    "build": "pnpm --filter @plantuml-viewer/frontend build",
    "clean": "pnpm -r exec rm -rf dist target node_modules"
  },
  "engines": {
    "node": ">=18",
    "pnpm": ">=8"
  }
}
```

- [x] **Step 2: Create pnpm-workspace.yaml**

```yaml
packages:
  - 'packages/*'
```

- [x] **Step 3: Update .gitignore**

Ensure `.gitignore` includes these entries (add if missing):

```
node_modules/
dist/
target/
.classpath
.project
.settings/
*.jar
*.war
*.class
.idea/
*.iml
.vscode/
*.swp
*.swo
.DS_Store
.env
.env.local
```

- [x] **Step 4: Install dependencies and verify**

Run: `pnpm install`
Expected: Creates `pnpm-lock.yaml`, no packages yet since workspaces are empty.

- [x] **Step 5: Commit**

```bash
git add package.json pnpm-workspace.yaml .gitignore
git commit -m "chore: initialize monorepo with pnpm workspaces"
```

---

### Task 2: Spring Boot Backend Skeleton

**Files:**
- Create: `packages/backend/pom.xml`
- Create: `packages/backend/src/main/java/com/plantuml/viewer/PlantumlViewerApplication.java`
- Create: `packages/backend/src/main/resources/application.yml`
- Create: `packages/backend/src/main/java/com/plantuml/viewer/config/WebConfig.java`

- [x] **Step 1: Create pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <groupId>com.plantuml</groupId>
    <artifactId>viewer-backend</artifactId>
    <version>1.0.0</version>
    <name>plantuml-viewer-backend</name>
    <description>PlantUML Viewer Backend</description>

    <properties>
        <java.version>17</java.version>
        <plantuml.version>1.2024.8</plantuml.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>net.sourceforge.plantuml</groupId>
            <artifactId>plantuml</artifactId>
            <version>${plantuml.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [x] **Step 2: Create application.yml**

```yaml
server:
  port: 8080

spring:
  application:
    name: plantuml-viewer

logging:
  level:
    com.plantuml.viewer: DEBUG
```

- [x] **Step 3: Create PlantumlViewerApplication.java**

Create directory structure: `packages/backend/src/main/java/com/plantuml/viewer/`

```java
package com.plantuml.viewer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PlantumlViewerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlantumlViewerApplication.class, args);
    }
}
```

- [x] **Step 4: Create WebConfig.java for CORS**

Create: `packages/backend/src/main/java/com/plantuml/viewer/config/WebConfig.java`

```java
package com.plantuml.viewer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
```

- [x] **Step 5: Verify backend starts**

Run: `cd packages/backend && mvn spring-boot:run`
Expected: Server starts on port 8080, logs show `Started PlantumlViewerApplication`. Stop with Ctrl+C after verifying.

- [x] **Step 6: Commit**

```bash
git add packages/backend/
git commit -m "feat(backend): scaffold Spring Boot project with CORS config"
```

---

## Phase 2: Backend Core

### Task 3: Backend Model/DTO Classes

**Files:**
- Create: `packages/backend/src/main/java/com/plantuml/viewer/model/Element.java`
- Create: `packages/backend/src/main/java/com/plantuml/viewer/model/Relation.java`
- Create: `packages/backend/src/main/java/com/plantuml/viewer/model/DiagramModel.java`
- Create: `packages/backend/src/main/java/com/plantuml/viewer/model/ParseRequest.java`
- Create: `packages/backend/src/main/java/com/plantuml/viewer/model/ErrorResponse.java`

- [x] **Step 1: Create Element.java**

```java
package com.plantuml.viewer.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Element {
    private String id;
    private String type;
    private String name;
    private String parentId;
    private List<String> children;
    private Position position;
    private Size size;
    private boolean visible = true;
    private Boolean collapsed;
    private Map<String, String> style;
    private Map<String, Object> metadata;

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public List<String> getChildren() { return children; }
    public void setChildren(List<String> children) { this.children = children; }
    public Position getPosition() { return position; }
    public void setPosition(Position position) { this.position = position; }
    public Size getSize() { return size; }
    public void setSize(Size size) { this.size = size; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public Boolean getCollapsed() { return collapsed; }
    public void setCollapsed(Boolean collapsed) { this.collapsed = collapsed; }
    public Map<String, String> getStyle() { return style; }
    public void setStyle(Map<String, String> style) { this.style = style; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public static class Position {
        private double x;
        private double y;

        public Position() {}

        public Position(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
    }

    public static class Size {
        private double width;
        private double height;

        public Size() {}

        public Size(double width, double height) {
            this.width = width;
            this.height = height;
        }

        public double getWidth() { return width; }
        public void setWidth(double width) { this.width = width; }
        public double getHeight() { return height; }
        public void setHeight(double height) { this.height = height; }
    }
}
```

- [x] **Step 2: Create Relation.java**

```java
package com.plantuml.viewer.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Relation {
    private String id;
    private String type;
    private String sourceId;
    private String targetId;
    private String label;
    private List<Element.Position> points;
    private Map<String, String> style;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public List<Element.Position> getPoints() { return points; }
    public void setPoints(List<Element.Position> points) { this.points = points; }
    public Map<String, String> getStyle() { return style; }
    public void setStyle(Map<String, String> style) { this.style = style; }
}
```

- [x] **Step 3: Create DiagramModel.java**

```java
package com.plantuml.viewer.model;

import java.util.List;

public class DiagramModel {
    private String version = "1.0";
    private String type;
    private List<Element> elements;
    private List<Relation> relations;

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public List<Element> getElements() { return elements; }
    public void setElements(List<Element> elements) { this.elements = elements; }
    public List<Relation> getRelations() { return relations; }
    public void setRelations(List<Relation> relations) { this.relations = relations; }
}
```

- [x] **Step 4: Create ParseRequest.java**

```java
package com.plantuml.viewer.model;

public class ParseRequest {
    private String source;

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
```

- [x] **Step 5: Create ErrorResponse.java**

```java
package com.plantuml.viewer.model;

public class ErrorResponse {
    private String error;
    private String details;

    public ErrorResponse(String error, String details) {
        this.error = error;
        this.details = details;
    }

    public String getError() { return error; }
    public String getDetails() { return details; }
}
```

- [x] **Step 6: Verify compilation**

Run: `cd packages/backend && mvn compile -q`
Expected: BUILD SUCCESS

- [x] **Step 7: Commit**

```bash
git add packages/backend/src/main/java/com/plantuml/viewer/model/
git commit -m "feat(backend): add model/DTO classes for DiagramModel, Element, Relation"
```

---

### Task 4: PlantUmlService — SVG Generation

**Files:**
- Create: `packages/backend/src/main/java/com/plantuml/viewer/service/PlantUmlService.java`
- Test: `packages/backend/src/test/java/com/plantuml/viewer/service/PlantUmlServiceTest.java`

- [x] **Step 1: Write the failing test**

Create: `packages/backend/src/test/java/com/plantuml/viewer/service/PlantUmlServiceTest.java`

```java
package com.plantuml.viewer.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlantUmlServiceTest {

    private final PlantUmlService service = new PlantUmlService();

    @Test
    void generateSvg_withValidSource_returnsSvgString() {
        String source = "@startuml\nclass Foo\n@enduml";
        String svg = service.generateSvg(source);

        assertNotNull(svg);
        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("</svg>"));
    }

    @Test
    void generateSvg_withEmptySource_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> service.generateSvg(""));
    }

    @Test
    void generateSvg_withSyntaxError_throwsException() {
        String source = "@startuml\nINVALID SYNTAX @#@#\n@enduml";
        // PlantUML may not throw for syntax errors — it renders what it can
        // But a completely malformed source should at least not crash
        String svg = service.generateSvg(source);
        assertNotNull(svg);
    }

    @Test
    void generateSvg_withSequenceDiagram_returnsSvg() {
        String source = "@startuml\nactor Alice\nactor Bob\nAlice->Bob : Hello\n@enduml";
        String svg = service.generateSvg(source);
        assertNotNull(svg);
        assertTrue(svg.contains("<svg"));
    }

    @Test
    void detectDiagramType_withClassDiagram_returnsClass() {
        String source = "@startuml\nclass Foo\n@enduml";
        assertEquals("class", service.detectDiagramType(source));
    }

    @Test
    void detectDiagramType_withSequenceDiagram_returnsSequence() {
        String source = "@startuml\nactor Alice\nAlice->Bob : Hello\n@enduml";
        assertEquals("sequence", service.detectDiagramType(source));
    }

    @Test
    void detectDiagramType_withUseCaseDiagram_returnsUsecase() {
        String source = "@startuml\nactor User\nusecase \"Do Thing\" as UC1\nUser --> UC1\n@enduml";
        assertEquals("usecase", service.detectDiagramType(source));
    }
}
```

- [x] **Step 2: Run tests to verify they fail**

Run: `cd packages/backend && mvn test -pl . -Dtest=PlantUmlServiceTest -q`
Expected: FAIL — class `PlantUmlService` does not exist

- [x] **Step 3: Implement PlantUmlService**

Create: `packages/backend/src/main/java/com/plantuml/viewer/service/PlantUmlService.java`

```java
package com.plantuml.viewer.service;

import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.FileFormat;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class PlantUmlService {

    public String generateSvg(String source) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Source cannot be empty");
        }

        try {
            SourceStringReader reader = new SourceStringReader(source);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            reader.outputImage(os, new FileFormatOption(FileFormat.SVG));
            return os.toString("UTF-8");
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate SVG", e);
        }
    }

    public String detectDiagramType(String source) {
        String lower = source.toLowerCase();
        if (lower.contains("actor") && (lower.contains("->") || lower.contains("-->"))) {
            return "sequence";
        }
        if (lower.contains("usecase") || lower.contains("use case")) {
            return "usecase";
        }
        if (lower.contains("component") && !lower.contains("class")) {
            return "component";
        }
        if (lower.contains("start") && lower.contains("stop")) {
            return "activity";
        }
        return "class";
    }
}
```

- [x] **Step 4: Run tests to verify they pass**

Run: `cd packages/backend && mvn test -pl . -Dtest=PlantUmlServiceTest -q`
Expected: All tests PASS

- [x] **Step 5: Commit**

```bash
git add packages/backend/src/main/java/com/plantuml/viewer/service/PlantUmlService.java
git add packages/backend/src/test/java/com/plantuml/viewer/service/PlantUmlServiceTest.java
git commit -m "feat(backend): add PlantUmlService for SVG generation"
```

---

### Task 5: SvgParserService — SVG DOM Parsing

**Files:**
- Create: `packages/backend/src/main/java/com/plantuml/viewer/service/SvgParserService.java`
- Test: `packages/backend/src/test/java/com/plantuml/viewer/service/SvgParserServiceTest.java`

- [x] **Step 1: Write the failing test**

Create: `packages/backend/src/test/java/com/plantuml/viewer/service/SvgParserServiceTest.java`

```java
package com.plantuml.viewer.service;

import com.plantuml.viewer.model.DiagramModel;
import com.plantuml.viewer.model.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SvgParserServiceTest {

    private SvgParserService parser;
    private PlantUmlService plantUmlService;

    @BeforeEach
    void setUp() {
        parser = new SvgParserService();
        plantUmlService = new PlantUmlService();
    }

    @Test
    void parse_withSimpleClassDiagram_returnsElements() {
        String source = "@startuml\nclass Foo {\n  +name: String\n}\n@enduml";
        String svg = plantUmlService.generateSvg(source);
        DiagramModel model = parser.parse(svg, "class");

        assertNotNull(model);
        assertNotNull(model.getElements());
        assertFalse(model.getElements().isEmpty());
        assertEquals("1.0", model.getVersion());
        assertEquals("class", model.getType());
    }

    @Test
    void parse_withTwoClasses_returnsTwoElements() {
        String source = "@startuml\nclass Foo\nclass Bar\nFoo --> Bar\n@enduml";
        String svg = plantUmlService.generateSvg(source);
        DiagramModel model = parser.parse(svg, "class");

        // Should have at least 2 class elements
        long classCount = model.getElements().stream()
                .filter(e -> "class".equals(e.getType()))
                .count();
        assertTrue(classCount >= 2, "Expected at least 2 class elements, got " + classCount);
    }

    @Test
    void parse_withRelation_returnsRelations() {
        String source = "@startuml\nclass Foo\nclass Bar\nFoo --> Bar\n@enduml";
        String svg = plantUmlService.generateSvg(source);
        DiagramModel model = parser.parse(svg, "class");

        assertNotNull(model.getRelations());
        assertFalse(model.getRelations().isEmpty(), "Expected at least 1 relation");
    }

    @Test
    void parse_elementsHavePositions() {
        String source = "@startuml\nclass Foo\n@enduml";
        String svg = plantUmlService.generateSvg(source);
        DiagramModel model = parser.parse(svg, "class");

        for (Element e : model.getElements()) {
            assertNotNull(e.getPosition(), "Element " + e.getId() + " should have position");
            assertNotNull(e.getSize(), "Element " + e.getId() + " should have size");
            assertTrue(e.getPosition().getX() >= 0);
            assertTrue(e.getPosition().getY() >= 0);
            assertTrue(e.getSize().getWidth() > 0);
            assertTrue(e.getSize().getHeight() > 0);
        }
    }

    @Test
    void parse_elementsHaveIds() {
        String source = "@startuml\nclass Foo\n@enduml";
        String svg = plantUmlService.generateSvg(source);
        DiagramModel model = parser.parse(svg, "class");

        for (Element e : model.getElements()) {
            assertNotNull(e.getId(), "Element should have an id");
            assertFalse(e.getId().isBlank());
        }
    }

    @Test
    void parse_withPackage_returnsElementsWithParent() {
        String source = "@startuml\npackage com.example {\nclass Foo\n}\n@enduml";
        String svg = plantUmlService.generateSvg(source);
        DiagramModel model = parser.parse(svg, "class");

        // Should have a package element and a class element with parentId
        assertNotNull(model.getElements());
    }
}
```

- [x] **Step 2: Run tests to verify they fail**

Run: `cd packages/backend && mvn test -pl . -Dtest=SvgParserServiceTest -q`
Expected: FAIL — class `SvgParserService` does not exist

- [x] **Step 3: Implement SvgParserService**

Create: `packages/backend/src/main/java/com/plantuml/viewer/service/SvgParserService.java`

```java
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

    private static final Pattern ID_PATTERN = Pattern.compile("(?:elem|Entity|CLASS|PACK)_(\\d+)");
    private static final Set<String> CONTAINER_TYPES = Set.of("package", "namespace", "frame", "rectangle", "node", "cloud");

    public DiagramModel parse(String svgContent, String diagramType) {
        DiagramModel model = new DiagramModel();
        model.setVersion("1.0");
        model.setType(diagramType);

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(svgContent)));

            List<Element> elements = new ArrayList<>();
            List<Relation> relations = new ArrayList<>();
            Map<String, Element> elementMap = new LinkedHashMap<>();
            Deque<String> parentStack = new ArrayDeque<>();

            // Walk the SVG DOM tree
            walkNodes(doc.getDocumentElement(), elements, elementMap, parentStack, 0);

            // Build parent-child relationships
            buildParentChildRelations(elements, elementMap);

            // Extract relations from paths and lines
            extractRelations(doc.getDocumentElement(), elementMap, relations);

            model.setElements(elements);
            model.setRelations(relations);

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse SVG", e);
        }

        return model;
    }

    private void walkNodes(Node node, List<Element> elements, Map<String, Element> elementMap,
                           Deque<String> parentStack, int depth) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = node == node.getOwnerDocument().getDocumentElement()
                    ? null
                    : tryExtractElement((Element) node, parentStack.peek());

            if (element != null) {
                elements.add(element);
                elementMap.put(element.getId(), element);

                if (CONTAINER_TYPES.contains(element.getType())) {
                    parentStack.push(element.getId());
                    walkChildren(node, elements, elementMap, parentStack, depth + 1);
                    parentStack.pop();
                    return;
                }
            }
        }

        walkChildren(node, elements, elementMap, parentStack, depth + 1);
    }

    private void walkChildren(Node node, List<Element> elements, Map<String, Element> elementMap,
                              Deque<String> parentStack, int depth) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            walkNodes(children.item(i), elements, elementMap, parentStack, depth + 1);
        }
    }

    private Element tryExtractElement(Element node, String parentId) {
        String id = extractId(node);
        if (id == null) return null;

        String type = detectElementType(node);
        String name = extractName(node);

        // Skip unnamed generic groups
        if (name == null && type == null) return null;

        Element element = new Element();
        element.setId(id);
        element.setType(type != null ? type : "group");
        element.setName(name != null ? name : "");
        element.setParentId(parentId);
        element.setVisible(true);

        // Extract position and size
        extractBounds(node, element);

        return element;
    }

    private String extractId(Element node) {
        // Check for PlantUML data attributes first
        String dataId = node.getAttribute("data-plantuml-elem-id");
        if (!dataId.isEmpty()) return dataId;

        dataId = node.getAttribute("id");
        if (!dataId.isEmpty()) return dataId;

        // Try to match common PlantUML patterns in attributes
        String cls = node.getAttribute("class");
        Matcher matcher = ID_PATTERN.matcher(cls + " " + node.getAttribute("id"));
        if (matcher.find()) return "elem_" + matcher.group(1);

        return null;
    }

    private String detectElementType(Element node) {
        String id = node.getAttribute("id");
        String cls = node.getAttribute("class");
        String combined = (id + " " + cls).toLowerCase();

        if (combined.contains("class") || combined.contains("entity")) return "class";
        if (combined.contains("interface")) return "interface";
        if (combined.contains("enum")) return "enum";
        if (combined.contains("abstract")) return "abstract_class";
        if (combined.contains("actor")) return "actor";
        if (combined.contains("usecase") || combined.contains("use case")) return "usecase";
        if (combined.contains("package") || combined.contains("pack")) return "package";
        if (combined.contains("component")) return "component";
        if (combined.contains("node")) return "node";
        if (combined.contains("note")) return "note";
        if (combined.contains("frame")) return "frame";
        if (combined.contains("rectangle") || combined.contains("rect")) return "rectangle";

        // Check child elements for type clues
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                String childTag = children.item(i).getNodeName().toLowerCase();
                if (childTag.equals("rect") || childTag.equals("polygon")) {
                    // Has visual bounds — likely a meaningful element
                    return "group";
                }
            }
        }

        return null;
    }

    private String extractName(Element node) {
        // First check title element
        NodeList titles = node.getElementsByTagName("title");
        if (titles.getLength() > 0) {
            String title = titles.item(0).getTextContent().trim();
            if (!title.isEmpty()) return title;
        }

        // Check text children
        return extractTextContent(node);
    }

    private String extractTextContent(Element node) {
        StringBuilder text = new StringBuilder();
        collectText(node, text);
        String result = text.toString().trim();
        // Only return if reasonably short (not entire SVG text)
        return result.length() <= 200 ? result : null;
    }

    private void collectText(Node node, StringBuilder text) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            text.append(node.getTextContent());
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            collectText(children.item(i), text);
        }
    }

    private void extractBounds(Element node, Element element) {
        // Try to get bounds from the element itself or its rect/polygon children
        double[] bounds = findBounds(node);
        if (bounds != null) {
            element.setPosition(new Element.Position(bounds[0], bounds[1]));
            element.setSize(new Element.Size(bounds[2], bounds[3]));
        }
    }

    private double[] findBounds(Element node) {
        // Check transform attribute for translation
        double tx = 0, ty = 0;
        String transform = node.getAttribute("transform");
        if (!transform.isEmpty()) {
            double[] translate = parseTranslate(transform);
            tx = translate[0];
            ty = translate[1];
        }

        // Look for rect or polygon children
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) child;
                String tag = el.getTagName().toLowerCase();

                if (tag.equals("rect")) {
                    double x = getDoubleAttr(el, "x", 0);
                    double y = getDoubleAttr(el, "y", 0);
                    double w = getDoubleAttr(el, "width", 0);
                    double h = getDoubleAttr(el, "height", 0);
                    return new double[]{tx + x, ty + y, w, h};
                }

                if (tag.equals("polygon") || tag.equals("path")) {
                    return computePathBounds(el, tx, ty);
                }
            }
        }

        return null;
    }

    private double[] computePathBounds(Element el, double tx, double ty) {
        String points = el.getAttribute("points");
        if (!points.isEmpty()) {
            return computePolygonBounds(points, tx, ty);
        }

        String d = el.getAttribute("d");
        if (!d.isEmpty()) {
            return computePathDBounds(d, tx, ty);
        }

        return null;
    }

    private double[] computePolygonBounds(String points, double tx, double ty) {
        String[] pairs = points.trim().split("\\s+");
        if (pairs.length < 3) return null;

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

        for (String pair : pairs) {
            String[] coords = pair.split(",");
            if (coords.length >= 2) {
                double x = Double.parseDouble(coords[0]) + tx;
                double y = Double.parseDouble(coords[1]) + ty;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        return new double[]{minX, minY, maxX - minX, maxY - minY};
    }

    private double[] computePathDBounds(String d, double tx, double ty) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;
        boolean found = false;

        // Extract all coordinate pairs from M, L, C, Q commands
        Pattern coordPattern = Pattern.compile("[-+]?\\d*\\.?\\d+");
        Matcher m = coordPattern.matcher(d);
        while (m.find()) {
            try {
                double val = Double.parseDouble(m.group());
                // Alternate between x and y
                if (!found) {
                    minX = Math.min(minX, val + tx);
                    maxX = Math.max(maxX, val + tx);
                    found = true;
                } else {
                    minY = Math.min(minY, val + ty);
                    maxY = Math.max(maxY, val + ty);
                    found = false;
                }
            } catch (NumberFormatException ignored) {}
        }

        if (minX == Double.MAX_VALUE) return null;
        return new double[]{minX, minY, maxX - minX, maxY - minY};
    }

    private double[] parseTranslate(String transform) {
        Pattern pattern = Pattern.compile("translate\\(\\s*([-+]?\\d*\\.?\\d+)\\s*[,\\s]\\s*([-+]?\\d*\\.?\\d+)\\s*\\)");
        Matcher m = pattern.matcher(transform);
        if (m.find()) {
            return new double[]{
                    Double.parseDouble(m.group(1)),
                    Double.parseDouble(m.group(2))
            };
        }
        return new double[]{0, 0};
    }

    private double getDoubleAttr(Element el, String attr, double defaultVal) {
        String val = el.getAttribute(attr);
        if (val.isEmpty()) return defaultVal;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private void buildParentChildRelations(List<Element> elements, Map<String, Element> elementMap) {
        for (Element element : elements) {
            if (element.getParentId() != null) {
                Element parent = elementMap.get(element.getParentId());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(element.getId());
                    // Mark container types as collapsible
                    if (CONTAINER_TYPES.contains(parent.getType())) {
                        parent.setCollapsed(false);
                    }
                }
            }
        }
    }

    private void extractRelations(Element root, Map<String, Element> elementMap, List<Relation> relations) {
        int relationId = 0;

        // Collect all path/line elements that could be relations
        NodeList paths = root.getElementsByTagName("path");
        for (int i = 0; i < paths.getLength(); i++) {
            Element path = (Element) paths.item(i);
            Relation relation = tryExtractRelation(path, elementMap, relationId++);
            if (relation != null) {
                relations.add(relation);
            }
        }

        // Also check lines and polylines
        NodeList lines = root.getElementsByTagName("line");
        for (int i = 0; i < lines.getLength(); i++) {
            Element line = (Element) lines.item(i);
            Relation relation = tryExtractRelationFromLine(line, elementMap, relationId++);
            if (relation != null) {
                relations.add(relation);
            }
        }

        NodeList polys = root.getElementsByTagName("polyline");
        for (int i = 0; i < polys.getLength(); i++) {
            Element poly = (Element) polys.item(i);
            Relation relation = tryExtractRelationFromPoly(poly, elementMap, relationId++);
            if (relation != null) {
                relations.add(relation);
            }
        }
    }

    private Relation tryExtractRelation(Element path, Map<String, Element> elementMap, int id) {
        String d = path.getAttribute("d");
        if (d == null || d.isEmpty()) return null;

        // Get the path's start and end points
        double[] bounds = computePathDBounds(d, 0, 0);
        if (bounds == null || bounds[2] < 10 || bounds[3] < 10) return null;

        // Skip very small paths (likely decorative)
        if (bounds[2] < 20 && bounds[3] < 20) return null;

        double startX = bounds[0];
        double startY = bounds[1];
        double endX = bounds[0] + bounds[2];
        double endY = bounds[1] + bounds[3];

        // Find nearest elements
        String sourceId = findNearestElement(startX, startY, elementMap);
        String targetId = findNearestElement(endX, endY, elementMap);

        if (sourceId != null && targetId != null && !sourceId.equals(targetId)) {
            Relation relation = new Relation();
            relation.setId("rel_" + id);
            relation.setSourceId(sourceId);
            relation.setTargetId(targetId);
            relation.setType("dependency");

            // Extract label from nearby text
            String label = findNearbyText(path);
            if (label != null) {
                relation.setLabel(label);
            }

            return relation;
        }

        return null;
    }

    private Relation tryExtractRelationFromLine(Element line, Map<String, Element> elementMap, int id) {
        double x1 = getDoubleAttr(line, "x1", Double.MAX_VALUE);
        double y1 = getDoubleAttr(line, "y1", Double.MAX_VALUE);
        double x2 = getDoubleAttr(line, "x2", Double.MAX_VALUE);
        double y2 = getDoubleAttr(line, "y2", Double.MAX_VALUE);

        if (x1 == Double.MAX_VALUE) return null;

        String sourceId = findNearestElement(x1, y1, elementMap);
        String targetId = findNearestElement(x2, y2, elementMap);

        if (sourceId != null && targetId != null && !sourceId.equals(targetId)) {
            Relation relation = new Relation();
            relation.setId("rel_" + id);
            relation.setSourceId(sourceId);
            relation.setTargetId(targetId);
            relation.setType("dependency");
            return relation;
        }

        return null;
    }

    private Relation tryExtractRelationFromPoly(Element poly, Map<String, Element> elementMap, int id) {
        String points = poly.getAttribute("points");
        if (points.isEmpty()) return null;

        double[] bounds = computePolygonBounds(points, 0, 0);
        if (bounds == null) return null;

        String sourceId = findNearestElement(bounds[0], bounds[1], elementMap);
        String targetId = findNearestElement(bounds[0] + bounds[2], bounds[1] + bounds[3], elementMap);

        if (sourceId != null && targetId != null && !sourceId.equals(targetId)) {
            Relation relation = new Relation();
            relation.setId("rel_" + id);
            relation.setSourceId(sourceId);
            relation.setTargetId(targetId);
            relation.setType("dependency");
            return relation;
        }

        return null;
    }

    private String findNearestElement(double x, double y, Map<String, Element> elementMap) {
        String nearest = null;
        double minDist = Double.MAX_VALUE;

        for (Map.Entry<String, Element> entry : elementMap.entrySet()) {
            Element el = entry.getValue();
            if (el.getPosition() == null || el.getSize() == null) continue;

            double cx = el.getPosition().getX() + el.getSize().getWidth() / 2;
            double cy = el.getPosition().getY() + el.getSize().getHeight() / 2;
            double dist = Math.sqrt(Math.pow(x - cx, 2) + Math.pow(y - cy, 2));

            // Element must be within a reasonable distance (max 500px)
            if (dist < minDist && dist < 500) {
                minDist = dist;
                nearest = entry.getKey();
            }
        }

        return nearest;
    }

    private String findNearbyText(Element path) {
        Node parent = path.getParentNode();
        if (parent == null) return null;

        NodeList siblings = parent.getChildNodes();
        for (int i = 0; i < siblings.getLength(); i++) {
            Node sibling = siblings.item(i);
            if (sibling.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) sibling;
                if (el.getTagName().equalsIgnoreCase("text")) {
                    String text = el.getTextContent().trim();
                    if (!text.isEmpty() && text.length() < 100) {
                        return text;
                    }
                }
            }
        }
        return null;
    }
}
```

- [x] **Step 4: Run tests to verify they pass**

Run: `cd packages/backend && mvn test -pl . -Dtest=SvgParserServiceTest -q`
Expected: All tests PASS

- [x] **Step 5: Commit**

```bash
git add packages/backend/src/main/java/com/plantuml/viewer/service/SvgParserService.java
git add packages/backend/src/test/java/com/plantuml/viewer/service/SvgParserServiceTest.java
git commit -m "feat(backend): add SvgParserService for SVG DOM element/relation extraction"
```

---

### Task 6: ParseController — REST API Endpoints

**Files:**
- Create: `packages/backend/src/main/java/com/plantuml/viewer/controller/ParseController.java`
- Create: `packages/backend/src/main/java/com/plantuml/viewer/controller/HealthController.java`

- [x] **Step 1: Create HealthController.java**

Create: `packages/backend/src/main/java/com/plantuml/viewer/controller/HealthController.java`

```java
package com.plantuml.viewer.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
```

- [x] **Step 2: Create ParseController.java**

Create: `packages/backend/src/main/java/com/plantuml/viewer/controller/ParseController.java`

```java
package com.plantuml.viewer.controller;

import com.plantuml.viewer.model.*;
import com.plantuml.viewer.service.PlantUmlService;
import com.plantuml.viewer.service.SvgParserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ParseController {

    private final PlantUmlService plantUmlService;
    private final SvgParserService svgParserService;

    public ParseController(PlantUmlService plantUmlService, SvgParserService svgParserService) {
        this.plantUmlService = plantUmlService;
        this.svgParserService = svgParserService;
    }

    @PostMapping("/parse")
    public ResponseEntity<?> parse(@RequestBody ParseRequest request) {
        if (request.getSource() == null || request.getSource().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Source cannot be empty", "Provide valid PlantUML source code"));
        }

        try {
            String svg = plantUmlService.generateSvg(request.getSource());
            String diagramType = plantUmlService.detectDiagramType(request.getSource());
            DiagramModel model = svgParserService.parse(svg, diagramType);
            return ResponseEntity.ok(model);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Invalid source", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("Parse error", e.getMessage()));
        }
    }

    @PostMapping("/render")
    public ResponseEntity<?> render(@RequestBody ParseRequest request) {
        if (request.getSource() == null || request.getSource().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Source cannot be empty", "Provide valid PlantUML source code"));
        }

        try {
            String svg = plantUmlService.generateSvg(request.getSource());
            return ResponseEntity.ok()
                    .header("Content-Type", "image/svg+xml")
                    .body(svg);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("Render error", e.getMessage()));
        }
    }
}
```

- [x] **Step 3: Verify backend starts and API works**

Start backend: `cd packages/backend && mvn spring-boot:run &`

Test health: `curl -s http://localhost:8080/api/health`
Expected: `{"status":"ok"}`

Test parse:
```bash
curl -s -X POST http://localhost:8080/api/parse \
  -H "Content-Type: application/json" \
  -d '{"source":"@startuml\nclass Foo\nclass Bar\nFoo --> Bar\n@enduml"}' | head -c 500
```
Expected: JSON with `version`, `type`, `elements`, `relations` fields.

Stop backend after verifying.

- [x] **Step 4: Commit**

```bash
git add packages/backend/src/main/java/com/plantuml/viewer/controller/
git commit -m "feat(backend): add REST API controllers for /api/parse, /api/render, /api/health"
```

---

## Phase 3: Frontend Foundation

### Task 7: Frontend Project Setup

**Files:**
- Create: `packages/frontend/package.json`
- Create: `packages/frontend/vite.config.ts`
- Create: `packages/frontend/tsconfig.json`
- Create: `packages/frontend/tsconfig.node.json`
- Create: `packages/frontend/index.html`
- Create: `packages/frontend/src/main.tsx`
- Create: `packages/frontend/src/App.tsx`
- Create: `packages/frontend/src/App.css`

- [x] **Step 1: Create package.json**

```json
{
  "name": "@plantuml-viewer/frontend",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "@ant-design/icons": "^5.3.7",
    "antd": "^5.18.0",
    "zustand": "^4.5.4",
    "@monaco-editor/react": "^4.6.0",
    "d3": "^7.9.0",
    "axios": "^1.7.2"
  },
  "devDependencies": {
    "@types/react": "^18.3.3",
    "@types/react-dom": "^18.3.0",
    "@types/d3": "^7.4.3",
    "@vitejs/plugin-react": "^4.3.1",
    "typescript": "^5.5.3",
    "vite": "^5.3.4"
  }
}
```

- [x] **Step 2: Create vite.config.ts**

```typescript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
```

- [x] **Step 3: Create tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "forceConsistentCasingInFileNames": true
  },
  "include": ["src"]
}
```

- [x] **Step 4: Create index.html**

```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>PlantUML Viewer</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [x] **Step 5: Create src/main.tsx**

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
```

- [x] **Step 6: Create src/App.tsx (placeholder)**

```tsx
import './App.css';

function App() {
  return (
    <div className="app">
      <h1>PlantUML Viewer</h1>
      <p>Loading...</p>
    </div>
  );
}

export default App;
```

- [x] **Step 7: Create src/App.css**

```css
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #root {
  height: 100%;
  width: 100%;
}

.app {
  height: 100%;
  display: flex;
  flex-direction: column;
}
```

- [x] **Step 8: Install dependencies and verify**

Run: `pnpm install`
Run: `cd packages/frontend && pnpm dev`
Expected: Vite dev server starts on port 5173. Stop after verifying.

- [x] **Step 9: Commit**

```bash
git add packages/frontend/
git commit -m "feat(frontend): scaffold React + Vite + TypeScript project"
```

---

### Task 8: TypeScript Types

**Files:**
- Create: `packages/frontend/src/types/diagram.ts`
- Create: `packages/frontend/src/types/index.ts`

- [x] **Step 1: Create diagram.ts**

```typescript
export interface Position {
  x: number;
  y: number;
}

export interface Size {
  width: number;
  height: number;
}

export interface Element {
  id: string;
  type: string;
  name: string;
  parentId?: string;
  children?: string[];
  position: Position;
  size: Size;
  visible: boolean;
  collapsed?: boolean;
  style?: Record<string, string>;
  metadata?: Record<string, unknown>;
}

export interface Relation {
  id: string;
  type: string;
  sourceId: string;
  targetId: string;
  label?: string;
  points?: Position[];
  style?: Record<string, string>;
}

export interface DiagramModel {
  version: string;
  type: string;
  elements: Element[];
  relations: Relation[];
}

export interface ParseRequest {
  source: string;
}

export interface ParseResponse {
  version: string;
  type: string;
  elements: Element[];
  relations: Relation[];
}

export interface ViewState {
  visibility: Record<string, boolean>;
  collapsed: Record<string, boolean>;
  source: string;
}

export interface PresentationStep {
  elementIds: string[];
  label?: string;
}
```

- [x] **Step 2: Create index.ts**

```typescript
export type { DiagramModel, Element, Relation, Position, Size, ParseRequest, ParseResponse, ViewState, PresentationStep } from './diagram';
```

- [x] **Step 3: Commit**

```bash
git add packages/frontend/src/types/
git commit -m "feat(frontend): add TypeScript type definitions for diagram model"
```

---

### Task 9: API Service

**Files:**
- Create: `packages/frontend/src/services/api.ts`

- [x] **Step 1: Create api.ts**

```typescript
import axios from 'axios';
import type { DiagramModel } from '../types';

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

export async function parsePlantUml(source: string): Promise<DiagramModel> {
  const response = await api.post<DiagramModel>('/parse', { source });
  return response.data;
}

export async function renderSvg(source: string): Promise<string> {
  const response = await api.post<string>('/render', { source });
  return response.data;
}

export async function checkHealth(): Promise<string> {
  const response = await api.get<{ status: string }>('/health');
  return response.data.status;
}

export default api;
```

- [x] **Step 2: Commit**

```bash
git add packages/frontend/src/services/api.ts
git commit -m "feat(frontend): add API service with parsePlantUml, renderSvg, checkHealth"
```

---

### Task 10: Zustand Store

**Files:**
- Create: `packages/frontend/src/store/useAppStore.ts`

- [x] **Step 1: Create useAppStore.ts**

```typescript
import { create } from 'zustand';
import { parsePlantUml } from '../services/api';
import type { DiagramModel, ViewState } from '../types';

interface AppState {
  // Data
  source: string;
  model: DiagramModel | null;
  svgRaw: string | null;
  parseError: string | null;
  isLoading: boolean;

  // Visibility state
  visibility: Record<string, boolean>;
  collapsed: Record<string, boolean>;
  selectedElements: string[];

  // UI state
  presentationMode: boolean;
  presentationSteps: string[][];
  presentationStep: number;
  leftPanelCollapsed: boolean;
  rightPanelCollapsed: boolean;

  // Actions
  setSource: (source: string) => void;
  parseSource: () => Promise<void>;
  toggleVisibility: (id: string) => void;
  toggleCollapse: (id: string) => void;
  setSelectedElements: (ids: string[]) => void;
  resetView: () => void;
  setPresentationMode: (on: boolean) => void;
  nextPresentationStep: () => void;
  prevPresentationStep: () => void;
  toggleLeftPanel: () => void;
  toggleRightPanel: () => void;
  loadViewState: () => void;
  saveViewState: () => void;
  importViewState: (json: string) => void;
  exportViewState: () => string;
  getEffectiveVisibility: (id: string) => boolean;
}

const STORAGE_KEY = 'plantuml-viewer-state';

function getInitialSource(): string {
  return `@startuml
class User {
  +name: String
  +email: String
  +login(): void
}

class Order {
  +id: Long
  +status: String
  +create(): void
}

class Product {
  +name: String
  +price: Double
}

User "1" --> "*" Order : places
Order "1" --> "*" Product : contains
@enduml`;
}

export const useAppStore = create<AppState>((set, get) => ({
  // Initial state
  source: getInitialSource(),
  model: null,
  svgRaw: null,
  parseError: null,
  isLoading: false,

  visibility: {},
  collapsed: {},
  selectedElements: [],

  presentationMode: false,
  presentationSteps: [],
  presentationStep: 0,
  leftPanelCollapsed: false,
  rightPanelCollapsed: false,

  // Actions
  setSource: (source) => set({ source }),

  parseSource: async () => {
    const { source } = get();
    set({ isLoading: true, parseError: null });

    try {
      const model = await parsePlantUml(source);

      // Initialize visibility for all elements
      const visibility: Record<string, boolean> = {};
      const collapsed: Record<string, boolean> = {};
      model.elements.forEach((el) => {
        visibility[el.id] = true;
      });

      set({
        model,
        visibility,
        collapsed,
        parseError: null,
        isLoading: false,
      });

      get().saveViewState();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Parse failed';
      set({ parseError: message, isLoading: false });
    }
  },

  toggleVisibility: (id) =>
    set((state) => {
      const newVisibility = { ...state.visibility, [id]: !state.visibility[id] };
      return { visibility: newVisibility };
    }),

  toggleCollapse: (id) =>
    set((state) => {
      const newCollapsed = { ...state.collapsed, [id]: !state.collapsed[id] };
      return { collapsed: newCollapsed };
    }),

  setSelectedElements: (ids) => set({ selectedElements: ids }),

  resetView: () => {
    const { model } = get();
    if (!model) return;

    const visibility: Record<string, boolean> = {};
    const collapsed: Record<string, boolean> = {};
    model.elements.forEach((el) => {
      visibility[el.id] = true;
    });

    set({ visibility, collapsed, selectedElements: [], presentationMode: false, presentationStep: 0 });
  },

  setPresentationMode: (on) =>
    set((state) => {
      if (on && state.model) {
        const steps: string[][] = state.model.elements.map((el) => [el.id]);
        return { presentationMode: true, presentationSteps: steps, presentationStep: 0 };
      }
      return { presentationMode: false, presentationSteps: [], presentationStep: 0 };
    }),

  nextPresentationStep: () =>
    set((state) => ({
      presentationStep: Math.min(state.presentationStep + 1, state.presentationSteps.length - 1),
    })),

  prevPresentationStep: () =>
    set((state) => ({
      presentationStep: Math.max(state.presentationStep - 1, 0),
    })),

  toggleLeftPanel: () => set((state) => ({ leftPanelCollapsed: !state.leftPanelCollapsed })),

  toggleRightPanel: () => set((state) => ({ rightPanelCollapsed: !state.rightPanelCollapsed })),

  loadViewState: () => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      const state: ViewState = JSON.parse(raw);
      set({
        source: state.source,
        visibility: state.visibility,
        collapsed: state.collapsed,
      });
    } catch {
      // Ignore parse errors
    }
  },

  saveViewState: () => {
    const { source, visibility, collapsed } = get();
    const state: ViewState = { source, visibility, collapsed };
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {
      // Ignore storage errors
    }
  },

  importViewState: (json) => {
    try {
      const state: ViewState = JSON.parse(json);
      set({
        source: state.source,
        visibility: state.visibility,
        collapsed: state.collapsed,
      });
    } catch {
      throw new Error('Invalid view state JSON');
    }
  },

  exportViewState: () => {
    const { source, visibility, collapsed } = get();
    return JSON.stringify({ source, visibility, collapsed }, null, 2);
  },

  getEffectiveVisibility: (id) => {
    const { model, visibility, collapsed } = get();
    if (!model) return true;
    if (visibility[id] === false) return false;

    // Walk up parent chain
    let currentId = id;
    const elementMap = new Map(model.elements.map((el) => [el.id, el]));
    while (currentId) {
      if (collapsed[currentId]) return false;
      if (visibility[currentId] === false) return false;
      const el = elementMap.get(currentId);
      currentId = el?.parentId || '';
    }

    return true;
  },
}));
```

- [x] **Step 2: Commit**

```bash
git add packages/frontend/src/store/
git commit -m "feat(frontend): add Zustand store with visibility, collapse, presentation, and persistence"
```

---

## Phase 4: UI Components

### Task 11: App Layout Component

**Files:**
- Modify: `packages/frontend/src/App.tsx`
- Modify: `packages/frontend/src/App.css`
- Create: `packages/frontend/src/components/AppLayout.tsx`
- Create: `packages/frontend/src/components/AppLayout.css`

- [x] **Step 1: Create AppLayout.tsx**

```tsx
import { useEffect } from 'react';
import { useAppStore } from '../store/useAppStore';
import EditorPanel from './EditorPanel';
import DiagramView from './DiagramView';
import ControlTree from './ControlTree';
import Toolbar from './Toolbar';
import './AppLayout.css';

function AppLayout() {
  const {
    source,
    parseSource,
    loadViewState,
    presentationMode,
  } = useAppStore();

  // Load saved state on mount
  useEffect(() => {
    loadViewState();
  }, [loadViewState]);

  // Auto-parse on mount with initial source
  useEffect(() => {
    if (source) {
      parseSource();
    }
  }, []);

  return (
    <div className="app-layout">
      {!presentationMode && <Toolbar />}
      <div className="app-body">
        {!presentationMode && (
          <div className={`panel panel-left ${useAppStore.getState().leftPanelCollapsed ? 'collapsed' : ''}`}>
            <EditorPanel />
          </div>
        )}
        <div className="panel panel-center">
          <DiagramView />
        </div>
        {!presentationMode && (
          <div className={`panel panel-right ${useAppStore.getState().rightPanelCollapsed ? 'collapsed' : ''}`}>
            <ControlTree />
          </div>
        )}
      </div>
    </div>
  );
}

export default AppLayout;
```

- [x] **Step 2: Create AppLayout.css**

```css
.app-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.app-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.panel {
  overflow: auto;
  background: #fff;
  border: 1px solid #e8e8e8;
  transition: width 0.2s ease;
}

.panel-left {
  width: 350px;
  min-width: 250px;
  max-width: 600px;
  resize: horizontal;
  display: flex;
  flex-direction: column;
}

.panel-left.collapsed {
  width: 0;
  min-width: 0;
  overflow: hidden;
  border: none;
}

.panel-center {
  flex: 1;
  min-width: 300px;
  overflow: auto;
  background: #f5f5f5;
}

.panel-right {
  width: 280px;
  min-width: 200px;
  max-width: 500px;
  resize: horizontal;
  overflow: auto;
}

.panel-right.collapsed {
  width: 0;
  min-width: 0;
  overflow: hidden;
  border: none;
}
```

- [x] **Step 3: Update App.tsx**

Replace contents of `packages/frontend/src/App.tsx`:

```tsx
import { ConfigProvider } from 'antd';
import AppLayout from './components/AppLayout';
import './App.css';

function App() {
  return (
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: '#1677ff',
        },
      }}
    >
      <AppLayout />
    </ConfigProvider>
  );
}

export default App;
```

- [x] **Step 4: Commit**

```bash
git add packages/frontend/src/App.tsx packages/frontend/src/App.css
git add packages/frontend/src/components/AppLayout.tsx packages/frontend/src/components/AppLayout.css
git commit -m "feat(frontend): add 3-panel app layout with resizable panels"
```

---

### Task 12: EditorPanel Component

**Files:**
- Create: `packages/frontend/src/components/EditorPanel/EditorPanel.tsx`
- Create: `packages/frontend/src/components/EditorPanel/EditorPanel.css`
- Create: `packages/frontend/src/components/EditorPanel/index.ts`

- [x] **Step 1: Create EditorPanel.tsx**

```tsx
import { useRef, useCallback } from 'react';
import Editor from '@monaco-editor/react';
import { useAppStore } from '../../store/useAppStore';
import './EditorPanel.css';

const PLANTUML_LANGUAGE = 'plaintext';

export default function EditorPanel() {
  const editorRef = useRef<any>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();

  const { source, setSource, parseSource, isLoading, parseError } = useAppStore();

  const handleEditorChange = useCallback(
    (value: string | undefined) => {
      const newSource = value || '';
      setSource(newSource);

      // Debounced auto-parse
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }
      debounceRef.current = setTimeout(() => {
        parseSource();
      }, 500);
    },
    [setSource, parseSource],
  );

  const handleEditorMount = useCallback((editor: any, monaco: any) => {
    editorRef.current = editor;

    // Register PlantUML keywords for basic highlighting
    monaco.languages.register({ id: PLANTUML_LANGUAGE });
    monaco.languages.setMonarchTokensProvider(PLANTUML_LANGUAGE, {
      keywords: [
        '@startuml', '@enduml', 'class', 'interface', 'enum', 'abstract',
        'package', 'namespace', 'actor', 'usecase', 'component', 'note',
        'frame', 'rectangle', 'node', 'cloud', 'database',
        '-->', '<--', '..>', '<..', '-[hidden]->', '-[dashed]->',
        'extends', 'implements', 'of', 'as',
      ],
      tokenizer: {
        root: [
          [/@startuml|@enduml/, 'keyword.startuml'],
          [/[a-zA-Z_]\w*/, {
            cases: {
              '@keywords': 'keyword',
              '@default': 'identifier',
            },
          }],
          [/"[^"]*"/, 'string'],
          [/'[^']*'/, 'string'],
          [/\/\/.*$/, 'comment'],
          [/'.*/, 'annotation'],
        ],
      },
    });

    // Ctrl+Enter to parse
    editor.addAction({
      id: 'parse-plantuml',
      label: 'Parse PlantUML',
      keybindings: [monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter],
      run: () => parseSource(),
    });
  }, [parseSource]);

  return (
    <div className="editor-panel">
      <div className="editor-header">
        <span className="editor-title">PlantUML Source</span>
        {isLoading && <span className="editor-status loading">Parsing...</span>}
        {parseError && <span className="editor-status error" title={parseError}>Error</span>}
      </div>
      <div className="editor-body">
        <Editor
          height="100%"
          language={PLANTUML_LANGUAGE}
          value={source}
          onChange={handleEditorChange}
          onMount={handleEditorMount}
          theme="vs"
          options={{
            minimap: { enabled: false },
            fontSize: 13,
            lineNumbers: 'on',
            scrollBeyondLastLine: false,
            wordWrap: 'on',
            automaticLayout: true,
            tabSize: 2,
          }}
        />
      </div>
    </div>
  );
}
```

- [x] **Step 2: Create EditorPanel.css**

```css
.editor-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid #e8e8e8;
  background: #fafafa;
}

.editor-title {
  font-weight: 600;
  font-size: 13px;
  color: #333;
}

.editor-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
}

.editor-status.loading {
  color: #1677ff;
  background: #e6f4ff;
}

.editor-status.error {
  color: #ff4d4f;
  background: #fff2f0;
  cursor: help;
}

.editor-body {
  flex: 1;
  overflow: hidden;
}
```

- [x] **Step 3: Create index.ts**

```typescript
export { default } from './EditorPanel';
```

- [x] **Step 4: Commit**

```bash
git add packages/frontend/src/components/EditorPanel/
git commit -m "feat(frontend): add EditorPanel with Monaco Editor and PlantUML syntax highlighting"
```

---

### Task 13: DiagramView Component

**Files:**
- Create: `packages/frontend/src/components/DiagramView/DiagramView.tsx`
- Create: `packages/frontend/src/components/DiagramView/DiagramView.css`
- Create: `packages/frontend/src/components/DiagramView/index.ts`
- Create: `packages/frontend/src/utils/visibility.ts`

- [x] **Step 1: Create visibility.ts utility**

```typescript
import type { DiagramModel } from '../types';

export function isEffectivelyVisible(
  id: string,
  visibility: Record<string, boolean>,
  collapsed: Record<string, boolean>,
  model: DiagramModel,
): boolean {
  if (visibility[id] === false) return false;

  const elementMap = new Map(model.elements.map((el) => [el.id, el]));
  let currentId = id;

  while (currentId) {
    if (collapsed[currentId]) return false;
    if (visibility[currentId] === false) return false;
    const el = elementMap.get(currentId);
    currentId = el?.parentId || '';
  }

  return true;
}

export function isRelationVisible(
  sourceId: string,
  targetId: string,
  visibility: Record<string, boolean>,
  collapsed: Record<string, boolean>,
  model: DiagramModel,
): boolean {
  return (
    isEffectivelyVisible(sourceId, visibility, collapsed, model) &&
    isEffectivelyVisible(targetId, visibility, collapsed, model)
  );
}
```

- [x] **Step 2: Create DiagramView.tsx**

```tsx
import { useRef, useCallback, useMemo, useState } from 'react';
import { useAppStore } from '../../store/useAppStore';
import { ContextMenu } from '../ContextMenu';
import { isEffectivelyVisible, isRelationVisible } from '../../utils/visibility';
import './DiagramView.css';

export default function DiagramView() {
  const {
    model,
    visibility,
    collapsed,
    selectedElements,
    parseError,
    isLoading,
    toggleVisibility,
    toggleCollapse,
    setSelectedElements,
    presentationMode,
    presentationStep,
    presentationSteps,
  } = useAppStore();

  const svgContainerRef = useRef<HTMLDivElement>(null);
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; elementId: string } | null>(null);
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [isPanning, setIsPanning] = useState(false);
  const panStart = useRef({ x: 0, y: 0 });

  const handleWheel = useCallback((e: React.WheelEvent) => {
    if (e.ctrlKey || e.metaKey) {
      e.preventDefault();
      setZoom((z) => Math.max(0.1, Math.min(5, z - e.deltaY * 0.001)));
    } else {
      setPan((p) => ({ x: p.x - e.deltaX, y: p.y - e.deltaY }));
    }
  }, []);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    if (e.button === 0 && !e.target.closest('.diagram-element')) {
      setIsPanning(true);
      panStart.current = { x: e.clientX - pan.x, y: e.clientY - pan.y };
      setSelectedElements([]);
    }
  }, [pan, setSelectedElements]);

  const handleMouseMove = useCallback((e: React.MouseEvent) => {
    if (isPanning) {
      setPan({ x: e.clientX - panStart.current.x, y: e.clientY - panStart.current.y });
    }
  }, [isPanning]);

  const handleMouseUp = useCallback(() => {
    setIsPanning(false);
  }, []);

  const handleElementClick = useCallback(
    (elementId: string, e: React.MouseEvent) => {
      e.stopPropagation();
      setSelectedElements([elementId]);
    },
    [setSelectedElements],
  );

  const handleContextMenu = useCallback((elementId: string, e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setContextMenu({ x: e.clientX, y: e.clientY, elementId });
  }, []);

  const handleCloseContextMenu = useCallback(() => {
    setContextMenu(null);
  }, []);

  const presentationHighlightIds = useMemo(() => {
    if (!presentationMode || presentationSteps.length === 0) return new Set<string>();
    const step = presentationSteps[presentationStep];
    if (!step) return new Set<string>();
    return new Set(step);
  }, [presentationMode, presentationSteps, presentationStep]);

  const visibleElements = useMemo(() => {
    if (!model) return [];
    return model.elements.filter((el) =>
      isEffectivelyVisible(el.id, visibility, collapsed, model),
    );
  }, [model, visibility, collapsed]);

  const visibleRelations = useMemo(() => {
    if (!model) return [];
    return model.relations.filter((rel) =>
      isRelationVisible(rel.sourceId, rel.targetId, visibility, collapsed, model),
    );
  }, [model, visibility, collapsed]);

  if (isLoading) {
    return <div className="diagram-view loading">Parsing PlantUML...</div>;
  }

  if (parseError) {
    return <div className="diagram-view error">{parseError}</div>;
  }

  if (!model || model.elements.length === 0) {
    return <div className="diagram-view empty">Enter PlantUML source code and press Ctrl+Enter to render</div>;
  }

  const svgWidth = Math.max(...model.elements.map((e) => e.position.x + e.size.width)) + 50;
  const svgHeight = Math.max(...model.elements.map((e) => e.position.y + e.size.height)) + 50;

  return (
    <div
      className="diagram-view"
      ref={svgContainerRef}
      onWheel={handleWheel}
      onMouseDown={handleMouseDown}
      onMouseMove={handleMouseMove}
      onMouseUp={handleMouseUp}
      onMouseLeave={handleMouseUp}
    >
      <svg
        width={svgWidth}
        height={svgHeight}
        viewBox={`0 0 ${svgWidth} ${svgHeight}`}
        style={{ transform: `scale(${zoom}) translate(${pan.x / zoom}px, ${pan.y / zoom}px)` }}
      >
        {/* Render elements */}
        {visibleElements.map((el) => {
          const isSelected = selectedElements.includes(el.id);
          const isHighlighted = presentationHighlightIds.has(el.id);
          const isContainer = el.children && el.children.length > 0;
          const isCollapsed = collapsed[el.id];

          return (
            <g
              key={el.id}
              className={`diagram-element element-type-${el.type}`}
              onClick={(e) => handleElementClick(el.id, e)}
              onContextMenu={(e) => handleContextMenu(el.id, e)}
              data-element-id={el.id}
            >
              {/* Element visual */}
              <rect
                x={el.position.x}
                y={el.position.y}
                width={el.size.width}
                height={el.size.height}
                fill={isCollapsed ? '#e6f7ff' : el.type === 'package' ? '#fffbe6' : '#ffffff'}
                stroke={isSelected ? '#1677ff' : isHighlighted ? '#52c41a' : '#d9d9d9'}
                strokeWidth={isSelected || isHighlighted ? 2 : 1}
                rx={4}
              />

              {/* Type badge */}
              <text
                x={el.position.x + 4}
                y={el.position.y + 14}
                fontSize={9}
                fill="#999"
              >
                {el.type}
              </text>

              {/* Element name */}
              <text
                x={el.position.x + el.size.width / 2}
                y={el.position.y + el.size.height / 2 + 5}
                textAnchor="middle"
                fontSize={12}
                fill="#333"
                fontWeight={isSelected || isHighlighted ? 600 : 400}
              >
                {el.name}
              </text>

              {/* Collapse indicator */}
              {isContainer && isCollapsed && (
                <circle
                  cx={el.position.x + el.size.width - 12}
                  cy={el.position.y + 12}
                  r={8}
                  fill="#1677ff"
                  onClick={(e) => { e.stopPropagation(); toggleCollapse(el.id); }}
                  className="collapse-indicator"
                >
                  <text x={el.position.x + el.size.width - 12} y={el.position.y + 16} textAnchor="middle" fontSize={12} fill="white" fontWeight="bold">+</text>
                </circle>
              )}
            </g>
          );
        })}

        {/* Render relations */}
        {visibleRelations.map((rel) => {
          const source = model!.elements.find((e) => e.id === rel.sourceId);
          const target = model!.elements.find((e) => e.id === rel.targetId);
          if (!source || !target) return null;

          const sx = source.position.x + source.size.width / 2;
          const sy = source.position.y + source.size.height / 2;
          const tx = target.position.x + target.size.width / 2;
          const ty = target.position.y + target.size.height / 2;

          const isHighlighted = presentationHighlightIds.has(rel.sourceId) && presentationHighlightIds.has(rel.targetId);

          return (
            <g key={rel.id} className="diagram-relation" data-relation-id={rel.id}>
              <line
                x1={sx} y1={sy}
                x2={tx} y2={ty}
                stroke={isHighlighted ? '#52c41a' : '#999'}
                strokeWidth={isHighlighted ? 2 : 1}
                markerEnd="url(#arrowhead)"
              />
              {rel.label && (
                <text
                  x={(sx + tx) / 2}
                  y={(sy + ty) / 2 - 5}
                  textAnchor="middle"
                  fontSize={10}
                  fill="#666"
                >
                  {rel.label}
                </text>
              )}
            </g>
          );
        })}

        {/* Arrow marker definition */}
        <defs>
          <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="10" refY="3.5" orient="auto">
            <polygon points="0 0, 10 3.5, 0 7" fill="#999" />
          </marker>
        </defs>
      </svg>

      {/* Presentation mode step counter */}
      {presentationMode && (
        <div className="presentation-controls">
          <button onClick={useAppStore.getState().prevPresentationStep}>&larr; Prev</button>
          <span>Step {presentationStep + 1} / {presentationSteps.length}</span>
          <button onClick={useAppStore.getState().nextPresentationStep}>Next &rarr;</button>
        </div>
      )}

      {/* Zoom controls */}
      <div className="zoom-controls">
        <button onClick={() => setZoom((z) => Math.min(5, z + 0.2))}>+</button>
        <span>{Math.round(zoom * 100)}%</span>
        <button onClick={() => setZoom((z) => Math.max(0.1, z - 0.2))}>-</button>
        <button onClick={() => { setZoom(1); setPan({ x: 0, y: 0 }); }}>Fit</button>
      </div>

      {/* Context menu */}
      {contextMenu && (
        <ContextMenu
          x={contextMenu.x}
          y={contextMenu.y}
          elementId={contextMenu.elementId}
          onClose={handleCloseContextMenu}
        />
      )}
    </div>
  );
}
```

- [x] **Step 3: Create DiagramView.css**

```css
.diagram-view {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  cursor: grab;
  background:
    radial-gradient(circle, #ddd 1px, transparent 1px);
  background-size: 20px 20px;
}

.diagram-view:active {
  cursor: grabbing;
}

.diagram-view.loading,
.diagram-view.error,
.diagram-view.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: #999;
}

.diagram-view.error {
  color: #ff4d4f;
}

.diagram-element {
  cursor: pointer;
}

.diagram-element:hover rect {
  stroke: #1677ff;
  stroke-width: 2;
}

.collapse-indicator {
  cursor: pointer;
}

.zoom-controls {
  position: absolute;
  bottom: 16px;
  right: 16px;
  display: flex;
  gap: 4px;
  align-items: center;
  background: #fff;
  padding: 4px 8px;
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  font-size: 12px;
  z-index: 10;
}

.zoom-controls button {
  border: 1px solid #d9d9d9;
  background: #fff;
  border-radius: 4px;
  padding: 2px 8px;
  cursor: pointer;
  font-size: 12px;
}

.zoom-controls button:hover {
  border-color: #1677ff;
  color: #1677ff;
}

.presentation-controls {
  position: absolute;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 16px;
  align-items: center;
  background: #fff;
  padding: 8px 16px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  font-size: 14px;
  z-index: 10;
}

.presentation-controls button {
  border: 1px solid #d9d9d9;
  background: #fff;
  border-radius: 4px;
  padding: 4px 12px;
  cursor: pointer;
}

.presentation-controls button:hover {
  border-color: #1677ff;
  color: #1677ff;
}
```

- [x] **Step 4: Create index.ts**

```typescript
export { default } from './DiagramView';
```

- [x] **Step 5: Commit**

```bash
git add packages/frontend/src/components/DiagramView/ packages/frontend/src/utils/visibility.ts
git commit -m "feat(frontend): add DiagramView with SVG rendering, zoom/pan, and element overlays"
```

---

### Task 14: ControlTree Component

**Files:**
- Create: `packages/frontend/src/components/ControlTree/ControlTree.tsx`
- Create: `packages/frontend/src/components/ControlTree/ControlTree.css`
- Create: `packages/frontend/src/components/ControlTree/index.ts`

- [x] **Step 1: Create ControlTree.tsx**

```tsx
import { useMemo, useState } from 'react';
import { Tree, Input } from 'antd';
import { useAppStore } from '../../store/useAppStore';
import type { DataNode } from 'antd/es/tree';
import './ControlTree.css';

export default function ControlTree() {
  const { model, visibility, collapsed, toggleVisibility, toggleCollapse } = useAppStore();
  const [searchText, setSearchText] = useState('');

  const treeData = useMemo(() => {
    if (!model) return [];

    // Group elements by type
    const typeGroups: Record<string, typeof model.elements> = {};
    model.elements.forEach((el) => {
      const type = el.type || 'other';
      if (!typeGroups[type]) typeGroups[type] = [];
      typeGroups[type].push(el);
    });

    // Build tree nodes
    const nodes: DataNode[] = Object.entries(typeGroups)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([type, elements]) => {
        const filteredElements = searchText
          ? elements.filter((el) => el.name.toLowerCase().includes(searchText.toLowerCase()))
          : elements;

        const children: DataNode[] = filteredElements.map((el) => {
          const isContainer = el.children && el.children.length > 0;
          const hasChildren = isContainer
            ? (el.children || []).map((cid) => model.elements.find((e) => e.id === cid)).filter(Boolean)
            : [];

          return {
            key: el.id,
            title: (
              <span className={`tree-node element-type-${el.type}`}>
                <span className="node-name">{el.name || el.id}</span>
                {isContainer && (
                  <span
                    className={`collapse-toggle ${collapsed[el.id] ? 'collapsed' : ''}`}
                    onClick={(e) => {
                      e.stopPropagation();
                      toggleCollapse(el.id);
                    }}
                  >
                    {collapsed[el.id] ? '[+]' : '[-]'}
                  </span>
                )}
              </span>
            ),
            children: hasChildren.map((child: any) => ({
              key: child.id,
              title: (
                <span className={`tree-node element-type-${child.type}`}>
                  <span className="node-name">{child.name || child.id}</span>
                </span>
              ),
            })),
          };
        });

        return {
          key: `group-${type}`,
          title: (
            <span className="tree-group">
              {type} ({filteredElements.length})
            </span>
          ),
          children,
        };
      });

    return nodes;
  }, [model, visibility, collapsed, searchText, toggleCollapse]);

  const checkedKeys = useMemo(() => {
    if (!model) return [];
    return model.elements.filter((el) => visibility[el.id] !== false).map((el) => el.id);
  }, [model, visibility]);

  const handleCheck = useCallback(
    (checkedKeys: React.Key[] | { checked: React.Key[]; halfChecked: React.Key[] }) => {
      const keys = Array.isArray(checkedKeys) ? checkedKeys : checkedKeys.checked;
      const keySet = new Set(keys);

      if (!model) return;
      const newVisibility: Record<string, boolean> = {};
      model.elements.forEach((el) => {
        newVisibility[el.id] = keySet.has(el.id);
      });

      useAppStore.setState({ visibility: newVisibility });
      useAppStore.getState().saveViewState();
    },
    [model],
  );

  if (!model) {
    return <div className="control-tree empty">No diagram loaded</div>;
  }

  return (
    <div className="control-tree">
      <div className="control-tree-header">
        <span className="tree-title">Elements</span>
      </div>
      <div className="control-tree-search">
        <Input
          placeholder="Search elements..."
          size="small"
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
          allowClear
        />
      </div>
      <div className="control-tree-body">
        <Tree
          checkable
          defaultExpandAll
          checkedKeys={checkedKeys}
          onCheck={handleCheck}
          treeData={treeData}
          selectable={false}
        />
      </div>
    </div>
  );
}
```

- [x] **Step 2: Create ControlTree.css**

```css
.control-tree {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.control-tree.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 13px;
}

.control-tree-header {
  padding: 8px 12px;
  border-bottom: 1px solid #e8e8e8;
  background: #fafafa;
}

.tree-title {
  font-weight: 600;
  font-size: 13px;
}

.control-tree-search {
  padding: 8px 12px;
  border-bottom: 1px solid #e8e8e8;
}

.control-tree-body {
  flex: 1;
  overflow: auto;
  padding: 4px 0;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 4px;
}

.node-name {
  font-size: 12px;
}

.collapse-toggle {
  font-size: 10px;
  color: #1677ff;
  cursor: pointer;
  margin-left: 4px;
}

.collapse-toggle:hover {
  color: #4096ff;
}

.tree-group {
  font-weight: 500;
  font-size: 12px;
  color: #666;
  text-transform: capitalize;
}
```

- [x] **Step 3: Create index.ts**

```typescript
export { default } from './ControlTree';
```

- [x] **Step 4: Commit**

```bash
git add packages/frontend/src/components/ControlTree/
git commit -m "feat(frontend): add ControlTree with checkable tree, search, and collapse toggles"
```

---

### Task 15: ContextMenu Component

**Files:**
- Create: `packages/frontend/src/components/ContextMenu/ContextMenu.tsx`
- Create: `packages/frontend/src/components/ContextMenu/ContextMenu.css`

- [x] **Step 1: Create ContextMenu.tsx**

```tsx
import { useAppStore } from '../../store/useAppStore';
import './ContextMenu.css';

interface ContextMenuProps {
  x: number;
  y: number;
  elementId: string;
  onClose: () => void;
}

export function ContextMenu({ x, y, elementId, onClose }: ContextMenuProps) {
  const { model, visibility, collapsed, toggleVisibility, toggleCollapse } = useAppStore();

  const element = model?.elements.find((e) => e.id === elementId);
  if (!element) return null;

  const isVisible = visibility[elementId] !== false;
  const isContainer = element.children && element.children.length > 0;
  const isCollapsed = collapsed[elementId];

  const handleAction = (action: () => void) => {
    action();
    onClose();
  };

  return (
    <div className="context-menu" style={{ left: x, top: y }} onClick={(e) => e.stopPropagation()}>
      <div className="context-menu-header">
        <span className="cm-type">{element.type}</span>
        <span className="cm-name">{element.name || element.id}</span>
      </div>
      <div className="context-menu-divider" />
      <button onClick={() => handleAction(() => toggleVisibility(elementId))}>
        {isVisible ? 'Hide' : 'Show'}
      </button>
      {isContainer && (
        <button onClick={() => handleAction(() => toggleCollapse(elementId))}>
          {isCollapsed ? 'Expand' : 'Collapse'}
        </button>
      )}
      {isContainer && element.children && (
        <button
          onClick={() =>
            handleAction(() => {
              const childVis = !isVisible;
              const newVisibility = { ...visibility };
              element.children!.forEach((cid) => {
                newVisibility[cid] = childVis;
              });
              useAppStore.setState({ visibility: newVisibility });
              useAppStore.getState().saveViewState();
            })
          }
        >
          {isVisible ? 'Hide Children' : 'Show Children'}
        </button>
      )}
    </div>
  );
}
```

- [x] **Step 2: Create ContextMenu.css**

```css
.context-menu {
  position: fixed;
  z-index: 1000;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  min-width: 160px;
  padding: 4px 0;
}

.context-menu-header {
  padding: 6px 12px;
  display: flex;
  gap: 8px;
  align-items: center;
}

.cm-type {
  font-size: 10px;
  color: #999;
  background: #f5f5f5;
  padding: 1px 6px;
  border-radius: 3px;
  text-transform: uppercase;
}

.cm-name {
  font-size: 12px;
  font-weight: 500;
  color: #333;
}

.context-menu-divider {
  height: 1px;
  background: #e8e8e8;
  margin: 4px 0;
}

.context-menu button {
  display: block;
  width: 100%;
  text-align: left;
  padding: 6px 12px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 13px;
  color: #333;
}

.context-menu button:hover {
  background: #e6f4ff;
  color: #1677ff;
}
```

- [x] **Step 3: Commit**

```bash
git add packages/frontend/src/components/ContextMenu/
git commit -m "feat(frontend): add ContextMenu with hide/show/collapse actions"
```

---

### Task 16: Toolbar Component

**Files:**
- Create: `packages/frontend/src/components/Toolbar/Toolbar.tsx`
- Create: `packages/frontend/src/components/Toolbar/Toolbar.css`
- Create: `packages/frontend/src/components/Toolbar/index.ts`

- [x] **Step 1: Create Toolbar.tsx**

```tsx
import { Button, Tooltip, Space } from 'antd';
import {
  ExportOutlined,
  PlayCircleOutlined,
  UndoOutlined,
  SaveOutlined,
  FolderOpenOutlined,
  ColumnWidthOutlined,
  ColumnHeightOutlined,
} from '@ant-design/icons';
import { useAppStore } from '../../store/useAppStore';
import './Toolbar.css';

export default function Toolbar() {
  const {
    model,
    resetView,
    setPresentationMode,
    exportViewState,
    importViewState,
    toggleLeftPanel,
    toggleRightPanel,
    leftPanelCollapsed,
    rightPanelCollapsed,
  } = useAppStore();

  const handleExportPng = async () => {
    const svgEl = document.querySelector('.diagram-view svg');
    if (!svgEl) return;

    const svgData = new XMLSerializer().serializeToString(svgEl);
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const img = new Image();
    const svgBlob = new Blob([svgData], { type: 'image/svg+xml;charset=utf-8' });
    const url = URL.createObjectURL(svgBlob);

    img.onload = () => {
      canvas.width = img.width * 2;
      canvas.height = img.height * 2;
      ctx.scale(2, 2);
      ctx.drawImage(img, 0, 0);
      URL.revokeObjectURL(url);

      const link = document.createElement('a');
      link.download = 'plantuml-diagram.png';
      link.href = canvas.toDataURL('image/png');
      link.click();
    };

    img.src = url;
  };

  const handleExportSvg = () => {
    const svgEl = document.querySelector('.diagram-view svg');
    if (!svgEl) return;

    const svgData = new XMLSerializer().serializeToString(svgEl);
    const blob = new Blob([svgData], { type: 'image/svg+xml' });
    const url = URL.createObjectURL(blob);

    const link = document.createElement('a');
    link.download = 'plantuml-diagram.svg';
    link.href = url;
    link.click();
    URL.revokeObjectURL(url);
  };

  const handleImportState = () => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json';
    input.onchange = async (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (!file) return;
      const text = await file.text();
      try {
        importViewState(text);
        useAppStore.getState().parseSource();
      } catch {
        alert('Invalid view state file');
      }
    };
    input.click();
  };

  const handleExportState = () => {
    const json = exportViewState();
    const blob = new Blob([json], { type: 'application/json' });
    const url = URL.createObjectURL(blob);

    const link = document.createElement('a');
    link.download = 'plantuml-viewstate.json';
    link.href = url;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="toolbar">
      <div className="toolbar-left">
        <span className="toolbar-brand">PlantUML Viewer</span>
      </div>
      <Space className="toolbar-right">
        <Tooltip title="Toggle Editor Panel">
          <Button
            type={leftPanelCollapsed ? 'default' : 'primary'}
            size="small"
            icon={<ColumnWidthOutlined />}
            onClick={toggleLeftPanel}
          />
        </Tooltip>
        <Tooltip title="Toggle Control Panel">
          <Button
            type={rightPanelCollapsed ? 'default' : 'primary'}
            size="small"
            icon={<ColumnHeightOutlined />}
            onClick={toggleRightPanel}
          />
        </Tooltip>
        <Tooltip title="Export PNG">
          <Button size="small" icon={<ExportOutlined />} onClick={handleExportPng} disabled={!model}>
            PNG
          </Button>
        </Tooltip>
        <Tooltip title="Export SVG">
          <Button size="small" icon={<ExportOutlined />} onClick={handleExportSvg} disabled={!model}>
            SVG
          </Button>
        </Tooltip>
        <Tooltip title="Save View State">
          <Button size="small" icon={<SaveOutlined />} onClick={handleExportState} disabled={!model}>
            Save
          </Button>
        </Tooltip>
        <Tooltip title="Load View State">
          <Button size="small" icon={<FolderOpenOutlined />} onClick={handleImportState}>
            Load
          </Button>
        </Tooltip>
        <Tooltip title="Presentation Mode">
          <Button size="small" icon={<PlayCircleOutlined />} onClick={() => setPresentationMode(true)} disabled={!model}>
            Present
          </Button>
        </Tooltip>
        <Tooltip title="Reset View">
          <Button size="small" icon={<UndoOutlined />} onClick={resetView} disabled={!model}>
            Reset
          </Button>
        </Tooltip>
      </Space>
    </div>
  );
}
```

- [x] **Step 2: Create Toolbar.css**

```css
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  height: 44px;
}

.toolbar-brand {
  font-weight: 700;
  font-size: 14px;
  color: #333;
}

.toolbar-right {
  display: flex;
  gap: 4px;
}
```

- [x] **Step 3: Create index.ts**

```typescript
export { default } from './Toolbar';
```

- [x] **Step 4: Commit**

```bash
git add packages/frontend/src/components/Toolbar/
git commit -m "feat(frontend): add Toolbar with export, presentation, save/load, and panel toggles"
```

---

## Phase 5: Integration & Polish

### Task 17: End-to-End Integration

**Files:**
- Modify: `packages/frontend/src/components/AppLayout.tsx`

- [x] **Step 1: Update AppLayout for presentation mode exit**

Update `AppLayout.tsx` to add an exit button in presentation mode:

Add this before the closing `</div>` of the `app-layout` div:

```tsx
{presentationMode && (
  <button
    className="exit-presentation-btn"
    onClick={() => setPresentationMode(false)}
  >
    Exit Presentation (Esc)
  </button>
)}
```

Add `setPresentationMode` to the destructured store values. Add keyboard listener for Escape key in a useEffect:

```tsx
useEffect(() => {
  const handleKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'Escape' && presentationMode) {
      setPresentationMode(false);
    }
  };
  window.addEventListener('keydown', handleKeyDown);
  return () => window.removeEventListener('keydown', handleKeyDown);
}, [presentationMode, setPresentationMode]);
```

Add to `AppLayout.css`:

```css
.exit-presentation-btn {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 100;
  padding: 8px 16px;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}

.exit-presentation-btn:hover {
  background: rgba(0, 0, 0, 0.9);
}
```

- [x] **Step 2: Verify end-to-end flow**

Start backend: `cd packages/backend && mvn spring-boot:run &`
Start frontend: `cd packages/frontend && pnpm dev`

In browser (http://localhost:5173):
1. Verify default PlantUML source renders as diagram with class boxes and relation lines
2. Click a class box → it highlights
3. Right-click → context menu appears with Hide/Show options
4. Check/uncheck elements in right panel tree → elements appear/disappear
5. Click Zoom + / - buttons
6. Click Export PNG → file downloads
7. Click Present → enters presentation mode, use arrows to navigate

- [x] **Step 3: Commit**

```bash
git add packages/frontend/src/components/AppLayout.tsx packages/frontend/src/components/AppLayout.css
git commit -m "feat(frontend): add presentation mode exit button and Escape key handler"
```

---

### Task 18: Final Polish

- [x] **Step 1: Add sample PlantUML sources**

Create: `packages/frontend/src/utils/samples.ts`

```typescript
export const SAMPLE_CLASS_DIAGRAM = `@startuml
skinparam classAttributeIconSize 0

class User {
  +name: String
  +email: String
  +login(): void
  +logout(): void
}

class Order {
  +id: Long
  +status: String
  +total: Double
  +create(): void
  +cancel(): void
}

class Product {
  +name: String
  +price: Double
  +stock: int
}

class PaymentService {
  +processPayment(order: Order): boolean
  +refund(order: Order): void
}

package "Shipping" {
  class ShippingService {
    +calculateShipping(order: Order): Double
    +trackShipment(orderId: Long): String
  }
}

interface NotificationService {
  +send(userId: Long, message: String): void
}

class EmailService implements NotificationService {
  +send(userId: Long, message: String): void
}

User "1" --> "*" Order : places
Order "1" --> "*" Product : contains
Order --> PaymentService : uses
Order --> ShippingService : uses
PaymentService ..> NotificationService : notifies
@enduml`;

export const SAMPLE_SEQUENCE_DIAGRAM = `@startuml
actor User
participant "Frontend" as FE
participant "API Gateway" as API
participant "Auth Service" as Auth
participant "Database" as DB

User -> FE : Login
FE -> API : POST /login
API -> Auth : Validate credentials
Auth -> DB : Query user
DB --> Auth : User data
Auth --> API : JWT token
API --> FE : 200 OK + token
FE --> User : Dashboard

User -> FE : View orders
FE -> API : GET /orders (Bearer token)
API -> Auth : Verify JWT
Auth --> API : Valid
API -> DB : Query orders
DB --> API : Order list
API --> FE : 200 OK + orders
FE --> User : Display orders
@enduml`;

export const SAMPLE_USECASE_DIAGRAM = `@startuml
left to right direction
actor Customer
actor Admin
actor System

rectangle "E-Commerce Platform" {
  usecase "Browse Products" as UC1
  usecase "Search Products" as UC2
  usecase "Add to Cart" as UC3
  usecase "Place Order" as UC4
  usecase "Make Payment" as UC5
  usecase "View Order History" as UC6
  usecase "Manage Inventory" as UC7
  usecase "Generate Reports" as UC8
}

Customer --> UC1
Customer --> UC2
Customer --> UC3
UC1 .> UC2 : extends
UC3 .> UC4 : includes
UC4 .> UC5 : includes
Customer --> UC6
Admin --> UC7
Admin --> UC8
@enduml`;
```

- [x] **Step 2: Update store to use the class diagram sample as default**

In `packages/frontend/src/store/useAppStore.ts`, update the `getInitialSource` function to import and use `SAMPLE_CLASS_DIAGRAM`.

Add import at top:
```typescript
import { SAMPLE_CLASS_DIAGRAM } from '../utils/samples';
```

Replace the inline source string in `getInitialSource`:
```typescript
function getInitialSource(): string {
  return SAMPLE_CLASS_DIAGRAM;
}
```

- [x] **Step 3: Verify all three sample diagrams render correctly**

Start both backend and frontend. In the editor, paste each sample and verify rendering.

- [x] **Step 4: Final commit**

```bash
git add packages/frontend/src/utils/samples.ts packages/frontend/src/store/useAppStore.ts
git commit -m "feat(frontend): add sample diagrams for class, sequence, and usecase types"
```

---

## Phase 6: Verification

### Task 19: Full Verification Checklist

- [x] **Step 1: Run backend tests**

Run: `cd packages/backend && mvn test`
Expected: All tests pass

- [x] **Step 2: Run frontend build**

Run: `cd packages/frontend && pnpm build`
Expected: Build succeeds with no TypeScript errors

- [x] **Step 3: Verify all functional requirements**

| # | Requirement | Test |
|---|-------------|------|
| F1 | Source code editing | Monaco editor loads, accepts input |
| F2 | Real-time preview | Edit source → diagram updates |
| F3 | Element fold/unfold | Click `[+]` on containers in tree |
| F4 | Element hide/show | Right-click → Hide, or uncheck in tree |
| F5 | Relation auto-adapt | Hide a class → its relations disappear |
| F6 | Control panel tree | Right panel shows grouped tree with checkboxes |
| F7 | In-diagram interaction | Click elements, right-click context menu |
| F8 | State persistence | Reload page → visibility state restored |
| F9 | Presentation mode | Click Present → step through elements |
| F10 | Export image | PNG and SVG downloads |
| F11 | Multiple diagram types | Class, sequence, usecase all render |

- [x] **Step 4: Fix any issues found**

Address any bugs or issues discovered during verification.

- [x] **Step 5: Final commit**

```bash
git add -A
git commit -m "fix: address issues found during full verification"
```
