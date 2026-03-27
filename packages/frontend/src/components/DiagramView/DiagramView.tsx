import { useRef, useCallback, useMemo, useState } from 'react';
import { useAppStore } from '../../store/useAppStore';
import { ContextMenu } from '../ContextMenu';
import { isEffectivelyVisible, isRelationVisible } from '../../utils/visibility';
import './DiagramView.css';

export default function DiagramView() {
  const {
    model, visibility, collapsed, selectedElements, parseError, isLoading,
    toggleCollapse, setSelectedElements,
    presentationMode, presentationStep, presentationSteps,
    nextPresentationStep, prevPresentationStep,
  } = useAppStore();

  const svgContainerRef = useRef<HTMLDivElement>(null);
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; elementId: string } | null>(null);
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [isPanning, setIsPanning] = useState(false);
  const panStart = useRef({ x: 0, y: 0 });

  const handleWheel = useCallback((e: React.WheelEvent) => {
    if (e.ctrlKey || e.metaKey) {
      e.preventDefault();
      setZoom((z) => Math.max(0.1, Math.min(5, z - e.deltaY * 0.001)));
    } else {
      setPan((p) => ({ x: p.x - e.deltaX, y: p.y - e.deltaY }));
    }
  }, []);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    if (e.button === 0 && !(e.target as Element).closest?.('.diagram-element')) {
      setIsPanning(true);
      panStart.current = { x: e.clientX - pan.x, y: e.clientY - pan.y };
      setSelectedElements([]);
    }
  }, [pan, setSelectedElements]);

  const handleMouseMove = useCallback((e: React.MouseEvent) => {
    if (isPanning) setPan({ x: e.clientX - panStart.current.x, y: e.clientY - panStart.current.y });
  }, [isPanning]);

  const handleMouseUp = useCallback(() => { setIsPanning(false); }, []);

  const handleElementClick = useCallback((elementId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setSelectedElements([elementId]);
  }, [setSelectedElements]);

  const handleContextMenu = useCallback((elementId: string, e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setContextMenu({ x: e.clientX, y: e.clientY, elementId });
  }, []);

  const handleCloseContextMenu = useCallback(() => { setContextMenu(null); }, []);

  const presentationHighlightIds = useMemo(() => {
    if (!presentationMode || presentationSteps.length === 0) return new Set<string>();
    const step = presentationSteps[presentationStep];
    return step ? new Set(step) : new Set<string>();
  }, [presentationMode, presentationSteps, presentationStep]);

  const visibleElements = useMemo(() => {
    if (!model) return [];
    return model.elements.filter((el) => isEffectivelyVisible(el.id, visibility, collapsed, model));
  }, [model, visibility, collapsed]);

  const visibleRelations = useMemo(() => {
    if (!model) return [];
    return model.relations.filter((rel) => isRelationVisible(rel.sourceId, rel.targetId, visibility, collapsed, model));
  }, [model, visibility, collapsed]);

  if (isLoading) return <div className="diagram-view loading">Parsing PlantUML...</div>;
  if (parseError) return <div className="diagram-view error">{parseError}</div>;
  if (!model || model.elements.length === 0) return <div className="diagram-view empty">Enter PlantUML source and press Ctrl+Enter</div>;

  const svgWidth = Math.max(...model.elements.map((e) => e.position.x + e.size.width)) + 50;
  const svgHeight = Math.max(...model.elements.map((e) => e.position.y + e.size.height)) + 50;

  const prevStep = prevPresentationStep;
  const nextStep = nextPresentationStep;

  return (
    <div className="diagram-view" ref={svgContainerRef}
      onWheel={handleWheel} onMouseDown={handleMouseDown}
      onMouseMove={handleMouseMove} onMouseUp={handleMouseUp} onMouseLeave={handleMouseUp}>
      <svg width={svgWidth} height={svgHeight} viewBox={`0 0 ${svgWidth} ${svgHeight}`}
        style={{ transform: `scale(${zoom}) translate(${pan.x / zoom}px, ${pan.y / zoom}px)` }}>
        {visibleElements.map((el) => {
          const isSelected = selectedElements.includes(el.id);
          const isHighlighted = presentationHighlightIds.has(el.id);
          const isContainer = el.children && el.children.length > 0;
          const isCollapsed = collapsed[el.id];
          return (
            <g key={el.id} className={`diagram-element element-type-${el.type}`}
              onClick={(e) => handleElementClick(el.id, e)}
              onContextMenu={(e) => handleContextMenu(el.id, e)}
              data-element-id={el.id}>
              <rect x={el.position.x} y={el.position.y} width={el.size.width} height={el.size.height}
                fill={isCollapsed ? '#e6f7ff' : el.type === 'package' || el.type === 'group' ? '#fffbe6' : '#ffffff'}
                stroke={isSelected ? '#1677ff' : isHighlighted ? '#52c41a' : '#d9d9d9'}
                strokeWidth={isSelected || isHighlighted ? 2 : 1} rx={4} />
              <text x={el.position.x + 4} y={el.position.y + 14} fontSize={9} fill="#999">{el.type}</text>
              <text x={el.position.x + el.size.width / 2} y={el.position.y + el.size.height / 2 + 5}
                textAnchor="middle" fontSize={12} fill="#333"
                fontWeight={isSelected || isHighlighted ? 600 : 400}>{el.name}</text>
              {isContainer && isCollapsed && (
                <circle cx={el.position.x + el.size.width - 12} cy={el.position.y + 12} r={8}
                  fill="#1677ff" onClick={(e) => { e.stopPropagation(); toggleCollapse(el.id); }}
                  className="collapse-indicator">
                  <text x={el.position.x + el.size.width - 12} y={el.position.y + 16}
                    textAnchor="middle" fontSize={12} fill="white" fontWeight="bold">+</text>
                </circle>
              )}
            </g>
          );
        })}
        {visibleRelations.map((rel) => {
          const source = model!.elements.find((e) => e.id === rel.sourceId);
          const target = model!.elements.find((e) => e.id === rel.targetId);
          if (!source || !target) return null;
          const sx = source.position.x + source.size.width / 2;
          const sy = source.position.y + source.size.height / 2;
          const tx = target.position.x + target.size.width / 2;
          const ty = target.position.y + target.size.height / 2;
          const highlighted = presentationHighlightIds.has(rel.sourceId) && presentationHighlightIds.has(rel.targetId);
          return (
            <g key={rel.id} className="diagram-relation" data-relation-id={rel.id}>
              <line x1={sx} y1={sy} x2={tx} y2={ty}
                stroke={highlighted ? '#52c41a' : '#999'} strokeWidth={highlighted ? 2 : 1}
                markerEnd="url(#arrowhead)" />
              {rel.label && (
                <text x={(sx + tx) / 2} y={(sy + ty) / 2 - 5}
                  textAnchor="middle" fontSize={10} fill="#666">{rel.label}</text>
              )}
            </g>
          );
        })}
        <defs>
          <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="10" refY="3.5" orient="auto">
            <polygon points="0 0, 10 3.5, 0 7" fill="#999" />
          </marker>
        </defs>
      </svg>
      {presentationMode && (
        <div className="presentation-controls">
          <button onClick={prevStep}>&larr; Prev</button>
          <span>Step {presentationStep + 1} / {presentationSteps.length}</span>
          <button onClick={nextStep}>Next &rarr;</button>
        </div>
      )}
      <div className="zoom-controls">
        <button onClick={() => setZoom((z) => Math.min(5, z + 0.2))}>+</button>
        <span>{Math.round(zoom * 100)}%</span>
        <button onClick={() => setZoom((z) => Math.max(0.1, z - 0.2))}>-</button>
        <button onClick={() => { setZoom(1); setPan({ x: 0, y: 0 }); }}>Fit</button>
      </div>
      {contextMenu && (
        <ContextMenu x={contextMenu.x} y={contextMenu.y} elementId={contextMenu.elementId} onClose={handleCloseContextMenu} />
      )}
    </div>
  );
}
