import { create } from 'zustand';
import { websocketService, ExecutionEvent } from '../api/websocket';
import { logger } from '../utils/logger';

export interface NodeExecutionState {
  nodeId: string;
  status: 'pending' | 'running' | 'completed' | 'failed';
  output?: Record<string, unknown>;
  error?: string;
  startedAt?: string;
  completedAt?: string;
}

export interface ExecutionState {
  id: string;
  status: 'pending' | 'running' | 'completed' | 'failed' | 'cancelled';
  nodeStates: Map<string, NodeExecutionState>;
  output?: Record<string, unknown>;
  error?: string;
  startedAt?: string;
  completedAt?: string;
}

interface ExecutionStore {
  // State
  executions: Map<string, ExecutionState>;
  activeSubscriptions: Map<string, () => void>;
  isConnected: boolean;

  // Actions
  connect: () => Promise<void>;
  disconnect: () => void;
  subscribeToExecution: (executionId: string) => void;
  unsubscribeFromExecution: (executionId: string) => void;
  subscribeToAllExecutions: () => void;
  getExecution: (executionId: string) => ExecutionState | undefined;
  getNodeState: (executionId: string, nodeId: string) => NodeExecutionState | undefined;
  handleEvent: (event: ExecutionEvent) => void;
  clearExecution: (executionId: string) => void;
}

export const useExecutionStore = create<ExecutionStore>((set, get) => ({
  executions: new Map(),
  activeSubscriptions: new Map(),
  isConnected: false,

  connect: async () => {
    try {
      await websocketService.connect();
      set({ isConnected: true });
    } catch (error) {
      logger.error('Failed to connect WebSocket:', error);
      set({ isConnected: false });
    }
  },

  disconnect: () => {
    const { activeSubscriptions } = get();
    activeSubscriptions.forEach((unsubscribe) => unsubscribe());
    websocketService.disconnect();
    set({
      isConnected: false,
      activeSubscriptions: new Map(),
    });
  },

  subscribeToExecution: (executionId: string) => {
    const { activeSubscriptions, handleEvent } = get();

    if (activeSubscriptions.has(executionId)) {
      return; // Already subscribed
    }

    const unsubscribe = websocketService.subscribeToExecution(executionId, handleEvent);

    set({
      activeSubscriptions: new Map(activeSubscriptions).set(executionId, unsubscribe),
    });

    // Initialize execution state if not exists
    const { executions } = get();
    if (!executions.has(executionId)) {
      const newExecutions = new Map(executions);
      newExecutions.set(executionId, {
        id: executionId,
        status: 'pending',
        nodeStates: new Map(),
      });
      set({ executions: newExecutions });
    }
  },

  unsubscribeFromExecution: (executionId: string) => {
    const { activeSubscriptions } = get();
    const unsubscribe = activeSubscriptions.get(executionId);

    if (unsubscribe) {
      unsubscribe();
      const newSubscriptions = new Map(activeSubscriptions);
      newSubscriptions.delete(executionId);
      set({ activeSubscriptions: newSubscriptions });
    }
  },

  subscribeToAllExecutions: () => {
    const { activeSubscriptions, handleEvent } = get();
    const key = '__all__';

    if (activeSubscriptions.has(key)) {
      return;
    }

    const unsubscribe = websocketService.subscribeToAllExecutions(handleEvent);
    set({
      activeSubscriptions: new Map(activeSubscriptions).set(key, unsubscribe),
    });
  },

  getExecution: (executionId: string) => {
    return get().executions.get(executionId);
  },

  getNodeState: (executionId: string, nodeId: string) => {
    const execution = get().executions.get(executionId);
    return execution?.nodeStates.get(nodeId);
  },

  handleEvent: (event: ExecutionEvent) => {
    const { executions } = get();
    const executionId = event.executionId;

    const newExecutions = new Map(executions);
    let execution = newExecutions.get(executionId);

    if (!execution) {
      execution = {
        id: executionId,
        status: 'pending',
        nodeStates: new Map(),
      };
    } else {
      // Clone to trigger reactivity
      execution = {
        ...execution,
        nodeStates: new Map(execution.nodeStates),
      };
    }

    switch (event.type) {
      case 'EXECUTION_STARTED':
        execution.status = 'running';
        execution.startedAt = event.timestamp;
        break;

      case 'EXECUTION_COMPLETED':
        execution.status = 'completed';
        execution.completedAt = event.timestamp;
        execution.output = event.data;
        break;

      case 'EXECUTION_FAILED':
        execution.status = 'failed';
        execution.completedAt = event.timestamp;
        execution.error = event.data?.error as string;
        break;

      case 'EXECUTION_CANCELLED':
        execution.status = 'cancelled';
        execution.completedAt = event.timestamp;
        break;

      case 'NODE_STARTED':
        if (event.nodeId) {
          execution.nodeStates.set(event.nodeId, {
            nodeId: event.nodeId,
            status: 'running',
            startedAt: event.timestamp,
          });
        }
        break;

      case 'NODE_COMPLETED':
        if (event.nodeId) {
          const nodeState = execution.nodeStates.get(event.nodeId) || {
            nodeId: event.nodeId,
            status: 'completed',
          };
          execution.nodeStates.set(event.nodeId, {
            ...nodeState,
            status: 'completed',
            output: event.data,
            completedAt: event.timestamp,
          });
        }
        break;

      case 'NODE_FAILED':
        if (event.nodeId) {
          const nodeState = execution.nodeStates.get(event.nodeId) || {
            nodeId: event.nodeId,
            status: 'failed',
          };
          execution.nodeStates.set(event.nodeId, {
            ...nodeState,
            status: 'failed',
            error: event.data?.error as string,
            completedAt: event.timestamp,
          });
        }
        break;
    }

    newExecutions.set(executionId, execution);
    set({ executions: newExecutions });
  },

  clearExecution: (executionId: string) => {
    const { executions, activeSubscriptions } = get();

    // Unsubscribe
    const unsubscribe = activeSubscriptions.get(executionId);
    if (unsubscribe) {
      unsubscribe();
    }

    const newExecutions = new Map(executions);
    const newSubscriptions = new Map(activeSubscriptions);
    newExecutions.delete(executionId);
    newSubscriptions.delete(executionId);

    set({
      executions: newExecutions,
      activeSubscriptions: newSubscriptions,
    });
  },
}));
