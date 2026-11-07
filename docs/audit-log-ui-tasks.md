# Task "Audit log" — cho Antigravity

- Người tạo: Opus (spec, review) · 2026-11-07. Backend: `GET /api/v1/admin/audit-logs` (identity-service, đã route qua gateway, BFF `/api/**` đã phủ).
- Phạm vi: chỉ `apps/backoffice/**`. Không sửa Java. Cần field hoặc bộ lọc khác: thêm entry vào `docs/api-requests.md`.
- Không thêm dependency. Dùng pnpm. Giữ Materio/MUI 5 và mẫu của màn `customers` / `groups`.

## Hợp đồng API

`GET /api/v1/admin/audit-logs?actorId=&entityType=&entityId=&action=&page=&size=&sort=occurredAt,desc`

- Mọi bộ lọc tuỳ chọn, khớp chính xác (không tìm gần đúng). Bỏ trống thì không gửi tham số.
- `page` bắt đầu từ 0, `size` mặc định 20. `sort` chỉ dùng `occurredAt,desc` hoặc `occurredAt,asc`.
- Quyền: `group:manage`. Thiếu quyền trả 403.
- `data` là PageResponse: `{ pageNo, pageSize, totalPage, totalElements, items: AuditLog[] }`.

```ts
type AuditLog = {
  id: string
  sourceService: string // 'order-service' | 'identity-service' | ...
  actorId: string // Keycloak sub của nhân viên
  action: string // vd RETURN_REFUND, GROUP_UPDATE, USER_LOCK
  entityType: string // vd RETURN_REQUEST, GROUP, USER
  entityId: string
  summary?: string
  occurredAt: string // LocalDateTime, không có offset
}
```

Hành động hiện có: `RETURN_REFUND` (order-service); `GROUP_CREATE`, `GROUP_UPDATE`, `GROUP_DELETE`, `GROUP_MEMBERS_ADD`, `GROUP_MEMBERS_REMOVE`, `USER_GROUPS_UPDATE`, `STAFF_CREATE`, `USER_LOCK`, `USER_UNLOCK` (identity-service).

## A1 — API client và type (Trạng thái: done)

- `src/types/audit.ts`: type `AuditLog`.
- `src/libs/api/audit-logs.ts`: `auditLogsApi.list({ page, size, actorId?, entityType?, entityId?, action? })` theo mẫu `customersApi.list` (bỏ tham số rỗng), gọi `api.get<PageDto<AuditLog>>` rồi **`normalizeAdminPage`**. Không tự suy `totalElements` (backend luôn trả).
- Test: `src/libs/api/audit-logs.test.ts`, kiểm query string bỏ tham số rỗng và lỗi khi page không hợp lệ.

## A2 — Quyền, route, menu (Trạng thái: done)

- `src/configs/routePermissions.ts`: thêm `{ prefix: '/audit-logs', anyOf: [Permission.GROUP_MANAGE] }`. Thêm case vào `routePermissions.test.ts`.
- `VerticalMenu.tsx`, `AdminSection`: thêm `MenuItem href='/audit-logs'` "Nhật ký thao tác" (icon `ri-file-list-3-line`), hiện khi `canView('/audit-logs')`. Thêm test hiện/ẩn vào `VerticalMenu.test.tsx`.
- Quyền lấy từ AuthContext (`/bff/session`), không tự gọi lại session.

## A3 — Trang danh sách (Trạng thái: done)

- `src/app/(dashboard)/audit-logs/page.tsx` và `src/views/audit-logs/AuditLogList.tsx`.
- Cột: Thời gian (`occurredAt`, định dạng `dd/MM/yyyy HH:mm:ss`, không đổi múi giờ), Nhân viên (`actorId`, rút gọn kèm tooltip đầy đủ), Service, Hành động (Chip), Đối tượng (`entityType` và `entityId`), Mô tả (`summary`, ellipsis kèm tooltip).
- Bộ lọc: 4 ô text `actorId`, `entityType`, `entityId`, `action`, có nút "Lọc" và "Xoá lọc". Đổi lọc thì về trang 0. Phân trang server-side (TablePagination, 10/20/50).
- Trạng thái: loading (skeleton hoặc spinner), lỗi (Alert kèm nút Thử lại; 403 hiện "Bạn không có quyền xem nhật ký"), rỗng ("Chưa có thao tác nào", hoặc "Không có kết quả phù hợp" khi đang lọc).
- Chỉ đọc: không nút sửa hay xoá.
- Test: `AuditLogList.test.tsx` gồm render dòng, bộ lọc gửi đúng tham số, trạng thái rỗng, trạng thái lỗi.

## Điều kiện hoàn thành

- `cd apps/backoffice && pnpm lint` và `pnpm test` xanh. Mở qua BFF `http://localhost:8084/audit-logs`, chụp màn hình.
- Ghi `Trạng thái: done` cho từng A1–A3.

## KHÔNG làm

- Không sửa Java, gateway, BFF, `apps/storefront`.
- Không lưu token hay dữ liệu này vào localStorage, không gọi thẳng `:8080` / `:8082`.
- Không thêm dependency (date picker, data-grid…), không lọc theo khoảng ngày hay tìm gần đúng (API chưa hỗ trợ).
- Không hiện email hay tên nhân viên bằng cách gọi thêm API khác (để task sau).

## Checklist Opus review diff của Antigravity

- [ ] Diff chỉ nằm trong `apps/backoffice/**`. Không đổi dependencies trong `package.json`.
- [ ] `audit-logs.ts` gọi đúng path, bỏ tham số rỗng, có `normalizeAdminPage`.
- [ ] `routePermissions.ts` có `/audit-logs` gắn `GROUP_MANAGE`, menu ẩn khi thiếu quyền, cả hai có test.
- [ ] Phân trang server-side (`page` tính từ 0), đổi lọc thì reset trang.
- [ ] Đủ ba trạng thái loading, lỗi (có xử lý 403), rỗng. Không có nút sửa hay xoá.
- [ ] Không localStorage, không URL tuyệt đối tới service, không `console.log`.
- [ ] `occurredAt` không bị cộng hay trừ múi giờ (không `new Date(x).toISOString()`).
- [ ] Code style: hàm ≤ 40 dòng, file ≤ 400, không magic string cho permission hay path.
- [ ] Có output `pnpm lint` và `pnpm test` xanh cùng ảnh chụp qua `:8084`.
