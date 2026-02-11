import { create } from 'zustand';

interface AppState {
  // Add global state here
}

export const useAppStore = create<AppState>(() => ({
  // Initial state
}));
