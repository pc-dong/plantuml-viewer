import { Button, Tooltip, Space } from 'antd';
import {
  ExportOutlined, PlayCircleOutlined, UndoOutlined,
  SaveOutlined, FolderOpenOutlined, ColumnWidthOutlined, ColumnHeightOutlined,
} from '@ant-design/icons';
import { useAppStore } from '../../store/useAppStore';
import './Toolbar.css';

export default function Toolbar() {
  const { model, resetView, setPresentationMode, exportViewState, importViewState, toggleLeftPanel, toggleRightPanel, leftPanelCollapsed, rightPanelCollapsed } = useAppStore();

  const handleExportPng = async () => {
    const svgEl = document.querySelector('.diagram-view svg');
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
    const svgEl = document.querySelector('.diagram-view svg');
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

  const handleImportState = () => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.json';
    input.onchange = async (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (!file) return;
      const text = await file.text();
      try {
        importViewState(text);
        useAppStore.getState().parseSource();
      } catch { alert('Invalid view state file'); }
    };
    input.click();
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
        <Tooltip title="Save View State">
          <Button size="small" icon={<SaveOutlined />} onClick={handleExportState} disabled={!model}>Save</Button>
        </Tooltip>
        <Tooltip title="Load View State">
          <Button size="small" icon={<FolderOpenOutlined />} onClick={handleImportState}>Load</Button>
        </Tooltip>
        <Tooltip title="Presentation Mode">
          <Button size="small" icon={<PlayCircleOutlined />} onClick={() => setPresentationMode(true)} disabled={!model}>Present</Button>
        </Tooltip>
        <Tooltip title="Reset View">
          <Button size="small" icon={<UndoOutlined />} onClick={resetView} disabled={!model}>Reset</Button>
        </Tooltip>
      </Space>
    </div>
  );
}
