import { useEffect, useCallback } from 'react';
import { useExecutionStore } from '../stores/executionStore';
import { executionApi, CreateExecutionRequest, ExecutionResponse } from '../api/execution';

export function useExecutionMonitor(executionId?: string) {
  const {
    connect,
    subscribeToExecution,
    unsubscribeFromExecution,
    getExecution,
    isConnected,
  } = useExecutionStore();

  const execution = executionId ? getExecution(executionId) : undefined;

  // Connect to WebSocket on mount
  useEffect(() => {
    connect();
    return () => {
      // Don't disconnect on unmount - let the connection persist
    };
  }, [connect]);

  // Subscribe to specific execution
  useEffect(() => {
    if (executionId && isConnected) {
      subscribeToExecution(executionId);
      return () => {
        unsubscribeFromExecution(executionId);
      };
    }
  }, [executionId, isConnected, subscribeToExecution, unsubscribeFromExecution]);

  return {
    execution,
    isConnected,
  };
}

export function useExecutionActions() {
  const { connect, subscribeToExecution, isConnected } = useExecutionStore();

  const startExecution = useCallback(
    async (request: CreateExecutionRequest): Promise<ExecutionResponse> => {
      // Ensure WebSocket is connected
      if (!isConnected) {
        await connect();
      }

      // Create execution via REST API
      const execution = await executionApi.create(request);

      // Subscribe to execution updates
      subscribeToExecution(execution.id);

      return execution;
    },
    [connect, isConnected, subscribeToExecution]
  );

  const cancelExecution = useCallback(async (executionId: string, reason?: string) => {
    return executionApi.cancel(executionId, reason);
  }, []);

  return {
    startExecution,
    cancelExecution,
  };
}

export function useAllExecutions() {
  const { connect, subscribeToAllExecutions, executions, isConnected } = useExecutionStore();

  useEffect(() => {
    connect().then(() => {
      subscribeToAllExecutions();
    });
  }, [connect, subscribeToAllExecutions]);

  return {
    executions: Array.from(executions.values()),
    isConnected,
  };
}
