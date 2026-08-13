import { Fragment } from 'react'
import type { CSSProperties, ReactNode } from 'react'
import { Empty, Spin } from 'antd'

/**
 * Drop-in replacement for the deprecated antd `List` component (removed in antd v7),
 * covering the prop subset this codebase uses: dataSource/renderItem/size/loading/
 * locale.emptyText plus List.Item(actions) and List.Item.Meta(avatar/title/description).
 * Import it as `List` so existing JSX keeps working unchanged.
 */

interface ListProps<T> {
  dataSource?: readonly T[]
  renderItem: (item: T, index: number) => ReactNode
  size?: 'small' | 'default' | 'large'
  loading?: boolean
  locale?: { emptyText?: ReactNode }
  split?: boolean
  style?: CSSProperties
  className?: string
}

interface ListItemProps {
  children?: ReactNode
  actions?: ReactNode[]
  style?: CSSProperties
  className?: string
  onClick?: () => void
  onMouseEnter?: () => void
  onMouseLeave?: () => void
}

interface ListItemMetaProps {
  avatar?: ReactNode
  title?: ReactNode
  description?: ReactNode
  style?: CSSProperties
  className?: string
}

function ListItemMeta({ avatar, title, description, style, className }: ListItemMetaProps) {
  return (
    <div className={`simple-list-item-meta${className ? ` ${className}` : ''}`} style={style}>
      {avatar && <div className="simple-list-item-meta-avatar">{avatar}</div>}
      <div className="simple-list-item-meta-content">
        {title != null && <div className="simple-list-item-meta-title">{title}</div>}
        {description != null && <div className="simple-list-item-meta-description">{description}</div>}
      </div>
    </div>
  )
}

function ListItem({ children, actions, style, className, onClick, onMouseEnter, onMouseLeave }: ListItemProps) {
  return (
    <div
      className={`simple-list-item${className ? ` ${className}` : ''}`}
      style={style}
      onClick={onClick}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      role={onClick ? 'button' : undefined}
    >
      <div className="simple-list-item-main">{children}</div>
      {actions && actions.length > 0 && (
        <div className="simple-list-item-actions">
          {actions.map((action, i) => (
            <span key={i} className="simple-list-item-action">{action}</span>
          ))}
        </div>
      )}
    </div>
  )
}

ListItem.Meta = ListItemMeta

function List<T>({ dataSource, renderItem, size = 'default', loading = false, locale, split = true, style, className }: ListProps<T>) {
  const items = dataSource ?? []
  const body = items.length === 0
    ? (
      <div className="simple-list-empty">
        {typeof locale?.emptyText === 'string' || locale?.emptyText == null
          ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={locale?.emptyText} />
          : locale.emptyText}
      </div>
    )
    : items.map((item, index) => <Fragment key={index}>{renderItem(item, index)}</Fragment>)

  const classes = [
    'simple-list',
    size === 'small' ? 'simple-list-sm' : '',
    split ? 'simple-list-split' : '',
    className || '',
  ].filter(Boolean).join(' ')

  return (
    <Spin spinning={loading}>
      <div className={classes} style={style}>
        {body}
      </div>
    </Spin>
  )
}

List.Item = ListItem

export default List
