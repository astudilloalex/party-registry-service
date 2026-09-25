-- EXPLAIN ANALYZE on 200 tenant-owned historical rows (V1-V5) used the GiST
-- country-exclusion index, sorted page/navigation rows (top-N heapsort), and
-- checked the Party join once per row. This B-tree supports descending keyset
-- traversal; the existing exclusion constraints remain the write safeguards.
CREATE INDEX ix_party_nationalities_party_created_id
    ON party_nationalities (party_id, created_at DESC, id DESC);
