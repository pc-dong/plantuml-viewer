import axios from 'axios';
import type { DiagramModel } from '../types';

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

export async function parsePlantUml(source: string): Promise<DiagramModel> {
  const response = await api.post<DiagramModel>('/parse', { source });
  return response.data;
}

export async function renderSvg(source: string): Promise<string> {
  const response = await api.post<string>('/render', { source });
  return response.data;
}

export async function checkHealth(): Promise<string> {
  const response = await api.get<{ status: string }>('/health');
  return response.data.status;
}

export default api;
