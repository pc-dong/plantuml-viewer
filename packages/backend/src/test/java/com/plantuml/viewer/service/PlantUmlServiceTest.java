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
    void generateSvg_withSequenceDiagram_returnsSvg() {
        String source = "@startuml\nactor Alice\nactor Bob\nAlice->Bob : Hello\n@enduml";
        String svg = service.generateSvg(source);
        assertNotNull(svg);
        assertTrue(svg.contains("<svg"));
    }

    @Test
    void detectDiagramType_withClassDiagram_returnsClass() {
        assertEquals("class", service.detectDiagramType("@startuml\nclass Foo\n@enduml"));
    }

    @Test
    void detectDiagramType_withSequenceDiagram_returnsSequence() {
        assertEquals("sequence", service.detectDiagramType("@startuml\nactor Alice\nAlice->Bob : Hello\n@enduml"));
    }

    @Test
    void detectDiagramType_withUseCaseDiagram_returnsUsecase() {
        assertEquals("usecase", service.detectDiagramType("@startuml\nactor User\nusecase \"Do Thing\" as UC1\nUser --> UC1\n@enduml"));
    }
}
