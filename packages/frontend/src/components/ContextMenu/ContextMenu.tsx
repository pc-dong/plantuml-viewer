import { useAppStore } from '../../store/useAppStore';
import './ContextMenu.css';

interface ContextMenuProps {
  x: number;
  y: number;
  elementId: string;
  onClose: () => void;
}

export function ContextMenu({ x, y, elementId, onClose }: ContextMenuProps) {
  const { model, visibility, collapsed, toggleVisibility, toggleCollapse } = useAppStore();
  const element = model?.elements.find((e) => e.id === elementId);
  if (!element) return null;

  const isVisible = visibility[elementId] !== false;
  const isContainer = element.children && element.children.length > 0;
  const isCollapsed = collapsed[elementId];

  const handleAction = (action: () => void) => { action(); onClose(); };

  return (
    <div className="context-menu" style={{ left: x, top: y }} onClick={(e) => e.stopPropagation()}>
      <div className="context-menu-header">
        <span className="cm-type">{element.type}</span>
        <span className="cm-name">{element.name || element.id}</span>
      </div>
      <div className="context-menu-divider" />
      <button onClick={() => handleAction(() => toggleVisibility(elementId))}>
        {isVisible ? 'Hide' : 'Show'}
      </button>
      {isContainer && (
        <button onClick={() => handleAction(() => toggleCollapse(elementId))}>
          {isCollapsed ? 'Expand' : 'Collapse'}
        </button>
      )}
      {isContainer && element.children && (
        <button onClick={() =>
          handleAction(() => {
            const childVis = !isVisible;
            const newVisibility = { ...visibility };
            element.children!.forEach((cid) => { newVisibility[cid] = childVis; });
            useAppStore.setState({ visibility: newVisibility });
            useAppStore.getState().saveViewState();
          })
        }>
          {isVisible ? 'Hide Children' : 'Show Children'}
        </button>
      )}
    </div>
  );
}
