import SockJS from 'sockjs-client';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { logger } from '../utils/logger';

export interface ExecutionEvent {
  type:
    | 'EXECUTION_STARTED'
    | 'EXECUTION_COMPLETED'
    | 'EXECUTION_FAILED'
    | 'EXECUTION_CANCELLED'
    | 'NODE_STARTED'
    | 'NODE_COMPLETED'
    | 'NODE_FAILED';
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
  private maxReconnectAttempts = 5;
  private reconnectDelay = 3000;
  onReconnectFailed: (() => void) | null = null;

  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.connected && this.client?.connected) {
        resolve();
        return;
      }

      const socket = new SockJS('/ws');
      this.client = new Client({
        webSocketFactory: () => socket as unknown as WebSocket,
        debug: (str) => {
          logger.debug('[STOMP] ' + str);
        },
        reconnectDelay: this.reconnectDelay,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
      });

      this.client.onConnect = () => {
        logger.info('WebSocket connected');
        this.connected = true;
        this.reconnectAttempts = 0;
        // Re-subscribe to all topics that have handlers
        this.resubscribeAll();
        resolve();
      };

      this.client.onStompError = (frame) => {
        logger.error('STOMP error:', frame.headers['message']);
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
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
          this.reconnectAttempts++;
          logger.info(`WebSocket reconnecting... attempt ${this.reconnectAttempts}`);
        } else {
          logger.error('WebSocket reconnection failed after max attempts');
          this.onReconnectFailed?.();
        }
      };

      this.client.activate();
    });
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
    const topic = '/topic/executions';
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
          this.handlers.get(topic)?.forEach((h) => h(event));
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
            this.handlers.get(topic)?.forEach((h) => h(event));
          } catch (error) {
            logger.error('Failed to parse WebSocket message:', error);
          }
        });
        this.subscriptions.set(topic, subscription);
        logger.info(`Re-subscribed to ${topic}`);
      }
    }
  }

  isConnected(): boolean {
    return this.connected && !!this.client?.connected;
  }
}

export const websocketService = new WebSocketService();
