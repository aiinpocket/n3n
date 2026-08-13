/**
 * Auto-saved drafts get machine-generated version names like `draft-1786613924084`.
 * Those are meaningless to users, so list pages should show a translated "draft"
 * label instead (the editor header already does this).
 */
export function isDraftVersion(version: string | null | undefined): boolean {
  return !!version && version.startsWith('draft-')
}
