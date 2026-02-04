import { Skeleton, Table } from 'antd'

interface Props {
  rows?: number
  columns?: number
}

export default function TableSkeleton({ rows = 5, columns = 4 }: Props) {
  const dataSource = Array(rows)
    .fill(null)
    .map((_, index) => ({
      key: index,
      ...Array(columns)
        .fill(null)
        .reduce((acc, _, colIndex) => {
          acc[`col${colIndex}`] = null
          return acc
        }, {} as Record<string, null>),
    }))

  const tableColumns = Array(columns)
    .fill(null)
    .map((_, index) => ({
      title: <Skeleton.Input active size="small" style={{ width: 80 }} />,
      dataIndex: `col${index}`,
      key: `col${index}`,
      render: () => <Skeleton.Input active size="small" style={{ width: '100%' }} />,
    }))

  return (
    <Table
      dataSource={dataSource}
      columns={tableColumns}
      pagination={false}
      style={{ opacity: 0.6 }}
    />
  )
}
