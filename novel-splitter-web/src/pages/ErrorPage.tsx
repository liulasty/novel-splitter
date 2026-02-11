import { useRouteError } from "react-router-dom";
import { AlertCircle } from "lucide-react";

export default function ErrorPage() {
  const error: any = useRouteError();
  console.error(error);

  return (
    <div className="flex h-screen w-full flex-col items-center justify-center bg-gray-50">
      <div className="flex max-w-md flex-col items-center text-center space-y-4 p-8 bg-white rounded-xl shadow-lg border border-gray-100">
        <div className="p-3 bg-red-50 rounded-full">
            <AlertCircle className="h-10 w-10 text-red-500" />
        </div>
        <h1 className="text-2xl font-bold text-gray-900">Oops! Something went wrong.</h1>
        <p className="text-gray-500">
          Sorry, an unexpected error has occurred.
        </p>
        <div className="w-full p-4 bg-gray-50 rounded-md text-left overflow-auto max-h-40 text-sm font-mono text-red-600">
          <p>{error.statusText || error.message}</p>
        </div>
        <button 
            onClick={() => window.location.reload()}
            className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors"
        >
            Reload Page
        </button>
      </div>
    </div>
  );
}
