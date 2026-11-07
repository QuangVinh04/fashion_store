export type AuditLog = {
  id: string
  sourceService: string
  actorId: string
  action: string
  entityType: string
  entityId: string
  summary?: string
  occurredAt: string
}
