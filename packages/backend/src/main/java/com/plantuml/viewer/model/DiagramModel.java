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
