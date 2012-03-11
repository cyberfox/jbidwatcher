ALTER TABLE entries ADD COLUMN sniped_amount decimal(10,2) default NULL

ALTER TABLE entries ADD COLUMN auto_canceled smallint

ALTER TABLE entries ADD COLUMN was_sniped smallint default 0
