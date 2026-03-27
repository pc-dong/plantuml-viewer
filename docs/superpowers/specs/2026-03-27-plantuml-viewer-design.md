# PlantUML Dynamic Preview Tool — Design Specification

**Date**: 2026-03-27
**Status**: Approved
**Approach**: SVG Wrapping (Approach A)

## 1. Overview

A web-based interactive PlantUML preview tool that renders UML diagrams from source code and allows users to dynamically hide/show/fold elements, with automatic relation adaptation.

## 2. Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Monorepo | pnpm workspaces | latest |
| Frontend | React 18 + TypeScript | React 18, TS 5.x |
| Build | Vite | 5.x |
| UI Library | Ant Design | 5.x |
| State | Zustand | latest |
| Editor | Monaco Editor | latest |
| Graphics | SVG + D3.js | D3 7.x |
| HTTP | Axios | latest |
| Backend | Spring Boot | 3.x |
| Java | OpenJDK | 17+ |
| Parser | PlantUML | latest |
| Build | Maven | latest |

## 3. Project Structure

```
plant_uml_viewer_2/
├── package.json                  # Root: pnpm workspace config
├── pnpm-workspace.yaml
├── .gitignore
├── docs/superpowers/specs/       # Design docs
├── packages/
│   ├── frontend/                 # React + Vite + TypeScript
│   │   ├── package.json
│   │   ├── vite.config.ts
│   │   ├── tsconfig.json
│   │   ├── index.html
│   │   └── src/
│   │       ├── main.tsx
│   │       ├── App.tsx
│   │       ├── components/
│   │       │   ├── EditorPanel/
│   │       │   ├── DiagramView/
│   │       │   ├── ControlTree/
│   │       │   ├── Toolbar/
│   │       │   └── ContextMenu/
│   │       ├── store/            # Zustand state
│   │       ├── hooks/
│   │       ├── services/         # API client (axios)
│   │       ├── types/
│   │       └── utils/
│   └── backend/                  # Spring Boot
│       ├── pom.xml
│       └── src/main/
│           ├── java/com/plantuml/viewer/
│           │   ├── PlantumlViewerApplication.java
│           │   ├── controller/
│           │   │   └── ParseController.java
│           │   ├── service/
│           │   │   ├── PlantUmlService.java
│           │   │   └── SvgParserService.java
│           │   ├── model/
│           │   └── config/
│           └── resources/
│               └── application.yml
```

## 4. Backend Architecture

### 4.1 Parsing Pipeline

```
PlantUML Source → SourceStringReader → Diagram → SVG Output → SVG DOM Parser → JSON Model
```

### 4.2 PlantUmlService

Responsibilities:
- Receives PlantUML source string
- Uses `net.sourceforge.plantuml.SourceStringReader` to generate SVG
- Returns the SVG as a string

### 4.3 SvgParserService

Responsibilities:
- Parses SVG DOM using `javax.xml.parsers`
- Extracts element metadata from `<g>` elements:
  - `id` → from `data-plantuml-elem-id` or generated id
  - `type` → inferred from CSS class or element structure
  - `name` → from `<text>` child elements
  - `position/size` → from `transform` attribute or bounding box
  - `parentId` → from nested `<g>` hierarchy
- Extracts relations from `<path>` and `<polygon>` elements
- Matches relation endpoints to element ids via proximity or data attributes
- Returns `DiagramModel` JSON

### 4.4 API Endpoints

| Method | Endpoint | Request | Response |
|--------|----------|---------|----------|
| POST | `/api/parse` | `{ source: "..." }` | `DiagramModel` JSON |
| POST | `/api/render` | `{ source: "..." }` | Raw SVG string |
| GET | `/api/health` | — | `{ status: "ok" }` |

### 4.5 Error Handling

- PlantUML syntax errors → 400 with error message and line number
- Internal errors → 500 with generic message
- Empty/missing source → 400

## 5. Data Model

```typescript
interface DiagramModel {
  version: string;
  type: string;          // "class", "sequence", "usecase", "component", "activity"
  elements: Element[];
  relations: Relation[];
}

interface Element {
  id: string;
  type: string;          // "package", "class", "interface", "actor", "lifeline", "message", "note", etc.
  name: string;
  parentId?: string;
  children?: string[];
  position: { x: number; y: number };
  size: { width: number; height: number };
  visible: boolean;
  collapsed?: boolean;   // containers only
  style?: Record<string, string>;
  metadata?: Record<string, any>;
}

interface Relation {
  id: string;
  type: string;          // "association", "dependency", "message", "extends", "implements", "composition", "aggregation"
  sourceId: string;
  targetId: string;
  label?: string;
  points?: { x: number; y: number }[];
  style?: Record<string, string>;
}
```

