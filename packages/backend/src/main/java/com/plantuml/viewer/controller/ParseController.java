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
            return ResponseEntity.badRequest().body(new ErrorResponse("Invalid source", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ErrorResponse("Parse error", e.getMessage()));
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
            return ResponseEntity.ok().header("Content-Type", "image/svg+xml").body(svg);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ErrorResponse("Render error", e.getMessage()));
        }
    }
}
