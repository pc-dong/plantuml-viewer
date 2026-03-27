import { useMemo, useState, useCallback } from 'react';
import { Tree, Input } from 'antd';
import { useAppStore } from '../../store/useAppStore';
import './ControlTree.css';

export default function ControlTree() {
  const { model, visibility, collapsed, toggleCollapse } = useAppStore();
  const [searchText, setSearchText] = useState('');

  const treeData = useMemo(() => {
    if (!model) return [];
    const typeGroups: Record<string, typeof model.elements> = {};
    model.elements.forEach((el) => {
      // Skip elements that have a parentId — they appear as children of their parent
      if (el.parentId) return;
      const type = el.type || 'other';
      if (!typeGroups[type]) typeGroups[type] = [];
      typeGroups[type].push(el);
    });

    return Object.entries(typeGroups)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([type, elements]) => {
        const filtered = searchText
          ? elements.filter((el) => el.name.toLowerCase().includes(searchText.toLowerCase()))
          : elements;

        return {
          key: `group-${type}`,
          title: <span className="tree-group">{type} ({filtered.length})</span>,
          children: filtered.map((el) => {
            const isContainer = el.children && el.children.length > 0;
            const children = isContainer
              ? (el.children || [])
                  .map((cid) => model.elements.find((e) => e.id === cid))
                  .filter(Boolean)
              : [];

            return {
              key: el.id,
              title: (
                <span className={`tree-node element-type-${el.type}`}>
                  <span className="node-name">{el.name || el.id}</span>
                  {isContainer && (
                    <span
                      className={`collapse-toggle ${collapsed[el.id] ? 'collapsed' : ''}`}
                      onClick={(e) => { e.stopPropagation(); toggleCollapse(el.id); }}
                    >
                      {collapsed[el.id] ? '[+]' : '[-]'}
                    </span>
                  )}
                </span>
              ),
              children: children.map((child: any) => ({
                key: child.id,
                title: (
                  <span className={`tree-node element-type-${child.type}`}>
                    <span className="node-name">{child.name || child.id}</span>
                  </span>
                ),
              })),
            };
          }),
        };
      });
  }, [model, collapsed, searchText, toggleCollapse]);

  const checkedKeys = useMemo(() => {
    if (!model) return [];
    return model.elements.filter((el) => visibility[el.id] !== false && !el.parentId).map((el) => el.id);
  }, [model, visibility]);

  const handleCheck = useCallback(
    (checkedKeys: React.Key[] | { checked: React.Key[]; halfChecked: React.Key[] }) => {
      const keys = Array.isArray(checkedKeys) ? checkedKeys : checkedKeys.checked;
      const keySet = new Set(keys);
      if (!model) return;
      const newVisibility: Record<string, boolean> = {};
      model.elements.forEach((el) => { newVisibility[el.id] = keySet.has(el.id); });
      useAppStore.setState({ visibility: newVisibility });
      useAppStore.getState().saveViewState();
    },
    [model],
  );

  if (!model) return <div className="control-tree empty">No diagram loaded</div>;

  return (
    <div className="control-tree">
      <div className="control-tree-header">
        <span className="tree-title">Elements</span>
      </div>
      <div className="control-tree-search">
        <Input placeholder="Search elements..." size="small" value={searchText} onChange={(e) => setSearchText(e.target.value)} allowClear />
      </div>
      <div className="control-tree-body">
        <Tree checkable defaultExpandAll checkedKeys={checkedKeys} onCheck={handleCheck} treeData={treeData} selectable={false} />
      </div>
    </div>
  );
}
