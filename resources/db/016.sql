DELETE FROM auctions WHERE id NOT IN (SELECT MAX(id) FROM auctions GROUP BY identifier)

UPDATE entries SET auction_id=(SELECT id FROM auctions WHERE auctions.identifier=entries.identifier)

DELETE FROM entries WHERE id NOT IN (SELECT MIN(id) FROM entries GROUP BY auction_id)
