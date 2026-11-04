# Trang quản lý thành viên của nhóm (backoffice) — việc của Antigravity

API đã xong (xem `docs/api-requests.md`, mục RBAC). Chỉ sửa `apps/backoffice/**`.

## Thay đổi trong `/groups`

- Cột Thao tác của `GroupList.tsx`: thay hai nút "Xem thành viên" (số thành viên và biểu tượng) bằng **một nút "Quản lý thành viên"**, điều hướng tới trang `/groups/{id}/members`. Bỏ dialog `GroupMembersDialog` khi trang mới xong.
- `Group.memberCount` luôn có số trong danh sách nhóm (kể cả `Customer`); vẫn khai báo `number | null` vì nhóm nằm trong `groups` của một user thì là `null`. Bỏ phần ẩn nút theo `memberCount === null`.
- Route `/groups/{id}/members` yêu cầu `group:manage` (thêm vào `routePermissions.ts`; prefix `/groups` đã khớp).

## Trang `/groups/{id}/members`

- Đầu trang: tên nhóm (lấy từ `groupsApi.list`, tìm theo id), số thành viên, nút quay lại `/groups`, nút **Thêm thành viên**.
- Ô tìm kiếm (debounce, dùng `useDebouncedSearch` sẵn có) truyền `keyword` cho `GET /api/v1/admin/groups/{id}/members?keyword=&page=&size=20`; đổi từ khóa thì về trang 0.
- Bảng phân trang theo endpoint trên, chỉ các cột: ô tick, email, họ tên, trạng thái (`isActive`: Hoạt động / Đã khóa, `null` thì "—"), thao tác. `email` / `fullName` null (hiếm) → hiện id rút gọn.
- Cột thao tác: **Xem chi tiết** → mở `CustomerDetailDialog` (hoặc trang chi tiết khách hiện có). **Ẩn nút khi thiếu `customer:read`**, vì `group:manage` không kéo theo quyền này.
- Tick nhiều dòng → thanh hành động hàng loạt: số đã chọn + nút **Xóa khỏi nhóm** (`ConfirmActionDialog`, nêu số người sẽ bị đăng xuất). Có ô "chọn tất cả trang này".
- **Thêm thành viên**: dialog có ô tìm (debounce, tối thiểu 2 ký tự) gọi `GET /api/v1/admin/groups/{id}/candidates?keyword=` (chỉ cần `group:manage`, đã loại thành viên hiện có, tối đa 20 kết quả, gồm cả người chưa từng đăng nhập). Chọn nhiều người (giữ lựa chọn khi đổi từ khóa), nút Thêm.
- Gọi `POST` / `DELETE /api/v1/admin/groups/{id}/members` body `{ userIds }` (tối đa 100 mỗi lần: chia lô nếu chọn nhiều hơn). Sau khi xong invalidate query thành viên và danh sách nhóm.

## Quy tắc theo nhóm

| nhóm | hành vi |
|---|---|
| `Customer` | chỉ xem: ẩn tick, "Xóa khỏi nhóm", "Thêm thành viên" (backend trả `2036` nếu cố làm). |
| `Admin` | cho thêm / gỡ. Gỡ có thể lỗi từng người: `2035` (tự rời), `2034` (còn một admin). Không cho tick chính mình. |
| nhóm khác | thêm / gỡ bình thường. |

## Hiển thị kết quả

Response là `{ succeededUserIds, failures: [{ userId, code, message }], notLoggedOutUserIds }`.
- Không có `failures`: thông báo thành công.
- Có `failures`: cảnh báo nêu "X thành công, Y thất bại" kèm danh sách từng lỗi (email người đó nếu có trong bảng, `message` từ backend). Phần thành công vẫn được áp dụng, đừng báo lỗi chung.
- `notLoggedOutUserIds` khác rỗng: dùng lại `changeWarning` như các màn hình khác.

## Test tối thiểu

- Hàm gom kết quả (thành công / thất bại → thông báo) tách ra file `.ts` riêng và test.
- Trang: nhóm `Customer` không hiện tick / xóa / thêm; thiếu `customer:read` thì không có nút xem chi tiết (nút thêm vẫn có).
- `groups.test.ts`: `addMembers` / `removeMembers` / `candidates` / `members` (có `keyword`) gọi đúng method, URL và body.
