import { useEffect } from 'react';
import { useAppStore } from '../../store/useAppStore';
import EditorPanel from '../EditorPanel';
import DiagramView from '../DiagramView';
import ControlTree from '../ControlTree';
import Toolbar from '../Toolbar';
import './AppLayout.css';

function AppLayout() {
  const { source, parseSource, loadViewState, presentationMode, setPresentationMode } = useAppStore();

  useEffect(() => { loadViewState(); }, [loadViewState]);
  useEffect(() => { if (source) parseSource(); }, []);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && presentationMode) setPresentationMode(false);
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [presentationMode, setPresentationMode]);

  const leftCollapsed = useAppStore((s) => s.leftPanelCollapsed);
  const rightCollapsed = useAppStore((s) => s.rightPanelCollapsed);

  return (
    <div className="app-layout">
      {!presentationMode && <Toolbar />}
      <div className="app-body">
        {!presentationMode && (
          <div className={`panel panel-left ${leftCollapsed ? 'collapsed' : ''}`}>
            <EditorPanel />
          </div>
        )}
        <div className="panel panel-center">
          <DiagramView />
        </div>
        {!presentationMode && (
          <div className={`panel panel-right ${rightCollapsed ? 'collapsed' : ''}`}>
            <ControlTree />
          </div>
        )}
      </div>
      {presentationMode && (
        <button className="exit-presentation-btn" onClick={() => setPresentationMode(false)}>
          Exit Presentation (Esc)
        </button>
      )}
    </div>
  );
}

export default AppLayout;
