-- A product belongs to several categories; the single category_id column was never populated.
ALTER TABLE public.checkout_item ADD COLUMN category_ids text;
