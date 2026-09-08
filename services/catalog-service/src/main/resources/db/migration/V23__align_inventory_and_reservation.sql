-- CAT-P0-01: Align inventory + reservation với entity (không sửa V20/V22)
-- Inventory: available_quantity -> quantity, thêm product_id + status
alter table inventory rename column available_quantity to quantity;
alter table inventory add column if not exists product_id varchar(255);
alter table inventory add column if not exists status varchar(20);

update inventory set status = 'ACTIVE' where status is null;
update inventory inventory_row
set product_id = variant.product_id
from product_variant variant
where inventory_row.variant_id = variant.id
  and inventory_row.product_id is null;

alter table inventory alter column product_id set not null;
alter table inventory alter column status set not null;

-- inventory_reservation: V22 tạo header với variant_id/quantity thừa trên header
-- (đúng phải là header + inventory_reservation_item). Chuyển dữ liệu rồi drop cột thừa.
do $$
begin
    if exists (select 1 from information_schema.columns
               where table_name='inventory_reservation' and column_name='variant_id') then
        insert into inventory_reservation_item (id, reservation_id, variant_id, quantity, created_at, updated_at)
        select gen_random_uuid()::text, id, variant_id, quantity, created_at, updated_at
        from inventory_reservation
        where variant_id is not null
          and not exists (select 1 from inventory_reservation_item i where i.reservation_id = inventory_reservation.id);

        alter table inventory_reservation drop column variant_id;
        alter table inventory_reservation drop column quantity;
    end if;
end $$;

alter table inventory_reservation_item add column if not exists created_by varchar(255);
alter table inventory_reservation_item add column if not exists updated_by varchar(255);
