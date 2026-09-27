-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信：zhuatech / zhuatech2
CREATE TABLE organizations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  name VARCHAR(200),
  enabled BOOLEAN NOT NULL
);
CREATE TABLE roles (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  name VARCHAR(120),
  permissions VARCHAR(2000),
  builtin BOOLEAN NOT NULL,
  UNIQUE(name)
);
CREATE TABLE accounts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  username VARCHAR(120),
  display_name VARCHAR(200),
  password_hash VARCHAR(200),
  org_id BIGINT,
  role_id BIGINT,
  customer_id BIGINT,
  enabled BOOLEAN NOT NULL,
  FOREIGN KEY (org_id) REFERENCES organizations(id),
  FOREIGN KEY (role_id) REFERENCES roles(id),
  UNIQUE (username)
);
CREATE TABLE customers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  org_id BIGINT NOT NULL,
  name VARCHAR(200),
  site VARCHAR(500),
  contact VARCHAR(200),
  enabled BOOLEAN NOT NULL,
  FOREIGN KEY (org_id) REFERENCES organizations(id)
);
CREATE INDEX idx_customers_org ON customers(org_id,id);
CREATE TABLE assets (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  org_id BIGINT NOT NULL,
  name VARCHAR(200),
  customer_id BIGINT,
  serial_number VARCHAR(200),
  location VARCHAR(500),
  warranty_until DATE,
  enabled BOOLEAN NOT NULL,
  FOREIGN KEY (org_id) REFERENCES organizations(id),
  FOREIGN KEY (customer_id) REFERENCES customers(id)
);
CREATE INDEX idx_assets_org ON assets(org_id,id);
CREATE TABLE contracts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  org_id BIGINT NOT NULL,
  name VARCHAR(200),
  customer_id BIGINT,
  asset_id BIGINT,
  start_date DATE,
  end_date DATE,
  response_hours INTEGER NOT NULL,
  resolution_hours INTEGER NOT NULL,
  enabled BOOLEAN NOT NULL,
  FOREIGN KEY (org_id) REFERENCES organizations(id),
  FOREIGN KEY (customer_id) REFERENCES customers(id),
  FOREIGN KEY (asset_id) REFERENCES assets(id)
);
CREATE INDEX idx_contracts_org ON contracts(org_id,id);
CREATE TABLE work_orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  org_id BIGINT NOT NULL,
  name VARCHAR(200),
  customer_id BIGINT,
  asset_id BIGINT,
  contract_id BIGINT,
  assignee_id BIGINT,
  state VARCHAR(200),
  priority VARCHAR(200),
  description VARCHAR(2000),
  diagnosis VARCHAR(2000),
  resolution VARCHAR(2000),
  reason VARCHAR(2000),
  coverage VARCHAR(200),
  due_at TIMESTAMP(6),
  respond_by TIMESTAMP(6),
  accepted_at TIMESTAMP(6),
  resolved_at TIMESTAMP(6),
  closed_at TIMESTAMP(6),
  labor_minutes INTEGER NOT NULL,
  labor_rate DECIMAL(14,2),
  part_cost DECIMAL(14,2),
  quoted_amount DECIMAL(14,2),
  settlement_state VARCHAR(200),
  payment_reference VARCHAR(200),
  safety_checked BOOLEAN NOT NULL,
  test_passed BOOLEAN NOT NULL,
  waiting_at TIMESTAMP(6),
  quote_approved BOOLEAN NOT NULL,
  plan_id BIGINT,
  planned_date DATE,
  FOREIGN KEY (org_id) REFERENCES organizations(id),
  FOREIGN KEY (asset_id) REFERENCES assets(id),
  FOREIGN KEY (customer_id) REFERENCES customers(id),
  FOREIGN KEY (contract_id) REFERENCES contracts(id),
  FOREIGN KEY (assignee_id) REFERENCES accounts(id),
  UNIQUE(plan_id,planned_date)
);
CREATE INDEX idx_work_orders_org ON work_orders(org_id,id);
CREATE TABLE parts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  org_id BIGINT NOT NULL,
  name VARCHAR(200),
  sku VARCHAR(120),
  unit VARCHAR(200),
  stock INTEGER NOT NULL,
  unit_cost DECIMAL(14,2),
  enabled BOOLEAN NOT NULL,
  FOREIGN KEY (org_id) REFERENCES organizations(id),
  CHECK(stock >= 0),
  UNIQUE(org_id,sku)
);
CREATE INDEX idx_parts_org ON parts(org_id,id);
CREATE TABLE movements (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  org_id BIGINT NOT NULL,
  part_id BIGINT,
  order_id BIGINT,
  kind VARCHAR(200),
  quantity INTEGER NOT NULL,
  unit_cost DECIMAL(14,2),
  request_key VARCHAR(120),
  issue_id BIGINT,
  actor_id BIGINT,
  reason VARCHAR(2000),
  FOREIGN KEY (org_id) REFERENCES organizations(id),
  FOREIGN KEY (part_id) REFERENCES parts(id),
  FOREIGN KEY (order_id) REFERENCES work_orders(id),
  UNIQUE(org_id,request_key)
);
CREATE INDEX idx_movements_org ON movements(org_id,id);
CREATE TABLE plans (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  org_id BIGINT NOT NULL,
  name VARCHAR(200),
  asset_id BIGINT,
  next_date DATE,
  interval_days INTEGER NOT NULL,
  enabled BOOLEAN NOT NULL,
  description VARCHAR(2000),
  FOREIGN KEY (org_id) REFERENCES organizations(id),
  FOREIGN KEY (asset_id) REFERENCES assets(id)
);
CREATE INDEX idx_plans_org ON plans(org_id,id);
CREATE TABLE knowledge (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  org_id BIGINT NOT NULL,
  name VARCHAR(200),
  category VARCHAR(200),
  content VARCHAR(2000),
  published BOOLEAN NOT NULL,
  FOREIGN KEY (org_id) REFERENCES organizations(id)
);
CREATE INDEX idx_knowledge_org ON knowledge(org_id,id);
CREATE TABLE audit_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  org_id BIGINT NOT NULL,
  actor VARCHAR(200),
  action VARCHAR(200),
  resource VARCHAR(200),
  detail VARCHAR(2000),
  FOREIGN KEY (org_id) REFERENCES organizations(id)
);
CREATE INDEX idx_audit_events_org ON audit_events(org_id,id);
CREATE TABLE config_entries (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  org_id BIGINT NOT NULL,
  name VARCHAR(200),
  kind VARCHAR(200),
  config_value VARCHAR(500),
  enabled BOOLEAN NOT NULL,
  FOREIGN KEY (org_id) REFERENCES organizations(id)
);
CREATE INDEX idx_config_entries_org ON config_entries(org_id,id);
CREATE TABLE attachments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version BIGINT NOT NULL,
  created_at TIMESTAMP(6) NOT NULL,
  updated_at TIMESTAMP(6) NOT NULL,
  org_id BIGINT NOT NULL,
  order_id BIGINT,
  original_name VARCHAR(200),
  storage_name VARCHAR(120),
  content_type VARCHAR(200),
  size BIGINT NOT NULL,
  FOREIGN KEY (org_id) REFERENCES organizations(id),
  FOREIGN KEY (order_id) REFERENCES work_orders(id)
);
CREATE INDEX idx_attachments_org ON attachments(org_id,id);

ALTER TABLE accounts ADD CONSTRAINT fk_account_customer FOREIGN KEY(customer_id) REFERENCES customers(id);
ALTER TABLE work_orders ADD CONSTRAINT fk_order_plan FOREIGN KEY(plan_id) REFERENCES plans(id);
ALTER TABLE movements ADD CONSTRAINT fk_movement_issue FOREIGN KEY(issue_id) REFERENCES movements(id);
