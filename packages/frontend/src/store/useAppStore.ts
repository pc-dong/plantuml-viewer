import { create } from 'zustand';
import { parsePlantUml, renderSvg } from '../services/api';
import type { DiagramModel, ViewState } from '../types';
import { SAMPLE_CLASS_DIAGRAM } from '../utils/samples';

interface AppState {
  source: string;
  model: DiagramModel | null;
  svgRaw: string | null;
  parseError: string | null;
  isLoading: boolean;
  visibility: Record<string, boolean>;
  collapsed: Record<string, boolean>;
  selectedElements: string[];
  presentationMode: boolean;
  presentationSteps: string[][];
  presentationStep: number;
  leftPanelCollapsed: boolean;
  rightPanelCollapsed: boolean;
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
  return SAMPLE_CLASS_DIAGRAM;
}

export const useAppStore = create<AppState>((set, get) => ({
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

  setSource: (source) => set({ source }),

  parseSource: async () => {
    const { source } = get();
    set({ isLoading: true, parseError: null });
    try {
      const [model, svgRaw] = await Promise.all([parsePlantUml(source), renderSvg(source)]);
      const visibility: Record<string, boolean> = {};
      const collapsed: Record<string, boolean> = {};
      model.elements.forEach((el) => { visibility[el.id] = true; });
      set({ model, svgRaw, visibility, collapsed, parseError: null, isLoading: false });
      get().saveViewState();
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Parse failed';
      set({ parseError: message, isLoading: false });
    }
  },

  toggleVisibility: (id) => set((state) => ({ visibility: { ...state.visibility, [id]: !state.visibility[id] } })),
  toggleCollapse: (id) => set((state) => ({ collapsed: { ...state.collapsed, [id]: !state.collapsed[id] } })),
  setSelectedElements: (ids) => set({ selectedElements: ids }),

  resetView: () => {
    const { model } = get();
    if (!model) return;
    const visibility: Record<string, boolean> = {};
    const collapsed: Record<string, boolean> = {};
    model.elements.forEach((el) => { visibility[el.id] = true; });
    set({ visibility, collapsed, selectedElements: [], presentationMode: false, presentationStep: 0 });
  },

  setPresentationMode: (on) => set((state) => {
    if (on && state.model) {
      const steps: string[][] = state.model.elements.map((el) => [el.id]);
      return { presentationMode: true, presentationSteps: steps, presentationStep: 0 };
    }
    return { presentationMode: false, presentationSteps: [], presentationStep: 0 };
  }),

  nextPresentationStep: () => set((state) => ({ presentationStep: Math.min(state.presentationStep + 1, state.presentationSteps.length - 1) })),
  prevPresentationStep: () => set((state) => ({ presentationStep: Math.max(state.presentationStep - 1, 0) })),
  toggleLeftPanel: () => set((state) => ({ leftPanelCollapsed: !state.leftPanelCollapsed })),
  toggleRightPanel: () => set((state) => ({ rightPanelCollapsed: !state.rightPanelCollapsed })),

  loadViewState: () => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      const state: ViewState = JSON.parse(raw);
      set({ source: state.source, visibility: state.visibility, collapsed: state.collapsed });
    } catch { /* ignore */ }
  },

  saveViewState: () => {
    const { source, visibility, collapsed } = get();
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify({ source, visibility, collapsed })); }
    catch { /* ignore */ }
  },

  importViewState: (json) => {
    try {
      const state: ViewState = JSON.parse(json);
      set({ source: state.source, visibility: state.visibility, collapsed: state.collapsed });
    } catch { throw new Error('Invalid view state JSON'); }
  },

  exportViewState: () => {
    const { source, visibility, collapsed } = get();
    return JSON.stringify({ source, visibility, collapsed }, null, 2);
  },

  getEffectiveVisibility: (id) => {
    const { model, visibility, collapsed } = get();
    if (!model) return true;
    if (visibility[id] === false) return false;
    const elementMap = new Map(model.elements.map((el) => [el.id, el]));
    let currentId: string | undefined = id;
    while (currentId) {
      if (collapsed[currentId]) return false;
      if (visibility[currentId] === false) return false;
      const el = elementMap.get(currentId);
      currentId = el?.parentId;
    }
    return true;
  },
}));
