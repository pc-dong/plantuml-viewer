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
