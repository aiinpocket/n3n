import { useEffect, useCallback, useMemo, useRef } from 'react';
import { message } from '../utils/feedback'
import { useExecutionStore } from '../stores/executionStore';
import { executionApi, CreateExecutionRequest, ExecutionResponse } from '../api/execution';
import { logger } from '../utils/logger';
import i18n from '../i18n';

export function useExecutionMonitor(executionId?: string) {
  const {
    connect,
    subscribeToExecution,
    unsubscribeFromExecution,
    getExecution,
    isConnected,
  } = useExecutionStore();

  const execution = executionId ? getExecution(executionId) : undefined;
  const subscribedRef = useRef<string | null>(null);

  // Connect to WebSocket on mount
  useEffect(() => {
    connect().catch((err) => logger.error('WebSocket connect failed:', err));
    return () => {
      // Don't disconnect on unmount - let the connection persist
    };
  }, [connect]);

  // Subscribe to specific execution
  useEffect(() => {
    if (executionId && isConnected) {
      // Only subscribe if not already subscribed to this execution
      if (subscribedRef.current !== executionId) {
        subscribedRef.current = executionId;
        subscribeToExecution(executionId);
      }
      return () => {
        if (subscribedRef.current === executionId) {
          subscribedRef.current = null;
          unsubscribeFromExecution(executionId);
        }
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
        try {
          await connect();
        } catch (error) {
          logger.error('WebSocket connect failed before execution start:', error);
          message.error(i18n.t('execution.connectionFailed'));
          throw error;
        }
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
  const { connect, subscribeToAllExecutions, unsubscribeFromExecution, executions, isConnected } = useExecutionStore();

  useEffect(() => {
    connect()
      .then(() => { subscribeToAllExecutions(); })
      .catch((err) => logger.error('WebSocket connect failed:', err));
    return () => {
      unsubscribeFromExecution('__all__');
    };
  }, [connect, subscribeToAllExecutions, unsubscribeFromExecution]);

  // Memoize on the store's Map reference — a fresh array every render would
  // retrigger consumers' effects that depend on it (infinite re-render loop).
  const executionList = useMemo(() => Array.from(executions.values()), [executions]);

  return {
    executions: executionList,
    isConnected,
  };
}
