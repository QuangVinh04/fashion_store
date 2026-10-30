# Quy trình làm việc backend

## Chạy Maven với JDK 21

Từ bash **phải** pin JDK 21 (`java` trên PATH là 1.8, `JAVA_HOME` mặc định là jdk-17):

```bash
export JAVA_HOME="C:/Program Files/Java/jdk-21"          # Linux: /usr/lib/jvm/java-21-openjdk-amd64

./mvnw -o -pl services/catalog-service -am clean test     # một service + dependency
./mvnw -o -pl services/catalog-service clean test         # một service (dependency đã install)
./mvnw -o -pl services/catalog-service test -Dtest=ProductServiceImplTest
./mvnw -o -pl services/catalog-service test -Dtest='ProductServiceImplTest#createsVariant*'
./mvnw -o -DskipTests clean install                       # toàn bộ reactor
```

- `-o` (offline) chạy được vì local repo đã có sẵn. Chỉ bỏ `-o` khi thiếu dependency.

## Thay đổi logic và refactor — Spec → Plan → TDD → Review

1. Spec: viết 1–3 dòng: thay đổi gì, kiểm chứng bằng cách nào.
2. Plan: liệt kê file sẽ sửa. Thay đổi lớn hoặc xuyên service: chờ người dùng đồng ý trước.
3. TDD: viết hoặc sửa test trước, xem test fail, rồi implement đến khi `./mvnw -o -pl services/<svc> clean test` xanh.
4. Review: đọc lại `git diff` của chính mình trước khi báo cáo.

Sửa typo, đổi tên, sửa tài liệu: bỏ qua quy trình, làm luôn.

## Trap khi build

- Chạy `./mvnw` không có `JAVA_HOME=jdk-21` lỗi `release version 21 not supported`. Đó là lỗi JDK, không phải lỗi code.
- Sau khi di chuyển/đổi tên package, chạy với `clean`. Class cũ trong `target/test-classes` gây lỗi giả `TestEngine with ID 'junit-jupiter' failed to discover tests`.
- MapStruct + Lombok: mapper impl được sinh vào `target/generated-sources`. Thấy mapping bị thiếu thì `clean` trước khi debug.
