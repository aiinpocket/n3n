import SockJS from 'sockjs-client';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { logger } from '../utils/logger';
import { useAuthStore } from '../stores/authStore';

export interface ExecutionEvent {
  type:
    | 'EXECUTION_STARTED'
    | 'EXECUTION_COMPLETED'
    | 'EXECUTION_FAILED'
    | 'EXECUTION_CANCELLED'
    | 'EXECUTION_WAITING'
    | 'EXECUTION_RESUMED'
    | 'NODE_STARTED'
    | 'NODE_COMPLETED'
    | 'NODE_FAILED'
    | 'APPROVAL_CREATED'
    | 'APPROVAL_ACTION'
    | 'APPROVAL_RESOLVED';
  executionId: string;
  status: string;
  nodeId?: string;
  data?: Record<string, unknown>;
  timestamp: string;
}

type EventHandler = (event: ExecutionEvent) => void;

class WebSocketService {
  private client: Client | null = null;
  private subscriptions: Map<string, StompSubscription> = new Map();
  private handlers: Map<string, Set<EventHandler>> = new Map();
  private connected = false;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 8;
  private baseReconnectDelay = 1000;
  private connectPromise: Promise<void> | null = null;
  onReconnectFailed: (() => void) | null = null;
  // Notified on every connect/disconnect so UI state can track reconnections
  onConnectionChange: ((connected: boolean) => void) | null = null;

