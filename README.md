# PlantUML Viewer

A web-based interactive PlantUML viewer with real-time editing, element-level visibility control, presentation mode, and multi-format export.

**English** | [中文](README_CN.md)

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18 + TypeScript + Vite + Ant Design 5 |
| State | Zustand + localStorage persistence |
| Editor | Monaco Editor (PlantUML syntax highlighting) |
| Backend | Spring Boot 3.x + Java 17 + PlantUML |
| Deploy | Docker (multi-stage build, Nginx + supervisord) |

## Features

### Real-time Editor
- Monaco Editor with PlantUML syntax highlighting
- Auto-parse on code changes (500ms debounce)
- Manual trigger via `Ctrl+Enter`

### Interactive Diagram View
- SVG rendering with zoom & pan (`Ctrl/Cmd+Scroll` to zoom, drag to pan)
- Click to highlight elements
- Right-click context menu on elements
- Auto-fit on diagram load

### Element Visibility Control
- Tree panel showing all diagram elements grouped by type
- Show/hide individual elements via checkboxes
- Relations auto-hide when connected elements are hidden
- Collapse/expand container elements (packages, classes)
- Search elements by name

### Compact Mode
- Collapse all class/interface/enum details for large diagrams
- Toggle individual elements to expand details selectively

### Presentation Mode
- Full-screen step-by-step diagram walkthrough
- Navigate with arrow keys, exit with `Escape`
- Auto-generated presentation steps from diagram structure

### Export
- **PNG** — rasterized at 2x scale, respects current visibility
- **SVG** — vector format with visibility state applied
- **PlantUML source** (`.puml`) — export current editor content
- **JSON state** — save/restore full view state including visibility

### Diagram Types
- Class diagrams (inheritance, interfaces, packages)
- Sequence diagrams (actors, lifelines, messages)
- Use case diagrams

## Quick Start

### Local Development

```bash
# Install dependencies
pnpm install

# Start backend (port 8080)
cd packages/backend && mvn spring-boot:run &

# Start frontend dev server (port 5173)
cd packages/frontend && pnpm dev
```

Open http://localhost:5173 in the browser.

### Docker

```bash
# Build image
docker build -t plantuml-viewer .

# Run container
docker run -p 8080:80 plantuml-viewer
```

Open http://localhost:8080 in the browser.

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/parse` | Parse PlantUML source to structured JSON |
| POST | `/api/render` | Render PlantUML source to SVG |
| GET | `/api/health` | Health check |

## Project Structure

```
.
├── packages/
│   ├── frontend/          # React + Vite
│   │   └── src/
│   │       ├── components/
│   │       │   ├── EditorPanel/     # Monaco editor with PlantUML support
│   │       │   ├── DiagramView/     # SVG rendering with zoom/pan/overlay
│   │       │   ├── ControlTree/     # Element visibility tree
│   │       │   ├── Toolbar/         # Export, new diagram, compact mode
│   │       │   └── ContextMenu/     # Right-click element actions
│   │       ├── store/               # Zustand state management
│   │       ├── services/            # API client
│   │       ├── types/               # TypeScript type definitions
│   │       └── utils/               # Visibility logic, samples
│   └── backend/           # Spring Boot
│       └── src/main/java/com/plantuml/viewer/
│           ├── controller/          # REST API endpoints
│           ├── service/             # PlantUML parsing & SVG generation
│           ├── model/               # Request/response DTOs
│           └── config/              # CORS configuration
├── Dockerfile
├── nginx.conf
└── docker/
    └── supervisord.conf
```

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+Enter` | Manually trigger parse |
| `Ctrl/Cmd+Scroll` | Zoom diagram |
| `F2` | Export PNG |
| `F5` | Export SVG |
| `F6` | Export PlantUML source |
| `F7` | Export JSON state |
| `F8` | Import JSON state |
| `F9` | Load `.puml` file |
| `F10` | Toggle compact mode |
| `F11` | Enter presentation mode |
| `Arrow Left/Right` | Navigate presentation steps |
| `Escape` | Exit presentation mode |
