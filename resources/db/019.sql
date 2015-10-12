ALTER TABLE entries ADD COLUMN created_at timestamp default NULL

ALTER TABLE entries ADD COLUMN updated_at timestamp default NULL

ALTER TABLE auctions ADD COLUMN created_at timestamp default NULL

ALTER TABLE auctions ADD COLUMN updated_at timestamp default NULL
