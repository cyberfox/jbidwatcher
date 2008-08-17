CREATE TABLE archived_auctions (
  id integer NOT NULL,
  identifier varchar(255) default NULL,
  current_bid decimal(10,2) default NULL,
  buy_now decimal(10,2) default NULL,
  minimum_bid decimal(10,2) default NULL,
  shipping decimal(10,2) default NULL,
  insurance decimal(10,2) default NULL,
  usd_current decimal(10,2) default NULL,
  usd_buy_now decimal(10,2) default NULL,
  currency varchar(10) default NULL,
  started_at timestamp default NULL,
  ending_at timestamp default NULL,
  high_bidder varchar(255) default NULL,
  high_bidder_email varchar(255) default NULL,
  title varchar(255) default NULL,
  quantity integer default NULL,
  bid_count integer default NULL,
  seller_id integer default NULL,
  location varchar(255) default NULL,
  paypal smallint default NULL,
  reserve_met smallint default NULL,
  private smallint default NULL,
  reserve smallint default NULL,
  dutch smallint default NULL,
  no_thumbnail smallint default NULL,
  has_thumbnail smallint default NULL,
  fixed_price smallint default NULL,
  optional_insurance smallint default NULL,
  outbid smallint default NULL,
  PRIMARY KEY  (id)
)

CREATE INDEX IDX_Archive_Auction_Identifier ON archived_auctions(identifier)

ALTER TABLE entries ADD COLUMN identifier varchar(255) default NULL

CREATE INDEX IDX_Entry_Identifier ON entries(identifier)

UPDATE entries SET identifier=(SELECT identifier FROM auctions WHERE auctions.id=entries.auction_id)
