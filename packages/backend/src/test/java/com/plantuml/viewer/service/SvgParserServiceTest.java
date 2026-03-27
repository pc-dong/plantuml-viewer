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
    void parse_withTwoClasses_returnsElements() {
        String source = "@startuml\nclass Foo\nclass Bar\nFoo --> Bar\n@enduml";
        String svg = plantUmlService.generateSvg(source);
        DiagramModel model = parser.parse(svg, "class");
        assertNotNull(model.getElements());
        long classCount = model.getElements().stream()
                .filter(e -> "class".equals(e.getType()) || "group".equals(e.getType()))
                .count();
        assertTrue(classCount >= 2, "Expected at least 2 class elements, got " + classCount);
    }

    @Test
    void parse_withRelation_returnsRelations() {
        String source = "@startuml\nclass Foo\nclass Bar\nFoo --> Bar\n@enduml";
        String svg = plantUmlService.generateSvg(source);
        DiagramModel model = parser.parse(svg, "class");
        assertNotNull(model.getRelations());
        // At minimum, we should detect some structural elements
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
    void parse_elementsHaveNames() {
        String source = "@startuml\nclass Foo\n@enduml";
        String svg = plantUmlService.generateSvg(source);
        DiagramModel model = parser.parse(svg, "class");
        // At least some elements should have names
        long named = model.getElements().stream()
                .filter(e -> e.getName() != null && !e.getName().isBlank())
                .count();
        assertTrue(named >= 1, "Expected at least 1 named element");
    }

    @Test
    void parse_withPackage_returnsElements() {
        String source = "@startuml\npackage com.example {\nclass Foo\n}\n@enduml";
        String svg = plantUmlService.generateSvg(source);
        DiagramModel model = parser.parse(svg, "class");
        assertNotNull(model.getElements());
        assertFalse(model.getElements().isEmpty());
    }
}
