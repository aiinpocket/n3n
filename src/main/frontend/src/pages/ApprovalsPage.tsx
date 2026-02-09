import { useEffect, useState, useCallback } from 'react'
import {
  Card,
  Button,
  Space,
  Table,
  Tag,
  message,
  Modal,
  Input,
  Typography,
  Spin,
  Result,
  Badge,
  Empty,
  Descriptions,
} from 'antd'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ReloadOutlined,
  ExclamationCircleOutlined,
  EyeOutlined,
} from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { approvalApi, ApprovalSummary, ApprovalDetail } from '../api/approval'
import { extractApiError } from '../utils/errorMessages'
import { getLocale } from '../utils/locale'

const { Text } = Typography

export default function ApprovalsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [approvals, setApprovals] = useState<ApprovalSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [detailModalOpen, setDetailModalOpen] = useState(false)
  const [selectedApproval, setSelectedApproval] = useState<ApprovalDetail | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [comment, setComment] = useState('')
  const [submittingIds, setSubmittingIds] = useState<Record<string, boolean>>({})

  const loadApprovals = useCallback(async () => {
    setLoadError(null)
    setLoading(true)
    try {
      const data = await approvalApi.getPending()
      setApprovals(data)
    } catch (error) {
      setLoadError(extractApiError(error, t('common.loadFailed')))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => {
    loadApprovals()
  }, [loadApprovals])

  const handleViewDetail = async (approvalId: string) => {
    setDetailModalOpen(true)
    setDetailLoading(true)
    try {
      const detail = await approvalApi.getApproval(approvalId)
      setSelectedApproval(detail)
    } catch (error) {
      message.error(extractApiError(error, t('common.loadFailed')))
      setDetailModalOpen(false)
    } finally {
      setDetailLoading(false)
    }
  }

  const handleAction = async (approvalId: string, action: 'approve' | 'reject') => {
    if (submittingIds[approvalId]) return
    setSubmittingIds(prev => ({ ...prev, [approvalId]: true }))
    try {
      await approvalApi.submitApproval(approvalId, action, comment || undefined)
      message.success(t(`approval.${action === 'approve' ? 'approved' : 'rejected'}`))
      setComment('')
      setDetailModalOpen(false)
      setSelectedApproval(null)
      loadApprovals()
    } catch (error) {
      message.error(extractApiError(error, t('common.operationFailed')))
    } finally {
      setSubmittingIds(prev => ({ ...prev, [approvalId]: false }))
    }
  }

  const columns = [
    {
      title: t('approval.message'),
      dataIndex: 'message',
      key: 'message',
      ellipsis: true,
      render: (msg: string) => msg || <Text type="secondary">{t('approval.pending')}</Text>,
    },
    {
      title: t('execution.executionId'),
      dataIndex: 'executionId',
      key: 'executionId',
      width: 140,
      render: (id: string) => (
        <Button
          type="link"
          size="small"
          onClick={() => navigate(`/executions/${id}`)}
          style={{ padding: 0 }}
        >
          {id.substring(0, 8)}...
        </Button>
      ),
    },
    {
      title: t('approval.modeLabel'),
      dataIndex: 'approvalMode',
      key: 'approvalMode',
      width: 100,
      render: (mode: string) => (
        <Tag>{t(`approval.mode.${mode}`, { defaultValue: mode })}</Tag>
      ),
    },
    {
      title: t('approval.waitingFor', { count: 0 }),
      key: 'progress',
      width: 140,
      render: (_: unknown, record: ApprovalSummary) => (
        <Space>
          <Badge count={record.approvedCount} style={{ backgroundColor: 'var(--color-success)' }} />
          <Text type="secondary">/</Text>
          <Text>{record.requiredApprovers}</Text>
        </Space>
      ),
    },
    {
      title: t('common.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (val: string) => val ? new Date(val).toLocaleString(getLocale()) : '-',
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 280,
      render: (_: unknown, record: ApprovalSummary) => (
        <Space>
          <Button
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record.id)}
          >
            {t('common.details')}
          </Button>
          <Button
            size="small"
            type="primary"
            icon={<CheckCircleOutlined />}
            onClick={() => handleAction(record.id, 'approve')}
            loading={submittingIds[record.id]}
            style={{ background: 'var(--color-success)', borderColor: 'var(--color-success)' }}
          >
            {t('approval.approve')}
          </Button>
          <Button
            size="small"
            danger
            icon={<CloseCircleOutlined />}
            onClick={() => handleAction(record.id, 'reject')}
            loading={submittingIds[record.id]}
          >
            {t('approval.reject')}
          </Button>
        </Space>
      ),
    },
  ]

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '50vh' }}>
        <Spin size="large" />
      </div>
    )
  }

  if (loadError) {
    return (
      <Result
        status="error"
        title={t('common.loadFailed')}
        subTitle={loadError}
        extra={<Button type="primary" onClick={loadApprovals}>{t('common.retry')}</Button>}
      />
    )
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <Card
        title={
          <Space>
            <ExclamationCircleOutlined style={{ color: 'var(--color-warning)' }} />
            <span>{t('approvals.title')}</span>
            {approvals.length > 0 && (
              <Badge count={approvals.length} style={{ backgroundColor: 'var(--color-warning)' }} />
            )}
          </Space>
        }
        extra={
          <Button icon={<ReloadOutlined />} onClick={loadApprovals}>
            {t('common.refresh')}
          </Button>
        }
      >
        {approvals.length === 0 ? (
          <Empty
            image={<CheckCircleOutlined style={{ fontSize: 48, color: 'var(--color-success)' }} />}
            description={t('approvals.noPending')}
          />
        ) : (
          <Table
            columns={columns}
            dataSource={approvals}
            rowKey="id"
            pagination={{ pageSize: 20, showTotal: (total) => t('common.total', { count: total }) }}
            scroll={{ x: 'max-content' }}
            size="small"
          />
        )}
      </Card>

      <Modal
        title={t('approvals.detail')}
        open={detailModalOpen}
        onCancel={() => {
          setDetailModalOpen(false)
          setSelectedApproval(null)
          setComment('')
        }}
        footer={
          selectedApproval?.status === 'pending' ? (
            <Space>
              <Button onClick={() => { setDetailModalOpen(false); setSelectedApproval(null); setComment('') }}>
                {t('common.cancel')}
              </Button>
              <Button
                type="primary"
                icon={<CheckCircleOutlined />}
                onClick={() => handleAction(selectedApproval.id, 'approve')}
                loading={submittingIds[selectedApproval.id]}
                style={{ background: 'var(--color-success)', borderColor: 'var(--color-success)' }}
              >
                {t('approval.approve')}
              </Button>
              <Button
                danger
                icon={<CloseCircleOutlined />}
                onClick={() => handleAction(selectedApproval.id, 'reject')}
                loading={submittingIds[selectedApproval.id]}
              >
                {t('approval.reject')}
              </Button>
            </Space>
          ) : null
        }
        width={640}
      >
        {detailLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin size="large" />
          </div>
        ) : selectedApproval ? (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            {selectedApproval.message && (
              <div style={{ padding: 12, background: 'var(--color-bg-elevated)', borderRadius: 8 }}>
                {selectedApproval.message}
              </div>
            )}
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label={t('common.status')}>
                <Tag color={selectedApproval.status === 'pending' ? 'orange' : selectedApproval.status === 'approved' ? 'green' : 'red'}>
                  {selectedApproval.status}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t('approval.modeLabel')}>
                {t(`approval.mode.${selectedApproval.approvalMode}`, { defaultValue: selectedApproval.approvalMode })}
              </Descriptions.Item>
              <Descriptions.Item label={t('approval.approvedCount', { count: selectedApproval.approvedCount })}>
                {selectedApproval.approvedCount}
              </Descriptions.Item>
              <Descriptions.Item label={t('approval.rejectedCount', { count: selectedApproval.rejectedCount })}>
                {selectedApproval.rejectedCount}
              </Descriptions.Item>
              <Descriptions.Item label={t('execution.executionId')}>
                <Button type="link" size="small" onClick={() => navigate(`/executions/${selectedApproval.executionId}`)} style={{ padding: 0 }}>
                  {selectedApproval.executionId}
                </Button>
              </Descriptions.Item>
              <Descriptions.Item label={t('common.createdAt')}>
                {selectedApproval.createdAt ? new Date(selectedApproval.createdAt).toLocaleString(getLocale()) : '-'}
              </Descriptions.Item>
            </Descriptions>

            {selectedApproval.actions.length > 0 && (
              <Card title={t('approvals.actionHistory')} size="small">
                {selectedApproval.actions.map((action) => (
                  <div key={action.id} style={{ padding: '8px 0', borderBottom: '1px solid var(--color-border)' }}>
                    <Space>
                      {action.action === 'approve' ? (
                        <CheckCircleOutlined style={{ color: 'var(--color-success)' }} />
                      ) : (
                        <CloseCircleOutlined style={{ color: 'var(--color-error)' }} />
                      )}
                      <Text strong>{action.action}</Text>
                      {action.comment && <Text type="secondary">- {action.comment}</Text>}
                      <Text type="secondary">
                        {action.createdAt ? new Date(action.createdAt).toLocaleString(getLocale()) : ''}
                      </Text>
                    </Space>
                  </div>
                ))}
              </Card>
            )}

            {selectedApproval.status === 'pending' && (
              <Input.TextArea
                placeholder={t('approval.commentPlaceholder')}
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                rows={2}
                maxLength={1000}
                showCount
              />
            )}
          </Space>
        ) : null}
      </Modal>
    </Space>
  )
}
