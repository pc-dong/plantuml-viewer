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

export interface ViewState {
  visibility: Record<string, boolean>;
  collapsed: Record<string, boolean>;
  source: string;
}

export interface PresentationStep {
  elementIds: string[];
  label?: string;
}
