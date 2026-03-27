import { useRef, useCallback, useMemo, useState, useEffect } from 'react';
import { useAppStore } from '../../store/useAppStore';
import { ContextMenu } from '../ContextMenu';
import { isEffectivelyVisible, isRelationVisible } from '../../utils/visibility';
import './DiagramView.css';

export default function DiagramView() {
  const {
    model, svgRaw, visibility, collapsed, selectedElements, parseError, isLoading,
    toggleCollapse, setSelectedElements,
    presentationMode, presentationStep, presentationSteps,
    nextPresentationStep, prevPresentationStep,
  } = useAppStore();

  const svgContainerRef = useRef<HTMLDivElement>(null);
  const [contextMenu, setContextMenu] = useState<{ x: number; y: number; elementId: string } | null>(null);
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [isPanning, setIsPanning] = useState(false);
  const [svgViewBox, setSvgViewBox] = useState('');
  const panStart = useRef({ x: 0, y: 0 });

  const handleWheel = useCallback((e: WheelEvent) => {
    if (e.ctrlKey || e.metaKey) {
      e.preventDefault();
      setZoom((z) => Math.max(0.1, Math.min(5, z - e.deltaY * 0.001)));
    } else {
      setPan((p) => ({ x: p.x - e.deltaX, y: p.y - e.deltaY }));
    }
  }, []);

  // Use native event listener for wheel to allow preventDefault (passive: false)
  useEffect(() => {
    const el = svgContainerRef.current;
    if (!el) return;
    el.addEventListener('wheel', handleWheel, { passive: false });
    return () => el.removeEventListener('wheel', handleWheel);
  }, [handleWheel]);

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

  // Compute which elements and relations should be visible
  const hiddenElementIds = useMemo(() => {
    if (!model) return new Set<string>();
    const hidden = new Set<string>();
    model.elements.forEach((el) => {
      if (!isEffectivelyVisible(el.id, visibility, collapsed, model)) {
        hidden.add(el.id);
      }
    });
    return hidden;
  }, [model, visibility, collapsed]);

  const hiddenRelationIds = useMemo(() => {
    if (!model) return new Set<string>();
    const hidden = new Set<string>();
    model.relations.forEach((rel) => {
      if (!isRelationVisible(rel.sourceId, rel.targetId, visibility, collapsed, model)) {
        hidden.add(rel.id);
      }
    });
    return hidden;
  }, [model, visibility, collapsed]);

  // Auto-fit the SVG to the container after loading
  useEffect(() => {
    if (!svgRaw || !svgContainerRef.current) return;
    const container = svgContainerRef.current;
    const firstSvg = container.querySelector('svg') as SVGSVGElement | null;
    if (!firstSvg) return;
    const vb = firstSvg.viewBox.baseVal;
    if (vb.width > 0 && vb.height > 0) {
      const rect = container.getBoundingClientRect();
      const scaleX = (rect.width - 20) / vb.width;
      const scaleY = (rect.height - 20) / vb.height;
      const fitZoom = Math.min(scaleX, scaleY, 1);
      setZoom(fitZoom);
      setPan({ x: 0, y: 0 });
      setSvgViewBox(`${vb.x} ${vb.y} ${vb.width} ${vb.height}`);
    }
  }, [svgRaw]);

  // IDs from the parsed model for hiding elements/relations in the PlantUML SVG
  // PlantUML SVG uses g elements with ids like "elem_Foo", "cluster_Bar", "link_Foo_Bar"
  const svgHiddenGroupIds = useMemo(() => {
    if (!model) return new Set<string>();
    const ids = new Set<string>();
    hiddenElementIds.forEach((id) => {
      // Also hide parent groups if a child's parent group contains it
      const el = model.elements.find((e) => e.id === id);
      if (el) {
        // Add both elem_ and cluster_ prefixed versions
        ids.add(`elem_${id}`);
        ids.add(`cluster_${id}`);
      }
    });
    return ids;
  }, [model, hiddenElementIds]);

  const svgHiddenRelationIds = useMemo(() => {
    const ids = new Set<string>();
    hiddenRelationIds.forEach((id) => {
      ids.add(id);
      // Also try link_ prefixed version
      if (!id.startsWith('link_')) {
        ids.add(`link_${id}`);
      }
    });
    return ids;
  }, [hiddenRelationIds]);

  if (isLoading) return <div className="diagram-view loading">Parsing PlantUML...</div>;
  if (parseError) return <div className="diagram-view error">{parseError}</div>;
  if (!model || model.elements.length === 0 || !svgRaw) {
    return <div className="diagram-view empty">Enter PlantUML source and press Ctrl+Enter</div>;
  }

  const prevStep = prevPresentationStep;
  const nextStep = nextPresentationStep;

  return (
    <div className="diagram-view" ref={svgContainerRef}
      onMouseDown={handleMouseDown}
      onMouseMove={handleMouseMove} onMouseUp={handleMouseUp} onMouseLeave={handleMouseUp}>
      <div
        className="diagram-svg-wrapper"
        style={{ transform: `scale(${zoom}) translate(${pan.x / zoom}px, ${pan.y / zoom}px)` }}
        dangerouslySetInnerHTML={{ __html: svgRaw }}
      />
      {/* Interactive overlays: hide/show SVG groups based on visibility state */}
      <style>{`
        ${[...svgHiddenGroupIds].map((id) => `#${id} { display: none !important; }`).join('\n')}
        ${[...svgHiddenRelationIds].map((id) => `#${id} { display: none !important; }`).join('\n')}
        ${presentationMode && presentationHighlightIds.size > 0 ? model.elements
          .filter((el) => !presentationHighlightIds.has(el.id))
          .map((el) => {
            const gid = `elem_${el.id}`;
            return `#${gid} { opacity: 0.25 !important; }`;
          }).join('\n') : ''}
      `}</style>
      {/* Transparent clickable overlays on each visible element */}
      <svg className="diagram-overlays" viewBox={svgViewBox} preserveAspectRatio="xMidYMid meet" style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', pointerEvents: 'none' }}>
        {model.elements.filter((el) => el.position && el.size && !hiddenElementIds.has(el.id)).map((el) => {
          const isSelected = selectedElements.includes(el.id);
          const isHighlighted = presentationHighlightIds.has(el.id);
          const isContainer = el.children && el.children.length > 0;
          const isCollapsed = collapsed[el.id];
          return (
            <g key={el.id} className="diagram-element" style={{ pointerEvents: 'auto' }}
              onClick={(e) => handleElementClick(el.id, e)}
              onContextMenu={(e) => handleContextMenu(el.id, e)}
              data-element-id={el.id}>
              <rect x={el.position.x} y={el.position.y} width={el.size.width} height={el.size.height}
                fill="transparent" stroke={isSelected ? '#1677ff' : isHighlighted ? '#52c41a' : 'transparent'}
                strokeWidth={isSelected || isHighlighted ? 2 : 0} rx={4}
                style={{ cursor: 'pointer' }} />
              {isContainer && isCollapsed && (
                <circle cx={el.position.x + el.size.width - 12} cy={el.position.y + 12} r={8}
                  fill="#1677ff" onClick={(e) => { e.stopPropagation(); toggleCollapse(el.id); }}
                  className="collapse-indicator" style={{ cursor: 'pointer' }}>
                  <text x={el.position.x + el.size.width - 12} y={el.position.y + 16}
                    textAnchor="middle" fontSize={12} fill="white" fontWeight="bold">+</text>
                </circle>
              )}
            </g>
          );
        })}
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
