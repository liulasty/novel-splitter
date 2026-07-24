import { createBrowserRouter } from 'react-router-dom';
import Layout from '@/components/Layout';
import ChatPage from '@/pages/ChatPage';
import KnowledgePage from '@/pages/KnowledgePage';
import IngestPage from '@/pages/IngestPage';
import ProcessPage from '@/pages/ProcessPage';
import TasksPage from '@/pages/TasksPage';
import DlqMonitorPanel from '@/pages/System/DlqMonitorPanel';
import TaskLoadPage from '@/pages/tasks/TaskLoadPage';
import TaskSplitPage from '@/pages/tasks/TaskSplitPage';
import TaskEmbedPage from '@/pages/tasks/TaskEmbedPage';
import TaskPipelinePage from '@/pages/tasks/TaskPipelinePage';
import SettingsPage from '@/pages/SettingsPage';
import SystemPage from '@/pages/SystemPage';
import ChromaAdminPage from '@/pages/ChromaAdminPage';
import ErrorPage from '@/pages/ErrorPage';
import RagDebugPage from '@/pages/RagDebugPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Layout />,
    errorElement: <ErrorPage />,
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
        path: 'process',
        element: <ProcessPage />,
      },
      {
        path: 'tasks',
        element: <TasksPage />,
      },
      {
        path: 'tasks/dlq',
        element: <DlqMonitorPanel />,
      },
      {
        path: 'tasks/load',
        element: <TaskLoadPage />,
      },
      {
        path: 'tasks/split',
        element: <TaskSplitPage />,
      },
      {
        path: 'tasks/embed',
        element: <TaskEmbedPage />,
      },
      {
        path: 'tasks/pipeline',
        element: <TaskPipelinePage />,
      },
      {
        path: 'settings',
        element: <SettingsPage />,
      },
      {
        path: 'system',
        element: <SystemPage />,
      },
      {
        path: 'chroma-admin',
        element: <ChromaAdminPage />,
      },
      {
        path: 'debug',
        element: <RagDebugPage />,
      },
    ],
  },
]);