  connect(): Promise<void> {
    // If already connected, resolve immediately
    if (this.connected && this.client?.connected) {
      return Promise.resolve();
    }
    // If a connection attempt is already in progress, return the same promise
    // to prevent orphaned SockJS connections from concurrent connect() calls
    if (this.connectPromise) {
      return this.connectPromise;
    }

    // Fresh connection attempt: reset the retry counter so a previously
    // exhausted retry cap doesn't permanently poison reconnection
    this.reconnectAttempts = 0;

    this.connectPromise = new Promise<void>((resolve, reject) => {

      this.client = new Client({
        // Create fresh SockJS + token on each connection/reconnection attempt
        webSocketFactory: () => {
          const freshToken = useAuthStore.getState().accessToken;
          return new SockJS(freshToken ? `/ws?token=${encodeURIComponent(freshToken)}` : '/ws') as unknown as WebSocket;
        },
        beforeConnect: () => {
          // Update connect headers with fresh token before each attempt
          const freshToken = useAuthStore.getState().accessToken;
          if (this.client) {
            this.client.connectHeaders = freshToken ? { Authorization: `Bearer ${freshToken}` } : {};
          }
        },
        debug: (str) => {
          logger.debug('[STOMP] ' + str);
        },
        reconnectDelay: this.getReconnectDelay(),
        heartbeatIncoming: 25000,
        heartbeatOutgoing: 25000,
      });

      this.client.onConnect = () => {
        logger.info('WebSocket connected');
        this.connected = true;
        this.reconnectAttempts = 0;
        this.connectPromise = null;
        // Re-subscribe to all topics that have handlers
        this.resubscribeAll();
        this.onConnectionChange?.(true);
        resolve();
      };

      this.client.onStompError = (frame) => {
        logger.error('STOMP error:', frame.headers['message']);
        this.connectPromise = null;
        reject(new Error(frame.headers['message']));
      };

      this.client.onDisconnect = () => {
        logger.info('WebSocket disconnected');
        this.connected = false;
        // Clear STOMP subscriptions but KEEP handlers for reconnect
        this.subscriptions.clear();
      };

      this.client.onWebSocketClose = () => {
        this.connected = false;
        this.subscriptions.clear();
        this.onConnectionChange?.(false);
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
          this.reconnectAttempts++;
          const nextDelay = Math.round(this.getReconnectDelay() / 1000);
          logger.info(`WebSocket reconnecting... attempt ${this.reconnectAttempts} (next in ~${nextDelay}s)`);
          if (this.client) {
            this.client.reconnectDelay = this.getReconnectDelay();
          }
        } else {
          logger.error('WebSocket reconnection failed after max attempts');
          // Stop STOMP auto-reconnect to prevent infinite retries
          if (this.client) {
            this.client.deactivate();
          }
          this.connectPromise = null;
          // Settle a still-pending connect() promise so callers don't await forever
          // (no-op if the promise already resolved/rejected)
          reject(new Error('WebSocket connection failed after max attempts'));
          this.onReconnectFailed?.();
        }
      };

      this.client.activate();
    }).catch((err) => {
      this.connectPromise = null;
      throw err;
    });
    return this.connectPromise;
  }

  disconnect(): void {
    if (this.client) {
      this.subscriptions.forEach((sub) => sub.unsubscribe());
      this.subscriptions.clear();
      this.handlers.clear();
      this.client.deactivate();
      this.client = null;
      this.connected = false;
    }
  }

  subscribeToExecution(executionId: string, handler: EventHandler): () => void {
    const topic = `/topic/executions/${executionId}`;
    return this.subscribe(topic, handler);
  }

  subscribeToAllExecutions(handler: EventHandler): () => void {
    // Subscribe to user-specific queue for dashboard updates (server sends only own executions)
    const topic = '/user/queue/executions';
    return this.subscribe(topic, handler);
  }

  private subscribe(topic: string, handler: EventHandler): () => void {
    if (!this.client || !this.connected) {
      logger.warn('WebSocket not connected. Call connect() first.');
      // Queue handler for when connection is established
      if (!this.handlers.has(topic)) {
        this.handlers.set(topic, new Set());
      }
      this.handlers.get(topic)!.add(handler);
      return () => {
        this.handlers.get(topic)?.delete(handler);
      };
    }

    // Add handler to set
    if (!this.handlers.has(topic)) {
      this.handlers.set(topic, new Set());
    }
    this.handlers.get(topic)!.add(handler);

    // Create subscription if it doesn't exist
    if (!this.subscriptions.has(topic)) {
      const subscription = this.client.subscribe(topic, (message: IMessage) => {
        try {
          const event: ExecutionEvent = JSON.parse(message.body);
          this.handlers.get(topic)?.forEach((h) => {
            try {
              h(event);
            } catch (handlerError) {
              logger.error('WebSocket handler error:', handlerError);
            }
          });
        } catch (error) {
          logger.error('Failed to parse WebSocket message:', error);
        }
      });
      this.subscriptions.set(topic, subscription);
    }

    // Return unsubscribe function
    return () => {
      this.handlers.get(topic)?.delete(handler);
      // Only unsubscribe from STOMP if no more handlers
      if (this.handlers.get(topic)?.size === 0) {
        this.subscriptions.get(topic)?.unsubscribe();
        this.subscriptions.delete(topic);
        this.handlers.delete(topic);
      }
    };
  }

  private resubscribeAll(): void {
    if (!this.client || !this.connected) return;
    for (const [topic, handlers] of this.handlers.entries()) {
      if (handlers.size > 0 && !this.subscriptions.has(topic)) {
        const subscription = this.client.subscribe(topic, (message: IMessage) => {
          try {
            const event: ExecutionEvent = JSON.parse(message.body);
            this.handlers.get(topic)?.forEach((h) => {
              try {
                h(event);
              } catch (handlerError) {
                logger.error('WebSocket handler error:', handlerError);
              }
            });
          } catch (error) {
            logger.error('Failed to parse WebSocket message:', error);
          }
        });
        this.subscriptions.set(topic, subscription);
        logger.info(`Re-subscribed to ${topic}`);
      }
    }
  }

  private getReconnectDelay(): number {
    // Exponential backoff: 1s, 2s, 4s, 8s, 16s, 32s, max 60s
    const delay = Math.min(this.baseReconnectDelay * Math.pow(2, this.reconnectAttempts), 60000);
    // Add jitter (0-25% of delay) to prevent thundering herd
    return delay + Math.random() * delay * 0.25;
  }

  isConnected(): boolean {
    return this.connected && !!this.client?.connected;
  }
}

export const websocketService = new WebSocketService();

// Clean up WebSocket connection on page unload to prevent orphaned connections
if (typeof window !== 'undefined') {
  window.addEventListener('beforeunload', () => {
    websocketService.disconnect();
  });
}
