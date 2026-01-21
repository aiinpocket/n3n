import { useEffect, useState, useCallback } from 'react'
import { Button, Card, Table, Space, Modal, Form, Input, message, Popconfirm, Tag } from 'antd'
import { PlusOutlined, EditOutlined, PlayCircleOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useFlowStore } from '../stores/flowStore'
import type { Flow } from '../api/flow'

export default function FlowListPage() {
  const navigate = useNavigate()
  const { flows, totalElements, loading, currentPage, pageSize, searchQuery, fetchFlows, setSearchQuery, createFlow, deleteFlow } = useFlowStore()
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [form] = Form.useForm()
  const [creating, setCreating] = useState(false)
  const [searchValue, setSearchValue] = useState(searchQuery)

  useEffect(() => {
    fetchFlows()
  }, [fetchFlows])

  // Debounced search
  const handleSearch = useCallback((value: string) => {
    setSearchQuery(value)
    fetchFlows(0, pageSize, value)
  }, [fetchFlows, pageSize, setSearchQuery])

  const handleCreate = async (values: { name: string; description?: string }) => {
    setCreating(true)
    try {
      const flow = await createFlow(values.name, values.description)
      message.success('流程建立成功')
      setCreateModalOpen(false)
      form.resetFields()
      navigate(`/flows/${flow.id}/edit`)
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } }
      message.error(err.response?.data?.message || '建立失敗')
    } finally {
      setCreating(false)
    }
  }

  const handleDelete = async (id: string) => {
    try {
      await deleteFlow(id)
      message.success('流程已刪除')
    } catch {
      message.error('刪除失敗')
    }
  }

  const columns = [
    {
      title: '名稱',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: Flow) => (
        <a onClick={() => navigate(`/flows/${record.id}/edit`)}>{name}</a>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: '最新版本',
      dataIndex: 'latestVersion',
      key: 'latestVersion',
      render: (version: string | null) => version || '-',
    },
    {
      title: '已發布版本',
      dataIndex: 'publishedVersion',
      key: 'publishedVersion',
      render: (version: string | null) =>
        version ? <Tag color="green">{version}</Tag> : <Tag>未發布</Tag>,
    },
    {
      title: '更新時間',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      render: (date: string) => new Date(date).toLocaleString('zh-TW'),
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_: unknown, record: Flow) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => navigate(`/flows/${record.id}/edit`)}
          >
            編輯
          </Button>
          <Button
            type="link"
            size="small"
            icon={<PlayCircleOutlined />}
            disabled={!record.publishedVersion}
            onClick={() => navigate(`/executions/new?flowId=${record.id}`)}
          >
            執行
          </Button>
          <Popconfirm
            title="確定要刪除此流程？"
            onConfirm={() => handleDelete(record.id)}
            okText="確定"
            cancelText="取消"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              刪除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <>
      <Card
        title="流程列表"
        extra={
          <Space>
            <Input.Search
              placeholder="搜尋流程名稱或描述"
              allowClear
              value={searchValue}
              onChange={(e) => setSearchValue(e.target.value)}
              onSearch={handleSearch}
              style={{ width: 250 }}
              enterButton={<SearchOutlined />}
            />
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setCreateModalOpen(true)}
            >
              新增流程
            </Button>
          </Space>
        }
      >
        <Table
          columns={columns}
          dataSource={flows}
          rowKey="id"
          loading={loading}
          pagination={{
            current: currentPage + 1,
            pageSize,
            total: totalElements,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 項`,
            onChange: (page, size) => fetchFlows(page - 1, size, searchQuery),
          }}
        />
      </Card>

      <Modal
        title="新增流程"
        open={createModalOpen}
        onCancel={() => {
          setCreateModalOpen(false)
          form.resetFields()
        }}
        footer={null}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleCreate}
        >
          <Form.Item
            name="name"
            label="流程名稱"
            rules={[{ required: true, message: '請輸入流程名稱' }]}
          >
            <Input placeholder="請輸入流程名稱" />
          </Form.Item>
          <Form.Item
            name="description"
            label="描述"
          >
            <Input.TextArea rows={3} placeholder="請輸入流程描述（選填）" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0, textAlign: 'right' }}>
            <Space>
              <Button onClick={() => {
                setCreateModalOpen(false)
                form.resetFields()
              }}>
                取消
              </Button>
              <Button type="primary" htmlType="submit" loading={creating}>
                建立
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