## 6. Frontend Architecture

### 6.1 Application Layout (3-panel)

```
┌──────────────────────────────────────────────────┐
│                   Toolbar                         │
├────────────┬──────────────────────┬──────────────┤
│  Editor    │    Diagram View      │  Control     │
│  Panel     │    (SVG Canvas)      │  Tree        │
│  Monaco    │                      │  Ant Tree    │
│  Editor    │                      │              │
├────────────┴──────────────────────┴──────────────┤
│                   Status Bar                      │
└──────────────────────────────────────────────────┘
```

- Left panel: resizable, collapsible (code editor)
- Center: main diagram view with zoom/pan
- Right panel: resizable, collapsible (tree control)

### 6.2 State Management (Zustand)

```typescript
interface AppState {
  // Data
  source: string;
  model: DiagramModel | null;
  svgRaw: string | null;
  parseError: string | null;

  // Visibility state
  visibility: Record<string, boolean>;
  collapsed: Record<string, boolean>;
  selectedElements: string[];

  // UI state
  presentationMode: boolean;
  presentationStep: number;
  leftPanelCollapsed: boolean;
  rightPanelCollapsed: boolean;

  // Actions
  setSource: (s: string) => void;
  parseSource: () => Promise<void>;
  toggleVisibility: (id: string) => void;
  toggleCollapse: (id: string) => void;
  resetView: () => void;
  setPresentationMode: (on: boolean) => void;
  nextStep: () => void;
  prevStep: () => void;
  exportImage: (format: 'png' | 'svg') => void;
  loadViewState: () => void;
  saveViewState: () => void;
  importViewState: (json: string) => void;
  exportViewState: () => string;
}
```

### 6.3 Component Responsibilities

**EditorPanel**: Monaco Editor with PlantUML syntax highlighting. Debounced auto-parse (300ms). Ctrl+Enter for manual parse.

**DiagramView**:
- Renders SVG with interactive overlays
- Each element gets transparent `<rect>` overlay for click/hover
- Collapsed containers show "+" icon
- Hidden elements use `display: none`
- Relations re-evaluated on every visibility change
- Zoom (scroll) and pan (drag) support

**ControlTree**:
- Ant Design `Tree` component with `checkable` mode
- Elements grouped by type
- Checkbox → visibility, expand icon → fold state
- Search/filter support

**Toolbar**:
- Export PNG/SVG buttons
- Presentation mode toggle
- Reset view button
- Import/Export state buttons

**ContextMenu**:
- Right-click on diagram elements
- Options: Hide, Show, Collapse/Expand, Toggle children

## 7. Interaction Logic

### 7.1 Effective Visibility

An element is effectively visible only if:
1. Its own `visibility[id]` is true
2. All ancestors are visible and not collapsed

```typescript
function isEffectivelyVisible(id: string): boolean {
  if (visibility[id] === false) return false;
  let current = getParent(id);
  while (current) {
    if (collapsed[current] || visibility[current] === false) return false;
    current = getParent(current);
  }
  return true;
}
```

### 7.2 Relation Visibility

A relation is visible only if both `sourceId` and `targetId` are effectively visible.

### 7.3 Collapse Behavior

- Container collapsed → children invisible (state preserved)
- Container shows "+" indicator
- External relations to hidden children auto-hide

### 7.4 Presentation Mode

- Pre-defined step sequence from user selection
- Full-screen, hide panels
- Step counter with prev/next navigation
- Previous elements dimmed, current element highlighted

### 7.5 Export

- **PNG**: SVG → Canvas → `toDataURL('image/png')` → download
- **SVG**: Serialize visible SVG → download as `.svg`
- Exports reflect current visibility state

### 7.6 Persistence

- Auto-save `visibility`, `collapsed`, `source` to `localStorage`
- Manual Import/Export as JSON file

## 8. Development Setup

- Backend: `mvn spring-boot:run` (port 8080)
- Frontend: `pnpm dev` (port 5173)
- Vite proxy: `/api` → `http://localhost:8080`
- No Docker required for development

## 9. Diagram Type Support

| Diagram Type | Fold/Hide | Priority |
|-------------|-----------|----------|
| Class | Full support | High |
| Sequence | Full support | High |
| Use Case | Full support | High |
| Component | Basic support | Medium |
| Activity | Basic (no fold) | Low |

## 10. Non-Functional Requirements

- Support up to 500 elements
- Fold/hide response < 200ms
- Cross-browser: Chrome, Firefox, Edge (latest)
- Keyboard shortcuts: Ctrl+Enter (parse), Ctrl+Click (toggle)
