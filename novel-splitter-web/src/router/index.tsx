import { createBrowserRouter } from 'react-router-dom';
import Layout from '@/components/Layout';
import ChatPage from '@/pages/ChatPage';
import KnowledgePage from '@/pages/KnowledgePage';
import IngestPage from '@/pages/IngestPage';
import SystemPage from '@/pages/SystemPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    children: [
      {
        index: true,
        element: <ChatPage />,
      },
      {
        path: 'knowledge',
        element: <KnowledgePage />,
      },
      {
        path: 'ingest',
        element: <IngestPage />,
      },
      {
        path: 'system',
        element: <SystemPage />,
      },
    ],
  },
]);
