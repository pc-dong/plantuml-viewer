import { useRef, useCallback } from 'react';
import Editor from '@monaco-editor/react';
import { useAppStore } from '../../store/useAppStore';
import './EditorPanel.css';

const PLANTUML_LANGUAGE = 'plaintext';

export default function EditorPanel() {
  const editorRef = useRef<any>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();
  const { source, setSource, parseSource, isLoading, parseError } = useAppStore();

  const handleEditorChange = useCallback(
    (value: string | undefined) => {
      const newSource = value || '';
      setSource(newSource);
      if (debounceRef.current) clearTimeout(debounceRef.current);
      debounceRef.current = setTimeout(() => { parseSource(); }, 500);
    },
    [setSource, parseSource],
  );

  const handleEditorMount = useCallback(
    (editor: any, monaco: any) => {
      editorRef.current = editor;
      monaco.languages.register({ id: PLANTUML_LANGUAGE });
      monaco.languages.setMonarchTokensProvider(PLANTUML_LANGUAGE, {
        keywords: [
          'class', 'interface', 'enum', 'abstract',
          'package', 'namespace', 'actor', 'usecase', 'component', 'note',
          'frame', 'rectangle', 'node', 'cloud', 'database',
          'extends', 'implements', 'of', 'as',
        ],
        tokenizer: {
          root: [
            [/[@]startuml|[@]enduml/, { token: 'keyword' }],
            [/[a-zA-Z_][a-zA-Z0-9_]*/, { cases: { '@keywords': 'keyword', '@default': 'identifier' } }],
            [/"[^"]*"/, 'string'],
            [/\/\/.*$/, 'comment'],
          ],
        },
      });
      editor.addAction({
        id: 'parse-plantuml',
        label: 'Parse PlantUML',
        keybindings: [monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter],
        run: () => parseSource(),
      });
    },
    [parseSource],
  );

  return (
    <div className="editor-panel">
      <div className="editor-header">
        <span className="editor-title">PlantUML Source</span>
        {isLoading && <span className="editor-status loading">Parsing...</span>}
        {parseError && <span className="editor-status error" title={parseError}>Error</span>}
      </div>
      <div className="editor-body">
        <Editor
          height="100%"
          language={PLANTUML_LANGUAGE}
          value={source}
          onChange={handleEditorChange}
          onMount={handleEditorMount}
          theme="vs"
          options={{
            minimap: { enabled: false },
            fontSize: 13,
            lineNumbers: 'on',
            scrollBeyondLastLine: false,
            wordWrap: 'on',
            automaticLayout: true,
            tabSize: 2,
          }}
        />
      </div>
    </div>
  );
}
