-- Two kinds of coupon: PRODUCT (reduces goods total) and SHIPPING (reduces the shipping fee).
-- shipping_fee keeps the real carrier fee; the coupon discount is stored next to it.
ALTER TABLE public.promotion
    ADD COLUMN target character varying(20) DEFAULT 'PRODUCT' NOT NULL,
    ADD COLUMN description character varying(255),
    ADD COLUMN is_public boolean DEFAULT false NOT NULL;

ALTER TABLE public.checkout
    ADD COLUMN shipping_coupon_code character varying(100),
    ADD COLUMN shipping_discount_amount numeric(19,2) DEFAULT 0 NOT NULL;

ALTER TABLE public.orders
    ADD COLUMN shipping_discount_amount numeric(19,2) DEFAULT 0 NOT NULL;

-- An order may now hold one PRODUCT and one SHIPPING coupon usage.
-- uq_coupon_usage_promo_user_order (promotion_id, user_id, order_id) still prevents duplicates.
ALTER TABLE public.coupon_usage DROP CONSTRAINT coupon_usage_order_id_key;
