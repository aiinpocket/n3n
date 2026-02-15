import { useEffect, useState, useCallback, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  Table,
  Button,
  Space,
  Tag,
  Modal,
  Form,
  Input,
  Select,
  message,
  Tooltip,
  Typography,
  Card,
  Empty,
} from 'antd'
import {
  PlusOutlined,
  DeleteOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  ThunderboltOutlined,
  EditOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import { schedulerApi } from '../api/scheduler'
import type { Schedule, CreateScheduleRequest } from '../api/scheduler'
import { useFlowListStore } from '../stores/flowListStore'
import { extractApiError } from '../utils/errorMessages'
import { getLocale } from '../utils/locale'

const { Text } = Typography

const TIMEZONES = [
  'UTC',
  'Asia/Taipei',
  'Asia/Tokyo',
  'Asia/Shanghai',
  'Asia/Hong_Kong',
  'America/New_York',
  'America/Chicago',
  'America/Los_Angeles',
  'Europe/London',
  'Europe/Berlin',
  'Europe/Paris',
  'Australia/Sydney',
]

const SchedulerPage: React.FC = () => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [schedules, setSchedules] = useState<Schedule[]>([])
  const [loading, setLoading] = useState(false)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [creating, setCreating] = useState(false)
  const [form] = Form.useForm()
  const [editModalOpen, setEditModalOpen] = useState(false)
  const [editingSchedule, setEditingSchedule] = useState<Schedule | null>(null)
  const [editSubmitting, setEditSubmitting] = useState(false)
  const [editForm] = Form.useForm()
  const [actionLoading, setActionLoading] = useState<string | null>(null)
  const [searchText, setSearchText] = useState('')

  const { flows, fetchFlows } = useFlowListStore()

  const loadSchedules = useCallback(async () => {
    setLoading(true)
    try {
      const data = await schedulerApi.list()
      setSchedules(data)
    } catch (error) {
      message.error(extractApiError(error, t('common.loadFailed')))
    } finally {
      setLoading(false)
    }
  }, [t])

  useEffect(() => {
    loadSchedules()
    fetchFlows(0, 200)
  }, [loadSchedules, fetchFlows])

  const handleCreate = async (values: CreateScheduleRequest) => {
    setCreating(true)
    try {
      await schedulerApi.create(values)
      message.success(t('common.createSuccess'))
      setIsModalOpen(false)
      form.resetFields()
      loadSchedules()
    } catch (error) {
      message.error(extractApiError(error, t('common.createFailed')))
    } finally {
      setCreating(false)
    }
  }

  const handleToggleActive = async (schedule: Schedule) => {
    setActionLoading(schedule.id)
    try {
      if (schedule.isActive) {
        await schedulerApi.pause(schedule.id)
        message.success(t('schedule.paused'))
      } else {
        await schedulerApi.resume(schedule.id)
        message.success(t('schedule.resumed'))
      }
      loadSchedules()
    } catch (error) {
      message.error(extractApiError(error, t('common.updateFailed')))
    } finally {
      setActionLoading(null)
    }
  }

  const handleTrigger = async (schedule: Schedule) => {
    if (!schedule.isActive) {
      message.warning(t('schedule.triggerInactiveWarning'))
      return
    }
    setActionLoading(schedule.id)
    try {
      const result = await schedulerApi.trigger(schedule.id)
      if (result.success) {
        const key = `schedule-trigger-${Date.now()}`
        message.success({
          content: (
            <span>
              {t('schedule.triggerSuccess')}{' '}
              <Button type="link" size="small" style={{ padding: 0 }} onClick={() => { navigate('/executions'); message.destroy(key) }}>
                {t('schedule.viewExecutions')}
              </Button>
            </span>
          ),
          key,
          duration: 5,
        })
      } else {
        message.error(t('schedule.triggerFailed'))
      }
    } catch (error) {
      message.error(extractApiError(error, t('schedule.triggerFailed')))
    } finally {
      setActionLoading(null)
    }
  }

  const handleDelete = async (id: string) => {
    Modal.confirm({
      title: t('schedule.deleteConfirm'),
      content: t('schedule.deleteWarning'),
      okText: t('common.delete'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          await schedulerApi.delete(id)
          message.success(t('common.deleteSuccess'))
          loadSchedules()
        } catch (error) {
          message.error(extractApiError(error, t('common.deleteFailed')))
        }
      },
    })
  }

  const handleEdit = (record: Schedule) => {
    setEditingSchedule(record)
    editForm.setFieldsValue({
      name: record.name,
      cronExpression: record.cronExpression,
      timezone: record.timezone,
    })
    setEditModalOpen(true)
  }

  const handleEditSubmit = async () => {
    try {
      const values = await editForm.validateFields()
      setEditSubmitting(true)
      await schedulerApi.update(editingSchedule!.id, values)
      message.success(t('common.updateSuccess'))
      setEditModalOpen(false)
      setEditingSchedule(null)
      editForm.resetFields()
      loadSchedules()
    } catch (err) {
      if (err && typeof err === 'object' && 'errorFields' in err) return
      message.error(extractApiError(err, t('common.saveFailed')))
    } finally {
      setEditSubmitting(false)
    }
  }

  const filteredSchedules = useMemo(() => {
    if (!searchText) return schedules
    const lower = searchText.toLowerCase()
    return schedules.filter(s =>
      s.name.toLowerCase().includes(lower) ||
      (s.flowName && s.flowName.toLowerCase().includes(lower)) ||
      (s.cronExpression && s.cronExpression.toLowerCase().includes(lower))
    )
  }, [schedules, searchText])

  const columns: ColumnsType<Schedule> = [
    {
      title: t('schedule.name'),
      dataIndex: 'name',
      key: 'name',
      sorter: (a: Schedule, b: Schedule) => a.name.localeCompare(b.name),
      render: (name: string) => (
        <Space>
          <ClockCircleOutlined />
          <Text strong>{name}</Text>
        </Space>
      ),
    },
    {
      title: t('schedule.flow'),
      dataIndex: 'flowName',
      key: 'flowName',
      sorter: (a: Schedule, b: Schedule) => (a.flowName || '').localeCompare(b.flowName || ''),
      render: (name: string | null, record: Schedule) => {
        if (!name) return <Text type="secondary">-</Text>
        return (
          <Button type="link" size="small" style={{ padding: 0 }} onClick={() => navigate(`/flows/${record.flowId}/edit`)}>
            {name}
          </Button>
        )
      },
    },
    {
      title: t('schedule.cronExpression'),
      dataIndex: 'cronExpression',
      key: 'cronExpression',
      width: 160,
      render: (cron: string) => <Text code>{cron}</Text>,
    },
    {
      title: t('schedule.timezone'),
      dataIndex: 'timezone',
      key: 'timezone',
      width: 140,
    },
    {
      title: t('common.status'),
      dataIndex: 'isActive',
      key: 'isActive',
      width: 100,
      render: (isActive: boolean) =>
        isActive ? (
          <Tag icon={<CheckCircleOutlined />} color="success">
            {t('schedule.active')}
          </Tag>
        ) : (
          <Tag icon={<PauseCircleOutlined />} color="default">
            {t('schedule.paused')}
          </Tag>
        ),
    },
    {
      title: t('schedule.nextRun'),
      dataIndex: 'nextRunAt',
      key: 'nextRunAt',
      width: 180,
      sorter: (a: Schedule, b: Schedule) => new Date(a.nextRunAt || 0).getTime() - new Date(b.nextRunAt || 0).getTime(),
      render: (time: string | null) =>
        time ? new Date(time).toLocaleString(getLocale()) : <Text type="secondary">-</Text>,
    },
    {
      title: t('schedule.lastRun'),
      dataIndex: 'lastRunAt',
      key: 'lastRunAt',
      width: 180,
      sorter: (a: Schedule, b: Schedule) => new Date(a.lastRunAt || 0).getTime() - new Date(b.lastRunAt || 0).getTime(),
      render: (time: string | null) =>
        time ? new Date(time).toLocaleString(getLocale()) : <Text type="secondary">-</Text>,
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 200,
      render: (_: unknown, record: Schedule) => (
        <Space>
          <Tooltip title={record.isActive ? t('schedule.pause') : t('schedule.resume')}>
            <Button
              type="text"
              icon={record.isActive ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
              onClick={() => handleToggleActive(record)}
              loading={actionLoading === record.id}
              aria-label={record.isActive ? t('schedule.pause') : t('schedule.resume')}
            />
          </Tooltip>
          <Tooltip title={t('schedule.triggerNow')}>
            <Button
              type="text"
              icon={<ThunderboltOutlined />}
              onClick={() => handleTrigger(record)}
              loading={actionLoading === record.id}
              aria-label={t('schedule.triggerNow')}
            />
          </Tooltip>
          <Tooltip title={t('common.edit')}>
            <Button
              type="link"
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
            />
          </Tooltip>
          <Tooltip title={t('common.delete')}>
            <Button
              type="text"
              danger
              icon={<DeleteOutlined />}
              onClick={() => handleDelete(record.id)}
              aria-label={t('common.delete')}
            />
          </Tooltip>
        </Space>
      ),
    },
  ]

  return (
    <div style={{ padding: 24 }}>
      <Card
        title={
          <Space>
            <ClockCircleOutlined />
            {t('schedule.title')}
          </Space>
        }
        extra={
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setIsModalOpen(true)}
          >
            {t('schedule.create')}
          </Button>
        }
      >
        <Typography.Paragraph type="secondary" style={{ marginBottom: 16 }}>
          {t('schedule.description')}
        </Typography.Paragraph>

        {schedules.length === 0 && !loading ? (
          <Empty
            description={t('schedule.empty')}
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          >
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setIsModalOpen(true)}
            >
              {t('schedule.createFirst')}
            </Button>
          </Empty>
        ) : (
          <>
            <Input
              placeholder={t('schedule.searchPlaceholder')}
              prefix={<SearchOutlined />}
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              allowClear
              style={{ width: 300, marginBottom: 16 }}
            />
            <Table
              columns={columns}
              dataSource={filteredSchedules}
              rowKey="id"
              loading={loading}
              pagination={{ pageSize: 10, showTotal: (total) => t('common.total', { count: total }) }}
              scroll={{ x: 1000 }}
            />
          </>
        )}
      </Card>

      {/* Create Modal */}
      <Modal
        title={t('schedule.createTitle')}
        open={isModalOpen}
        onCancel={() => {
          setIsModalOpen(false)
          form.resetFields()
        }}
        footer={null}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreate}
          initialValues={{ timezone: 'UTC' }}
        >
          <Form.Item
            name="flowId"
            label={t('schedule.flow')}
            rules={[{ required: true, message: t('schedule.flowRequired') }]}
          >
            <Select
              placeholder={t('schedule.selectFlow')}
              showSearch
              optionFilterProp="children"
            >
              {flows.map((flow) => (
                <Select.Option key={flow.id} value={flow.id}>
                  {flow.name}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item
            name="name"
            label={t('schedule.name')}
            rules={[
              { required: true, message: t('schedule.nameRequired') },
              { max: 255, message: t('common.maxLength', { max: 255 }) },
            ]}
          >
            <Input placeholder={t('schedule.namePlaceholder')} maxLength={255} />
          </Form.Item>

          <Form.Item
            name="cronExpression"
            label={t('schedule.cronExpression')}
            rules={[
              { required: true, message: t('schedule.cronRequired') },
              { max: 100, message: t('common.maxLength', { max: 100 }) },
            ]}
            extra={
              <Space direction="vertical" size={4} style={{ marginTop: 4 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>{t('schedule.cronHint')}</Text>
                <Space wrap size={[4, 4]}>
                  <Text type="secondary" style={{ fontSize: 12 }}>{t('schedule.presets')}:</Text>
                  {[
                    { label: t('schedule.everyMinute'), value: '0 * * * * ?' },
                    { label: t('schedule.everyHour'), value: '0 0 * * * ?' },
                    { label: t('schedule.everyDay'), value: '0 0 0 * * ?' },
                    { label: t('schedule.everyWeekday'), value: '0 0 9 ? * MON-FRI' },
                    { label: t('schedule.everyMonday'), value: '0 0 9 ? * MON' },
                    { label: t('schedule.everyMonth'), value: '0 0 0 1 * ?' },
                  ].map((preset) => (
                    <Tag
                      key={preset.value}
                      style={{ cursor: 'pointer', fontSize: 11 }}
                      onClick={() => form.setFieldsValue({ cronExpression: preset.value })}
                    >
                      {preset.label}
                    </Tag>
                  ))}
                </Space>
              </Space>
            }
          >
            <Input placeholder="0 0 * * * ?" maxLength={100} />
          </Form.Item>

          <Form.Item
            name="timezone"
            label={t('schedule.timezone')}
          >
            <Select showSearch>
              {TIMEZONES.map((tz) => (
                <Select.Option key={tz} value={tz}>
                  {tz}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>

          <Form.Item>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button onClick={() => setIsModalOpen(false)}>
                {t('common.cancel')}
              </Button>
              <Button type="primary" htmlType="submit" loading={creating}>
                {t('common.create')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>

      {/* Edit Modal */}
      <Modal
        title={`${t('common.edit')}: ${editingSchedule?.name}`}
        open={editModalOpen}
        onCancel={() => { setEditModalOpen(false); editForm.resetFields() }}
        onOk={handleEditSubmit}
        confirmLoading={editSubmitting}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label={t('schedule.name')}
            rules={[
              { required: true, message: t('schedule.nameRequired') },
              { max: 255, message: t('common.maxLength', { max: 255 }) },
            ]}
          >
            <Input maxLength={255} />
          </Form.Item>
          <Form.Item
            name="cronExpression"
            label={t('schedule.cronExpression')}
            rules={[
              { required: true, message: t('schedule.cronRequired') },
              { max: 100, message: t('common.maxLength', { max: 100 }) },
            ]}
            extra={
              <Space direction="vertical" size={4} style={{ marginTop: 4 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>{t('schedule.cronHint')}</Text>
                <Space wrap size={[4, 4]}>
                  <Text type="secondary" style={{ fontSize: 12 }}>{t('schedule.presets')}:</Text>
                  {[
                    { label: t('schedule.everyMinute'), value: '0 * * * * ?' },
                    { label: t('schedule.everyHour'), value: '0 0 * * * ?' },
                    { label: t('schedule.everyDay'), value: '0 0 0 * * ?' },
                    { label: t('schedule.everyWeekday'), value: '0 0 9 ? * MON-FRI' },
                    { label: t('schedule.everyMonday'), value: '0 0 9 ? * MON' },
                    { label: t('schedule.everyMonth'), value: '0 0 0 1 * ?' },
                  ].map((preset) => (
                    <Tag
                      key={preset.value}
                      style={{ cursor: 'pointer', fontSize: 11 }}
                      onClick={() => editForm.setFieldsValue({ cronExpression: preset.value })}
                    >
                      {preset.label}
                    </Tag>
                  ))}
                </Space>
              </Space>
            }
          >
            <Input maxLength={100} />
          </Form.Item>
          <Form.Item name="timezone" label={t('schedule.timezone')}>
            <Select showSearch>
              {TIMEZONES.map((tz) => (
                <Select.Option key={tz} value={tz}>
                  {tz}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default SchedulerPage
