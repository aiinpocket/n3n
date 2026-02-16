import { Dropdown } from 'antd'
import type { MenuProps } from 'antd'
import { GlobalOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'

const languages = [
  { key: 'zh-TW', label: '繁體中文', flag: '🇹🇼' },
  { key: 'en', label: 'English', flag: '🇺🇸' },
  { key: 'ja', label: '日本語', flag: '🇯🇵' },
]

export default function LanguageSwitcher() {
  const { i18n, t } = useTranslation()

  const handleLanguageChange: MenuProps['onClick'] = ({ key }) => {
    i18n.changeLanguage(key)
  }

  const currentLang = languages.find((lang) => lang.key === i18n.language) || languages[1]

  const items: MenuProps['items'] = languages.map((lang) => ({
    key: lang.key,
    label: (
      <span>
        <span style={{ marginRight: 8 }}>{lang.flag}</span>
        {lang.label}
      </span>
    ),
  }))

  return (
    <Dropdown
      menu={{ items, onClick: handleLanguageChange, selectedKeys: [i18n.language] }}
      placement="bottomRight"
      trigger={['click']}
    >
      <span
        style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 4 }}
        role="button"
        aria-label={t('language.title')}
        tabIndex={0}
        onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); (e.currentTarget as HTMLElement).click() } }}
      >
        <GlobalOutlined />
        <span>{currentLang.flag}</span>
      </span>
    </Dropdown>
  )
}
