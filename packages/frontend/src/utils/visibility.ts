import type { DiagramModel } from '../types';

export function isEffectivelyVisible(
  id: string,
  visibility: Record<string, boolean>,
  collapsed: Record<string, boolean>,
  model: DiagramModel,
): boolean {
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
}

export function isRelationVisible(
  sourceId: string,
  targetId: string,
  visibility: Record<string, boolean>,
  collapsed: Record<string, boolean>,
  model: DiagramModel,
): boolean {
  return isEffectivelyVisible(sourceId, visibility, collapsed, model) &&
    isEffectivelyVisible(targetId, visibility, collapsed, model);
}
