import { Button, Tooltip, Space } from 'antd';
import {
  ExportOutlined, PlayCircleOutlined, UndoOutlined,
  SaveOutlined, FolderOpenOutlined, ColumnWidthOutlined, ColumnHeightOutlined,
  AppstoreOutlined, FileTextOutlined,
} from '@ant-design/icons';
import { useAppStore } from '../../store/useAppStore';
import { isEffectivelyVisible, isRelationVisible } from '../../utils/visibility';
import './Toolbar.css';

function buildExportSvg(): SVGSVGElement | null {
  const svgEl = document.querySelector('.diagram-view svg');
  if (!svgEl) return null;
  const clone = svgEl.cloneNode(true) as SVGSVGElement;

  const { model, visibility, collapsed } = useAppStore.getState();
  if (!model) return clone;

  const styleEl = document.createElementNS('http://www.w3.org/2000/svg', 'style');
  const cssLines: string[] = [];

  model.elements.forEach((el) => {
    if (!isEffectivelyVisible(el.id, visibility, collapsed, model)) {
      cssLines.push(`#elem_${el.id} { display: none !important; }`);
      cssLines.push(`#cluster_${el.id} { display: none !important; }`);
    }
  });

  model.relations.forEach((rel) => {
    if (!isRelationVisible(rel.sourceId, rel.targetId, visibility, collapsed, model)) {
      cssLines.push(`#${rel.id} { display: none !important; }`);
      if (!rel.id.startsWith('link_')) {
        cssLines.push(`#link_${rel.id} { display: none !important; }`);
      }
    }
  });

  if (cssLines.length > 0) {
    styleEl.textContent = cssLines.join('\n');
    clone.insertBefore(styleEl, clone.firstChild);
  }

  return clone;
}

export default function Toolbar() {
  const { source, model, resetView, setPresentationMode, exportViewState, toggleLeftPanel, toggleRightPanel, toggleCompactMode, compactMode, leftPanelCollapsed, rightPanelCollapsed } = useAppStore();

  const handleExportPng = async () => {
    const svgEl = buildExportSvg();
    if (!svgEl) return;
    const svgData = new XMLSerializer().serializeToString(svgEl);
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    const img = new Image();
    const svgBlob = new Blob([svgData], { type: 'image/svg+xml;charset=utf-8' });
    const url = URL.createObjectURL(svgBlob);
    img.onload = () => {
      canvas.width = img.width * 2;
      canvas.height = img.height * 2;
      ctx.scale(2, 2);
      ctx.drawImage(img, 0, 0);
      URL.revokeObjectURL(url);
      const link = document.createElement('a');
      link.download = 'plantuml-diagram.png';
      link.href = canvas.toDataURL('image/png');
      link.click();
    };
    img.src = url;
  };

  const handleExportSvg = () => {
    const svgEl = buildExportSvg();
    if (!svgEl) return;
    const svgData = new XMLSerializer().serializeToString(svgEl);
    const blob = new Blob([svgData], { type: 'image/svg+xml' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.download = 'plantuml-diagram.svg';
    link.href = url;
    link.click();
    URL.revokeObjectURL(url);
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const text = await file.text();
    useAppStore.getState().setSource(text);
    await useAppStore.getState().parseSource();
    e.target.value = '';
  };

  const handleExportState = () => {
    const json = exportViewState();
    const blob = new Blob([json], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.download = 'plantuml-viewstate.json';
    link.href = url;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="toolbar">
      <div className="toolbar-left">
        <span className="toolbar-brand">PlantUML Viewer</span>
      </div>
      <Space className="toolbar-right">
        <Tooltip title="Toggle Editor Panel">
          <Button type={leftPanelCollapsed ? 'default' : 'primary'} size="small" icon={<ColumnWidthOutlined />} onClick={toggleLeftPanel} />
        </Tooltip>
        <Tooltip title="Toggle Control Panel">
          <Button type={rightPanelCollapsed ? 'default' : 'primary'} size="small" icon={<ColumnHeightOutlined />} onClick={toggleRightPanel} />
        </Tooltip>
        <Tooltip title="Export PNG">
          <Button size="small" icon={<ExportOutlined />} onClick={handleExportPng} disabled={!model}>PNG</Button>
        </Tooltip>
        <Tooltip title="Export SVG">
          <Button size="small" icon={<ExportOutlined />} onClick={handleExportSvg} disabled={!model}>SVG</Button>
        </Tooltip>
        <Tooltip title="Save as .puml file">
          <Button size="small" icon={<FileTextOutlined />} onClick={() => {
            const blob = new Blob([source], { type: 'text/plain' });
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.download = 'plantuml-diagram.puml';
            link.href = url;
            link.click();
            URL.revokeObjectURL(url);
          }} disabled={!model}>PUML</Button>
        </Tooltip>
        <Tooltip title="Save View State">
          <Button size="small" icon={<SaveOutlined />} onClick={handleExportState} disabled={!model}>Save</Button>
        </Tooltip>
        <Tooltip title="Load PlantUML File">
          <label className="toolbar-load-btn" htmlFor="plantuml-file-input">
            <FolderOpenOutlined /> Load
          </label>
        </Tooltip>
        <Tooltip title="Presentation Mode">
          <Button size="small" icon={<PlayCircleOutlined />} onClick={() => setPresentationMode(true)} disabled={!model}>Present</Button>
        </Tooltip>
        <Tooltip title={compactMode ? 'Show Details' : 'Compact View (names only)'}>
          <Button size="small" type={compactMode ? 'primary' : 'default'} icon={<AppstoreOutlined />} onClick={toggleCompactMode} disabled={!model}>Compact</Button>
        </Tooltip>
        <Tooltip title="Reset View">
          <Button size="small" icon={<UndoOutlined />} onClick={resetView} disabled={!model}>Reset</Button>
        </Tooltip>
      </Space>
      <input id="plantuml-file-input" type="file" accept=".puml,.plantuml,.wsd,.txt" style={{ display: 'none' }} onChange={handleFileChange} />
    </div>
  );
}
