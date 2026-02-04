import { Card, Skeleton, Row, Col } from 'antd'

interface Props {
  count?: number
  columns?: number
}

export default function CardSkeleton({ count = 4, columns = 4 }: Props) {
  const span = Math.floor(24 / columns)

  return (
    <Row gutter={[16, 16]}>
      {Array(count)
        .fill(null)
        .map((_, index) => (
          <Col key={index} xs={24} sm={12} md={span} lg={span}>
            <Card>
              <Skeleton active avatar paragraph={{ rows: 2 }} />
            </Card>
          </Col>
        ))}
    </Row>
  )
}
