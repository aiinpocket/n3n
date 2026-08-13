import { create } from 'zustand';
import { websocketService, ExecutionEvent } from '../api/websocket';
import { executionApi } from '../api/execution';
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
  status: 'pending' | 'running' | 'completed' | 'failed' | 'cancelled' | 'waiting' | 'paused';
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
  refreshExecution: (executionId: string) => Promise<void>;
  unsubscribeFromExecution: (executionId: string) => void;
  subscribeToAllExecutions: () => void;
  getExecution: (executionId: string) => ExecutionState | undefined;
  getNodeState: (executionId: string, nodeId: string) => NodeExecutionState | undefined;
  handleEvent: (event: ExecutionEvent) => void;
  clearExecution: (executionId: string) => void;
}

const MAX_EXECUTIONS = 100;

export const useExecutionStore = create<ExecutionStore>((set, get) => ({
  executions: new Map(),
  activeSubscriptions: new Map(),
  isConnected: false,

  connect: async () => {
    try {
      websocketService.onReconnectFailed = () => {
        logger.error('WebSocket reconnection failed after max attempts');
        set({ isConnected: false });
      };
      // Keep the Live/Disconnected indicator honest across auto-reconnects
      websocketService.onConnectionChange = (connected) => {
        set({ isConnected: connected });
      };
      await websocketService.connect();
      set({ isConnected: true });
    } catch (error) {
      logger.error('Failed to connect WebSocket:', error);
      set({ isConnected: false });
      // Re-throw so callers can surface the connection failure to the user
      throw error;
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

    // Reconcile with REST state: fast executions can finish before the WebSocket
    // subscription is active, which would leave the UI stuck on "pending"
    void get().refreshExecution(executionId);
  },

  refreshExecution: async (executionId: string) => {
    try {
      const [exec, nodeExecs] = await Promise.all([
        executionApi.get(executionId),
        executionApi.getNodeExecutions(executionId).catch(() => []),
      ]);
      set((state) => {
        const executions = new Map(state.executions);
        const existing = executions.get(executionId);
        // WebSocket events that arrived meanwhile are fresher; don't downgrade them
        const terminalStates = ['completed', 'failed', 'cancelled'];
        if (existing && terminalStates.includes(existing.status)) {
          return {};
        }
        const nodeStates = new Map(existing?.nodeStates || []);
        for (const n of nodeExecs) {
          const current = nodeStates.get(n.nodeId);
          if (!current || current.status === 'pending' || current.status === 'running') {
            nodeStates.set(n.nodeId, {
              nodeId: n.nodeId,
              status: n.status,
              error: n.errorMessage,
              startedAt: n.startedAt,
              completedAt: n.completedAt,
              output: current?.output,
            });
          }
        }
        executions.set(executionId, {
          id: executionId,
          status: exec.status,
          nodeStates,
          error: existing?.error,
          output: existing?.output,
          startedAt: exec.startedAt,
          completedAt: exec.completedAt,
        });
        return { executions };
      });
    } catch (error) {
      logger.error('Failed to refresh execution state:', error);
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

      case 'EXECUTION_WAITING':
        execution.status = 'waiting';
        break;

      case 'EXECUTION_RESUMED':
        execution.status = 'running';
        break;

      case 'APPROVAL_CREATED':
      case 'APPROVAL_ACTION':
      case 'APPROVAL_RESOLVED':
        // Trigger re-render so ExecutionPage can refresh approval data
        break;
    }

    newExecutions.set(executionId, execution);

    // Prune completed executions if map exceeds limit
    if (newExecutions.size > MAX_EXECUTIONS) {
      const { activeSubscriptions } = get();
      for (const [id, exec] of newExecutions) {
        if (newExecutions.size <= MAX_EXECUTIONS) break;
        if (['completed', 'failed', 'cancelled'].includes(exec.status) && !activeSubscriptions.has(id)) {
          newExecutions.delete(id);
        }
      }
    }

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
