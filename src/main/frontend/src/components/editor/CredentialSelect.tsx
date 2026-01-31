import { useEffect, useState, useCallback } from 'react'
import { Select, Space, Typography, Button, Tooltip } from 'antd'
import { PlusOutlined, ReloadOutlined, SafetyOutlined, WarningOutlined } from '@ant-design/icons'
import { credentialApi, Credential } from '../../api/credential'
import { logger } from '../../utils/logger'

const { Text } = Typography

interface CredentialSelectProps {
  value?: string
  onChange?: (value: string | undefined) => void
  credentialType?: string
  placeholder?: string
  disabled?: boolean
  allowClear?: boolean
  onCreateNew?: () => void
}

export default function CredentialSelect({
  value,
  onChange,
  credentialType,
  placeholder = 'Select credential',
  disabled = false,
  allowClear = true,
  onCreateNew,
}: CredentialSelectProps) {
  const [credentials, setCredentials] = useState<Credential[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const fetchCredentials = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const response = await credentialApi.list(0, 100)
      let filtered = response.content

      // Filter by type if specified
      if (credentialType) {
        filtered = filtered.filter(c => c.type === credentialType)
      }

      setCredentials(filtered)
    } catch (err) {
      logger.error('Failed to fetch credentials', err)
      setError('Failed to load credentials')
    } finally {
      setLoading(false)
    }
  }, [credentialType])

  useEffect(() => {
    fetchCredentials()
  }, [fetchCredentials])

  const handleChange = (newValue: string | undefined) => {
    onChange?.(newValue)
  }

  return (
    <div>
      <Space.Compact style={{ width: '100%' }}>
        <Select
          value={value}
          onChange={handleChange}
          placeholder={placeholder}
          disabled={disabled}
          loading={loading}
          allowClear={allowClear}
          style={{ flex: 1 }}
          showSearch
          optionFilterProp="label"
          status={error ? 'error' : undefined}
          notFoundContent={
            error ? (
              <Text type="danger">{error}</Text>
            ) : credentials.length === 0 ? (
              <div style={{ padding: 8, textAlign: 'center' }}>
                <Text type="secondary">
                  {credentialType
                    ? `No ${credentialType} credentials found`
                    : 'No credentials found'}
                </Text>
                {onCreateNew && (
                  <Button
                    type="link"
                    size="small"
                    icon={<PlusOutlined />}
                    onClick={onCreateNew}
                  >
                    Create new
                  </Button>
                )}
              </div>
            ) : null
          }
          options={credentials.map(cred => ({
            value: cred.id,
            label: (
              <Space>
                <SafetyOutlined style={{ color: '#52c41a' }} />
                <span>{cred.name}</span>
                {cred.description && (
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    - {cred.description}
                  </Text>
                )}
              </Space>
            ),
            searchLabel: cred.name,
          }))}
        />
        <Tooltip title="Refresh">
          <Button
            icon={<ReloadOutlined />}
            onClick={fetchCredentials}
            loading={loading}
          />
        </Tooltip>
        {onCreateNew && (
          <Tooltip title="Create new credential">
            <Button
              icon={<PlusOutlined />}
              onClick={onCreateNew}
            />
          </Tooltip>
        )}
      </Space.Compact>

      {!value && credentialType && (
        <div style={{ marginTop: 4 }}>
          <Text type="warning" style={{ fontSize: 12 }}>
            <WarningOutlined /> {credentialType} credential required
          </Text>
        </div>
      )}
    </div>
  )
}
