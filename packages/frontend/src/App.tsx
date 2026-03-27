import { ConfigProvider } from 'antd';
import AppLayout from './components/AppLayout';
import './App.css';

function App() {
  return (
    <ConfigProvider theme={{ token: { colorPrimary: '#1677ff' } }}>
      <AppLayout />
    </ConfigProvider>
  );
}

export default App;
