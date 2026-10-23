-- Explicit verified OWN071-or-later installer only; runtime never installs or reseeds.
-- Installer binds one authority row to its own manifest installation UUID after schema ownership checks.
CREATE TABLE IF NOT EXISTS economy071_authority (
 schema_name VARCHAR(64) NOT NULL PRIMARY KEY,
 task_id INT NOT NULL,
 installation_id CHAR(36) NOT NULL,
 schema_version INT NOT NULL,
 migration_sha256 CHAR(64) NOT NULL,
 CHECK (task_id >= 71 AND schema_version = 1)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS economy070_wallet (
 account_name VARCHAR(45) NOT NULL PRIMARY KEY,
 balance BIGINT NOT NULL DEFAULT 0,
 CHECK (balance >= 0)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS economy070_stock (
 sku INT NOT NULL PRIMARY KEY,
 current_stock INT NOT NULL,
 next_restock BIGINT NOT NULL DEFAULT 0,
 epoch BIGINT NOT NULL DEFAULT 0,
 version BIGINT NOT NULL DEFAULT 0,
 max_stock INT NOT NULL DEFAULT 1,
 restock_days INT NOT NULL DEFAULT 0,
 source_price_revision CHAR(64) NOT NULL DEFAULT '',
 CHECK (current_stock BETWEEN 0 AND 1)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS economy070_orders (
 request_id VARCHAR(36) NOT NULL PRIMARY KEY,
 account_name VARCHAR(45) NOT NULL,
 char_id INT NOT NULL,
 kind VARCHAR(20) NOT NULL,
 sku INT NULL,
 epoch BIGINT NULL,
 amount BIGINT NOT NULL,
 revision CHAR(64) NOT NULL,
 item_object_id INT NOT NULL DEFAULT 0,
 result_value BIGINT NOT NULL DEFAULT 0,
 created_utc BIGINT NOT NULL,
 UNIQUE KEY account_sku_epoch (account_name,sku,epoch)
) ENGINE=InnoDB;
INSERT IGNORE INTO economy070_stock(sku,current_stock) VALUES
(6656,1),(6657,1),(6658,1),(6659,1),(6660,1),(6661,1),(6662,1),
(8191,1),(10314,1),(16025,1),(16026,1),(21712,1);
