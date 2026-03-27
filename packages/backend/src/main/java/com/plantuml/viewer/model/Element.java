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
        public Position(double x, double y) { this.x = x; this.y = y; }
        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
    }

    public static class Size {
        private double width;
        private double height;
        public Size() {}
        public Size(double width, double height) { this.width = width; this.height = height; }
        public double getWidth() { return width; }
        public void setWidth(double width) { this.width = width; }
        public double getHeight() { return height; }
        public void setHeight(double height) { this.height = height; }
    }
}
