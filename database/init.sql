-- 古代沙船稳性仿真与装载优化系统 - PostgreSQL初始化脚本

CREATE DATABASE sand_ship_stability;
\c sand_ship_stability;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS postgis;

-- 船舶信息表
CREATE TABLE IF NOT EXISTS ships (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    ship_type VARCHAR(50) NOT NULL DEFAULT '方艄沙船',
    length_overall NUMERIC(10, 2) NOT NULL,
    breadth_molded NUMERIC(10, 2) NOT NULL,
    depth_molded NUMERIC(10, 2) NOT NULL,
    design_draft NUMERIC(10, 2) NOT NULL,
    displacement NUMERIC(12, 2) NOT NULL,
    lightship_weight NUMERIC(12, 2) NOT NULL,
    deadweight_tons NUMERIC(12, 2) NOT NULL,
    metacentric_height_design NUMERIC(8, 4) NOT NULL DEFAULT 0.8,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 货舱信息表
CREATE TABLE IF NOT EXISTS cargo_holds (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ship_id UUID NOT NULL REFERENCES ships(id) ON DELETE CASCADE,
    hold_number INTEGER NOT NULL,
    hold_name VARCHAR(50) NOT NULL,
    capacity_cubic NUMERIC(12, 2) NOT NULL,
    max_weight NUMERIC(12, 2) NOT NULL,
    center_gravity_x NUMERIC(10, 2) NOT NULL,
    center_gravity_y NUMERIC(10, 2) NOT NULL,
    center_gravity_z NUMERIC(10, 2) NOT NULL,
    is_tank BOOLEAN DEFAULT false,
    tank_length NUMERIC(10, 2),
    tank_breadth NUMERIC(10, 2),
    liquid_density NUMERIC(10, 3),
    tank_fullness NUMERIC(5, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 货物类型表
CREATE TABLE IF NOT EXISTS cargo_types (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cargo_code VARCHAR(20) NOT NULL UNIQUE,
    cargo_name VARCHAR(50) NOT NULL,
    density NUMERIC(10, 3) NOT NULL,
    unit_weight NUMERIC(10, 2) NOT NULL,
    color_hex VARCHAR(7) NOT NULL DEFAULT '#FFD700',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 传感器数据表
CREATE TABLE IF NOT EXISTS sensor_data (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ship_id UUID NOT NULL REFERENCES ships(id) ON DELETE CASCADE,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    draft_forward NUMERIC(8, 3),
    draft_aft NUMERIC(8, 3),
    draft_mean NUMERIC(8, 3),
    roll_angle NUMERIC(8, 3),
    pitch_angle NUMERIC(8, 3),
    heel_angle NUMERIC(8, 3),
    bilge_water_level NUMERIC(8, 3),
    water_temperature NUMERIC(8, 2),
    wind_speed NUMERIC(8, 2),
    wind_direction NUMERIC(8, 2),
    wave_height NUMERIC(8, 3),
    mqtt_topic VARCHAR(200),
    raw_payload JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sensor_data_ship_time ON sensor_data(ship_id, timestamp DESC);

-- 货物装载记录表
CREATE TABLE IF NOT EXISTS cargo_loadings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ship_id UUID NOT NULL REFERENCES ships(id) ON DELETE CASCADE,
    hold_id UUID NOT NULL REFERENCES cargo_holds(id) ON DELETE CASCADE,
    cargo_type_id UUID NOT NULL REFERENCES cargo_types(id),
    weight NUMERIC(12, 2) NOT NULL,
    volume NUMERIC(12, 2) NOT NULL,
    loading_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    loading_order INTEGER,
    is_optimized BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 稳性计算结果表
CREATE TABLE IF NOT EXISTS stability_results (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ship_id UUID NOT NULL REFERENCES ships(id) ON DELETE CASCADE,
    sensor_data_id UUID REFERENCES sensor_data(id),
    calculation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    displacement_actual NUMERIC(12, 2),
    center_gravity_x NUMERIC(10, 3),
    center_gravity_y NUMERIC(10, 3),
    center_gravity_z NUMERIC(10, 3),
    center_buoyancy_x NUMERIC(10, 3),
    center_buoyancy_y NUMERIC(10, 3),
    center_buoyancy_z NUMERIC(10, 3),
    metacentric_height_transverse NUMERIC(8, 4),
    metacentric_height_longitudinal NUMERIC(8, 4),
    righting_arm NUMERIC(10, 4),
    righting_moment NUMERIC(12, 2),
    roll_period NUMERIC(10, 3),
    gm_value NUMERIC(8, 4),
    free_surface_correction NUMERIC(8, 4),
    gm_uncorrected NUMERIC(8, 4),
    stability_status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    warning_message TEXT,
    curve_points JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stability_ship_time ON stability_results(ship_id, calculation_time DESC);

-- 装载优化结果表
CREATE TABLE IF NOT EXISTS loading_optimizations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ship_id UUID NOT NULL REFERENCES ships(id) ON DELETE CASCADE,
    optimization_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_cargo_weight NUMERIC(12, 2),
    total_cargo_volume NUMERIC(12, 2),
    effective_payload NUMERIC(12, 2),
    min_gm_required NUMERIC(8, 4) NOT NULL DEFAULT 0.3,
    resulting_gm NUMERIC(8, 4),
    grain_weight NUMERIC(12, 2),
    salt_weight NUMERIC(12, 2),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    solution JSONB,
    objective_value NUMERIC(12, 2),
    solve_time_ms NUMERIC(12, 2),
    algorithm_used VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 告警记录表
CREATE TABLE IF NOT EXISTS alarms (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ship_id UUID NOT NULL REFERENCES ships(id) ON DELETE CASCADE,
    sensor_data_id UUID REFERENCES sensor_data(id),
    stability_result_id UUID REFERENCES stability_results(id),
    alarm_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    alarm_type VARCHAR(50) NOT NULL,
    alarm_level VARCHAR(20) NOT NULL DEFAULT 'WARNING',
    severity VARCHAR(20),
    alarm_message TEXT NOT NULL,
    description TEXT,
    parameter_name VARCHAR(50),
    parameter_value NUMERIC(12, 4),
    threshold_value NUMERIC(12, 4),
    is_acknowledged BOOLEAN DEFAULT false,
    acknowledged BOOLEAN DEFAULT false,
    acknowledged_at TIMESTAMP,
    acknowledged_by VARCHAR(100),
    triggered_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alarms_ship_time ON alarms(ship_id, alarm_time DESC);
CREATE INDEX IF NOT EXISTS idx_alarms_unack ON alarms(ship_id, is_acknowledged) WHERE is_acknowledged = false;

-- WebSocket连接表
CREATE TABLE IF NOT EXISTS ws_connections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id VARCHAR(100) NOT NULL UNIQUE,
    ship_id UUID REFERENCES ships(id),
    user_name VARCHAR(100),
    connected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_ping TIMESTAMP,
    is_active BOOLEAN DEFAULT true
);

-- 初始化船舶数据 - 宋代沙船
INSERT INTO ships (name, ship_type, length_overall, breadth_molded, depth_molded, 
                   design_draft, displacement, lightship_weight, deadweight_tons, 
                   metacentric_height_design)
VALUES 
('宋代沙船-001', '方艄沙船', 35.00, 9.50, 3.20, 2.10, 420.00, 180.00, 240.00, 0.85),
('宋代沙船-002', '方艄沙船', 38.00, 10.00, 3.50, 2.30, 520.00, 210.00, 310.00, 0.82)
ON CONFLICT DO NOTHING;

-- 初始化货舱数据
INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z)
SELECT s.id, 1, '前货舱', 85.00, 60.00, -10.50, 0.00, 1.20
FROM ships s WHERE s.name = '宋代沙船-001'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z)
SELECT s.id, 2, '中前货舱', 95.00, 70.00, -3.50, 0.00, 1.20
FROM ships s WHERE s.name = '宋代沙船-001'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z)
SELECT s.id, 3, '中后货舱', 95.00, 70.00, 3.50, 0.00, 1.20
FROM ships s WHERE s.name = '宋代沙船-001'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z)
SELECT s.id, 4, '后货舱', 85.00, 60.00, 10.50, 0.00, 1.20
FROM ships s WHERE s.name = '宋代沙船-001'
ON CONFLICT DO NOTHING;

-- 第二艘船货舱
INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z)
SELECT s.id, 1, '前货舱', 95.00, 70.00, -11.50, 0.00, 1.30
FROM ships s WHERE s.name = '宋代沙船-002'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z)
SELECT s.id, 2, '中前货舱', 105.00, 80.00, -3.80, 0.00, 1.30
FROM ships s WHERE s.name = '宋代沙船-002'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z)
SELECT s.id, 3, '中后货舱', 105.00, 80.00, 3.80, 0.00, 1.30
FROM ships s WHERE s.name = '宋代沙船-002'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z)
SELECT s.id, 4, '后货舱', 95.00, 70.00, 11.50, 0.00, 1.30
FROM ships s WHERE s.name = '宋代沙船-002'
ON CONFLICT DO NOTHING;

-- 第一艘船液舱
INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z,
                         is_tank, tank_length, tank_breadth, liquid_density, tank_fullness)
SELECT s.id, 5, '淡水舱', 12.00, 12.00, -8.00, 3.50, 0.50,
       true, 4.00, 3.00, 1.000, 0.6
FROM ships s WHERE s.name = '宋代沙船-001'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z,
                         is_tank, tank_length, tank_breadth, liquid_density, tank_fullness)
SELECT s.id, 6, '压载水舱-左', 18.00, 18.00, 0.00, -4.20, 0.30,
       true, 6.00, 3.00, 1.025, 0.4
FROM ships s WHERE s.name = '宋代沙船-001'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z,
                         is_tank, tank_length, tank_breadth, liquid_density, tank_fullness)
SELECT s.id, 7, '压载水舱-右', 18.00, 18.00, 0.00, 4.20, 0.30,
       true, 6.00, 3.00, 1.025, 0.4
FROM ships s WHERE s.name = '宋代沙船-001'
ON CONFLICT DO NOTHING;

-- 第二艘船液舱
INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z,
                         is_tank, tank_length, tank_breadth, liquid_density, tank_fullness)
SELECT s.id, 5, '淡水舱', 15.00, 15.00, -9.00, 3.80, 0.55,
       true, 4.50, 3.30, 1.000, 0.7
FROM ships s WHERE s.name = '宋代沙船-002'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z,
                         is_tank, tank_length, tank_breadth, liquid_density, tank_fullness)
SELECT s.id, 6, '压载水舱-左', 22.00, 22.00, 0.00, -4.50, 0.35,
       true, 6.50, 3.40, 1.025, 0.3
FROM ships s WHERE s.name = '宋代沙船-002'
ON CONFLICT DO NOTHING;

INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, 
                         center_gravity_x, center_gravity_y, center_gravity_z,
                         is_tank, tank_length, tank_breadth, liquid_density, tank_fullness)
SELECT s.id, 7, '压载水舱-右', 22.00, 22.00, 0.00, 4.50, 0.35,
       true, 6.50, 3.40, 1.025, 0.3
FROM ships s WHERE s.name = '宋代沙船-002'
ON CONFLICT DO NOTHING;

-- 初始化货物类型
INSERT INTO cargo_types (cargo_code, cargo_name, density, unit_weight, color_hex)
VALUES 
('GRAIN', '粮食', 0.750, 0.75, '#DEB887'),
('SALT', '海盐', 2.160, 2.16, '#F0F8FF'),
('TEA', '茶叶', 0.450, 0.45, '#228B22'),
('PORCELAIN', '瓷器', 1.800, 1.80, '#E6E6FA'),
('SILK', '丝绸', 0.350, 0.35, '#FFB6C1')
ON CONFLICT DO NOTHING;

-- 创建更新时间触发器函数
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_ships_updated_at ON ships;
CREATE TRIGGER update_ships_updated_at
    BEFORE UPDATE ON ships
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 稳性计算视图
CREATE OR REPLACE VIEW v_stability_status AS
SELECT 
    sr.id,
    sr.ship_id,
    s.name as ship_name,
    sr.calculation_time,
    sr.gm_value,
    sr.metacentric_height_transverse,
    sr.roll_period,
    sr.righting_moment,
    sr.stability_status,
    sr.warning_message,
    sd.roll_angle,
    sd.draft_mean,
    sd.bilge_water_level
FROM stability_results sr
JOIN ships s ON sr.ship_id = s.id
LEFT JOIN sensor_data sd ON sr.sensor_data_id = sd.id
WHERE sr.calculation_time >= NOW() - INTERVAL '24 hours';

-- 装载分布视图
CREATE OR REPLACE VIEW v_cargo_distribution AS
SELECT 
    cl.ship_id,
    s.name as ship_name,
    ch.hold_number,
    ch.hold_name,
    ct.cargo_name,
    ct.color_hex,
    cl.weight,
    cl.volume,
    cl.loading_time,
    cl.is_optimized
FROM cargo_loadings cl
JOIN ships s ON cl.ship_id = s.id
JOIN cargo_holds ch ON cl.hold_id = ch.id
JOIN cargo_types ct ON cl.cargo_type_id = ct.id
ORDER BY cl.ship_id, ch.hold_number;

-- ============================================================
-- Performance Indexes
-- ============================================================

-- sensor_data: time-range queries per ship
CREATE INDEX IF NOT EXISTS idx_sensor_data_time_range
    ON sensor_data(ship_id, timestamp DESC);

-- sensor_data: roll angle threshold scan
CREATE INDEX IF NOT EXISTS idx_sensor_data_roll
    ON sensor_data(ship_id, roll_angle)
    WHERE ABS(roll_angle) > 10.0;

-- stability_results: warning status filter
CREATE INDEX IF NOT EXISTS idx_stability_status
    ON stability_results(ship_id, stability_status)
    WHERE stability_status != 'NORMAL';

-- stability_results: GM threshold scan
CREATE INDEX IF NOT EXISTS idx_stability_gm
    ON stability_results(ship_id, gm_value)
    WHERE gm_value < 0.3;

-- alarms: severity + unacknowledged composite
CREATE INDEX IF NOT EXISTS idx_alarms_severity_ack
    ON alarms(ship_id, severity, acknowledged)
    WHERE acknowledged = false;

-- alarms: triggered_at range queries
CREATE INDEX IF NOT EXISTS idx_alarms_triggered
    ON alarms(ship_id, triggered_at DESC);

-- cargo_loadings: ship + optimized flag
CREATE INDEX IF NOT EXISTS idx_cargo_loadings_ship_opt
    ON cargo_loadings(ship_id, is_optimized);

-- cargo_loadings: hold_id lookup
CREATE INDEX IF NOT EXISTS idx_cargo_loadings_hold
    ON cargo_loadings(hold_id);

-- cargo_holds: ship + tank flag
CREATE INDEX IF NOT EXISTS idx_cargo_holds_ship_tank
    ON cargo_holds(ship_id, is_tank)
    WHERE is_tank = true;

-- loading_optimizations: ship latest
CREATE INDEX IF NOT EXISTS idx_loading_opt_ship_time
    ON loading_optimizations(ship_id, optimization_time DESC);

-- loading_optimizations: algorithm used stats
CREATE INDEX IF NOT EXISTS idx_loading_opt_algorithm
    ON loading_optimizations(algorithm_used);

-- ============================================================
-- 新增：扩展船型数据（福船、广船、现代散货船）
-- 完全向后兼容，不修改原有数据
-- ============================================================

-- 船型分类枚举（用于对比功能）
CREATE TYPE IF NOT EXISTS ship_category AS ENUM ('ANCIENT', 'MODERN');
CREATE TYPE IF NOT EXISTS ship_family AS ENUM ('沙船', '福船', '广船', '散货船');

-- 为ships表添加扩展字段（可选，用于新功能）
ALTER TABLE ships ADD COLUMN IF NOT EXISTS ship_category ship_category;
ALTER TABLE ships ADD COLUMN IF NOT EXISTS ship_family ship_family;
ALTER TABLE ships ADD COLUMN IF NOT EXISTS ship_variant VARCHAR(100);
ALTER TABLE ships ADD COLUMN IF NOT EXISTS block_coefficient NUMERIC(10, 4);
ALTER TABLE ships ADD COLUMN IF NOT EXISTS roll_radius_coefficient NUMERIC(10, 4);
ALTER TABLE ships ADD COLUMN IF NOT EXISTS bow_height NUMERIC(10, 2);
ALTER TABLE ships ADD COLUMN IF NOT EXISTS stern_height NUMERIC(10, 2);
ALTER TABLE ships ADD COLUMN IF NOT EXISTS watertight_bulkheads INTEGER;
ALTER TABLE ships ADD COLUMN IF NOT EXISTS historical_period VARCHAR(50);
ALTER TABLE ships ADD COLUMN IF NOT EXISTS spectrum_key VARCHAR(50);

-- 更新现有沙船数据的分类字段
UPDATE ships SET 
    ship_category = 'ANCIENT',
    ship_family = '沙船',
    ship_variant = CASE 
        WHEN name = '宋代沙船-001' THEN '中型方艄'
        WHEN name = '宋代沙船-002' THEN '大型方艄'
        ELSE ship_variant
    END,
    spectrum_key = CASE 
        WHEN name = '宋代沙船-001' THEN 'fangshao-medium'
        WHEN name = '宋代沙船-002' THEN 'fangshao-large'
        ELSE spectrum_key
    END,
    block_coefficient = CASE 
        WHEN name = '宋代沙船-001' THEN 0.66
        WHEN name = '宋代沙船-002' THEN 0.65
        ELSE block_coefficient
    END,
    roll_radius_coefficient = 0.35,
    historical_period = '宋代'
WHERE ship_type = '方艄沙船' AND ship_category IS NULL;

-- 初始化新增古代船型：福船（明代福建尖底海船）
-- 文献考证：泉州宋代沉船Cb≈0.44，福船尖底折减系数0.85
INSERT INTO ships (name, ship_type, ship_category, ship_family, ship_variant, spectrum_key,
                   length_overall, breadth_molded, depth_molded, design_draft, 
                   displacement, lightship_weight, deadweight_tons, 
                   metacentric_height_design, block_coefficient, roll_radius_coefficient,
                   bow_height, stern_height, watertight_bulkheads, historical_period)
VALUES 
('明代福船-001', '福船', 'ANCIENT', '福船', '小型福船', 'fuchuan-small',
 33.00, 7.50, 4.00, 2.80, 415.00, 185.00, 230.00, 1.10, 0.48, 0.38, 5.20, 4.80, 12, '明代'),
('明代福船-002', '福船', 'ANCIENT', '福船', '中型福船', 'fuchuan-medium',
 42.00, 9.00, 5.00, 3.50, 760.00, 340.00, 420.00, 1.15, 0.46, 0.39, 6.50, 5.80, 14, '明代'),
('明代宝船-001', '福船', 'ANCIENT', '福船', '大型福船（宝船级）', 'fuchuan-large',
 60.00, 13.00, 6.50, 4.80, 1600.00, 700.00, 900.00, 0.65, 0.44, 0.40, 8.50, 7.50, 16, '明代郑和下西洋')
ON CONFLICT DO NOTHING;

-- 初始化新增古代船型：广船（明代广东尖底海船）
-- 文献：广船Cb=0.55-0.62，介于沙船和福船之间
INSERT INTO ships (name, ship_type, ship_category, ship_family, ship_variant, spectrum_key,
                   length_overall, breadth_molded, depth_molded, design_draft, 
                   displacement, lightship_weight, deadweight_tons, 
                   metacentric_height_design, block_coefficient, roll_radius_coefficient,
                   bow_height, stern_height, watertight_bulkheads, historical_period)
VALUES 
('明代广船-001', '广船', 'ANCIENT', '广船', '小型广船', 'guangchuan-small',
 28.00, 6.50, 3.80, 2.60, 370.00, 160.00, 210.00, 1.05, 0.58, 0.37, 4.50, 4.20, 10, '明代'),
('明代广船-002', '广船', 'ANCIENT', '广船', '中型广船', 'guangchuan-medium',
 36.00, 8.00, 4.60, 3.20, 680.00, 290.00, 390.00, 1.08, 0.56, 0.38, 5.80, 5.20, 12, '明代')
ON CONFLICT DO NOTHING;

-- 初始化现代船型：散货船
-- 标准：IMO IS Code 2008, IACS UR S1/S25, Lloyd's Register典型尺度
INSERT INTO ships (name, ship_type, ship_category, ship_family, ship_variant, spectrum_key,
                   length_overall, breadth_molded, depth_molded, design_draft, 
                   displacement, lightship_weight, deadweight_tons, 
                   metacentric_height_design, block_coefficient, roll_radius_coefficient,
                   bow_height, stern_height, watertight_bulkheads, historical_period)
VALUES 
('现代散货船-Handysize', '散货船', 'MODERN', '散货船', 'Handysize (35000DWT)', 'modern-bulk-handysize',
 169.00, 27.20, 14.20, 9.50, 46500.00, 11500.00, 35000.00, 1.00, 0.82, 0.40, 16.00, 14.00, 7, '现代'),
('现代散货船-Panamax', '散货船', 'MODERN', '散货船', 'Panamax (75000DWT)', 'modern-bulk-panamax',
 225.00, 32.20, 19.20, 12.50, 97000.00, 22000.00, 75000.00, 0.90, 0.83, 0.40, 22.00, 19.00, 9, '现代'),
('现代散货船-Capesize', '散货船', 'MODERN', '散货船', 'Capesize (180000DWT)', 'modern-bulk-capesize',
 292.00, 45.00, 24.80, 16.50, 230000.00, 50000.00, 180000.00, 0.80, 0.84, 0.40, 28.00, 24.00, 11, '现代')
ON CONFLICT DO NOTHING;

-- 为福船初始化货舱（小型福船，6个货舱）
DO $$
DECLARE
    ship_id UUID;
BEGIN
    SELECT id INTO ship_id FROM ships WHERE name = '明代福船-001';
    IF ship_id IS NOT NULL THEN
        -- 货舱
        INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, center_gravity_x, center_gravity_y, center_gravity_z) VALUES
        (ship_id, 1, '第一货舱', 75.00, 55.00, -12.00, 0.00, 1.80),
        (ship_id, 2, '第二货舱', 80.00, 60.00, -6.00, 0.00, 1.80),
        (ship_id, 3, '第三货舱', 85.00, 65.00, 0.00, 0.00, 1.80),
        (ship_id, 4, '第四货舱', 85.00, 65.00, 6.00, 0.00, 1.80),
        (ship_id, 5, '第五货舱', 80.00, 60.00, 12.00, 0.00, 1.80),
        (ship_id, 6, '第六货舱', 75.00, 55.00, 12.00, 0.00, 1.80)
        ON CONFLICT DO NOTHING;
        
        -- 液舱
        INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, center_gravity_x, center_gravity_y, center_gravity_z, is_tank, tank_length, tank_breadth, liquid_density, tank_fullness) VALUES
        (ship_id, 7, '淡水舱', 15.00, 15.00, -9.00, 3.00, 0.60, true, 4.00, 3.00, 1.000, 0.6),
        (ship_id, 8, '压载水舱-左', 20.00, 20.00, 0.00, -3.50, 0.40, true, 6.00, 3.00, 1.025, 0.4),
        (ship_id, 9, '压载水舱-右', 20.00, 20.00, 0.00, 3.50, 0.40, true, 6.00, 3.00, 1.025, 0.4)
        ON CONFLICT DO NOTHING;
    END IF;
END $$;

-- 为福船初始化货舱（中型福船）
DO $$
DECLARE
    ship_id UUID;
BEGIN
    SELECT id INTO ship_id FROM ships WHERE name = '明代福船-002';
    IF ship_id IS NOT NULL THEN
        INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, center_gravity_x, center_gravity_y, center_gravity_z) VALUES
        (ship_id, 1, '第一货舱', 100.00, 75.00, -15.00, 0.00, 2.20),
        (ship_id, 2, '第二货舱', 110.00, 85.00, -7.50, 0.00, 2.20),
        (ship_id, 3, '第三货舱', 120.00, 95.00, 0.00, 0.00, 2.20),
        (ship_id, 4, '第四货舱', 120.00, 95.00, 7.50, 0.00, 2.20),
        (ship_id, 5, '第五货舱', 110.00, 85.00, 15.00, 0.00, 2.20),
        (ship_id, 6, '第六货舱', 100.00, 75.00, 15.00, 0.00, 2.20)
        ON CONFLICT DO NOTHING;
        
        INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, center_gravity_x, center_gravity_y, center_gravity_z, is_tank, tank_length, tank_breadth, liquid_density, tank_fullness) VALUES
        (ship_id, 7, '淡水舱', 20.00, 20.00, -12.00, 3.50, 0.80, true, 5.00, 3.50, 1.000, 0.6),
        (ship_id, 8, '压载水舱-左', 25.00, 25.00, 0.00, -4.00, 0.50, true, 7.00, 3.50, 1.025, 0.4),
        (ship_id, 9, '压载水舱-右', 25.00, 25.00, 0.00, 4.00, 0.50, true, 7.00, 3.50, 1.025, 0.4)
        ON CONFLICT DO NOTHING;
    END IF;
END $$;

-- 为福船（宝船级）初始化货舱
DO $$
DECLARE
    ship_id UUID;
BEGIN
    SELECT id INTO ship_id FROM ships WHERE name = '明代宝船-001';
    IF ship_id IS NOT NULL THEN
        INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, center_gravity_x, center_gravity_y, center_gravity_z) VALUES
        (ship_id, 1, '第一货舱', 300.00, 220.00, -22.00, 0.00, 3.00),
        (ship_id, 2, '第二货舱', 350.00, 260.00, -11.00, 0.00, 3.00),
        (ship_id, 3, '第三货舱', 400.00, 300.00, 0.00, 0.00, 3.00),
        (ship_id, 4, '第四货舱', 400.00, 300.00, 11.00, 0.00, 3.00),
        (ship_id, 5, '第五货舱', 350.00, 260.00, 22.00, 0.00, 3.00),
        (ship_id, 6, '第六货舱', 300.00, 220.00, 22.00, 0.00, 3.00)
        ON CONFLICT DO NOTHING;
        
        INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, center_gravity_x, center_gravity_y, center_gravity_z, is_tank, tank_length, tank_breadth, liquid_density, tank_fullness) VALUES
        (ship_id, 7, '淡水舱', 50.00, 50.00, -18.00, 5.00, 1.20, true, 8.00, 5.00, 1.000, 0.6),
        (ship_id, 8, '压载水舱-左', 80.00, 80.00, 0.00, -6.00, 0.80, true, 12.00, 6.00, 1.025, 0.4),
        (ship_id, 9, '压载水舱-右', 80.00, 80.00, 0.00, 6.00, 0.80, true, 12.00, 6.00, 1.025, 0.4)
        ON CONFLICT DO NOTHING;
    END IF;
END $$;

-- 为广船初始化货舱（小型广船，5个货舱）
DO $$
DECLARE
    ship_id UUID;
BEGIN
    SELECT id INTO ship_id FROM ships WHERE name = '明代广船-001';
    IF ship_id IS NOT NULL THEN
        INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, center_gravity_x, center_gravity_y, center_gravity_z) VALUES
        (ship_id, 1, '第一货舱', 60.00, 45.00, -10.00, 0.00, 1.60),
        (ship_id, 2, '第二货舱', 65.00, 50.00, -3.33, 0.00, 1.60),
        (ship_id, 3, '第三货舱', 70.00, 55.00, 3.33, 0.00, 1.60),
        (ship_id, 4, '第四货舱', 65.00, 50.00, 10.00, 0.00, 1.60),
        (ship_id, 5, '第五货舱', 60.00, 45.00, 10.00, 0.00, 1.60)
        ON CONFLICT DO NOTHING;
        
        INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, center_gravity_x, center_gravity_y, center_gravity_z, is_tank, tank_length, tank_breadth, liquid_density, tank_fullness) VALUES
        (ship_id, 6, '淡水舱', 10.00, 10.00, -7.00, 2.80, 0.50, true, 3.50, 2.80, 1.000, 0.6),
        (ship_id, 7, '压载水舱-左', 15.00, 15.00, 0.00, -3.00, 0.35, true, 5.00, 2.80, 1.025, 0.4),
        (ship_id, 8, '压载水舱-右', 15.00, 15.00, 0.00, 3.00, 0.35, true, 5.00, 2.80, 1.025, 0.4)
        ON CONFLICT DO NOTHING;
    END IF;
END $$;

-- 为广船初始化货舱（中型广船）
DO $$
DECLARE
    ship_id UUID;
BEGIN
    SELECT id INTO ship_id FROM ships WHERE name = '明代广船-002';
    IF ship_id IS NOT NULL THEN
        INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, center_gravity_x, center_gravity_y, center_gravity_z) VALUES
        (ship_id, 1, '第一货舱', 85.00, 65.00, -13.00, 0.00, 2.00),
        (ship_id, 2, '第二货舱', 95.00, 75.00, -4.33, 0.00, 2.00),
        (ship_id, 3, '第三货舱', 100.00, 80.00, 4.33, 0.00, 2.00),
        (ship_id, 4, '第四货舱', 95.00, 75.00, 13.00, 0.00, 2.00),
        (ship_id, 5, '第五货舱', 85.00, 65.00, 13.00, 0.00, 2.00)
        ON CONFLICT DO NOTHING;
        
        INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, center_gravity_x, center_gravity_y, center_gravity_z, is_tank, tank_length, tank_breadth, liquid_density, tank_fullness) VALUES
        (ship_id, 6, '淡水舱', 15.00, 15.00, -9.00, 3.20, 0.65, true, 4.00, 3.20, 1.000, 0.6),
        (ship_id, 7, '压载水舱-左', 20.00, 20.00, 0.00, -3.60, 0.45, true, 6.00, 3.20, 1.025, 0.4),
        (ship_id, 8, '压载水舱-右', 20.00, 20.00, 0.00, 3.60, 0.45, true, 6.00, 3.20, 1.025, 0.4)
        ON CONFLICT DO NOTHING;
    END IF;
END $$;

-- 为现代散货船初始化货舱（Handysize型，9个货舱）
DO $$
DECLARE
    ship_id UUID;
BEGIN
    SELECT id INTO ship_id FROM ships WHERE name = '现代散货船-Handysize';
    IF ship_id IS NOT NULL THEN
        -- 9个货舱，现代散货船标准布局
        INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, center_gravity_x, center_gravity_y, center_gravity_z) VALUES
        (ship_id, 1, 'NO.1货舱', 3000.00, 2500.00, -65.00, 0.00, 6.00),
        (ship_id, 2, 'NO.2货舱', 3500.00, 3000.00, -45.00, 0.00, 6.00),
        (ship_id, 3, 'NO.3货舱', 4000.00, 3500.00, -25.00, 0.00, 6.00),
        (ship_id, 4, 'NO.4货舱', 4200.00, 3700.00, -5.00, 0.00, 6.00),
        (ship_id, 5, 'NO.5货舱', 4200.00, 3700.00, 15.00, 0.00, 6.00),
        (ship_id, 6, 'NO.6货舱', 4000.00, 3500.00, 35.00, 0.00, 6.00),
        (ship_id, 7, 'NO.7货舱', 3500.00, 3000.00, 55.00, 0.00, 6.00),
        (ship_id, 8, 'NO.8货舱', 3000.00, 2500.00, 75.00, 0.00, 6.00),
        (ship_id, 9, 'NO.9货舱', 2500.00, 2000.00, 75.00, 0.00, 6.00)
        ON CONFLICT DO NOTHING;
        
        -- 现代船舶液舱系统
        INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, center_gravity_x, center_gravity_y, center_gravity_z, is_tank, tank_length, tank_breadth, liquid_density, tank_fullness) VALUES
        (ship_id, 10, '淡水舱', 200.00, 200.00, -50.00, 12.00, 2.00, true, 15.00, 8.00, 1.000, 0.7),
        (ship_id, 11, '压载水舱-艏', 500.00, 500.00, -80.00, 0.00, 1.50, true, 20.00, 12.00, 1.025, 0.3),
        (ship_id, 12, '压载水舱-艉', 500.00, 500.00, 80.00, 0.00, 1.50, true, 20.00, 12.00, 1.025, 0.3),
        (ship_id, 13, '压载水舱-舷侧左', 800.00, 800.00, 0.00, -14.00, 2.00, true, 30.00, 3.00, 1.025, 0.3),
        (ship_id, 14, '压载水舱-舷侧右', 800.00, 800.00, 0.00, 14.00, 2.00, true, 30.00, 3.00, 1.025, 0.3),
        (ship_id, 15, '燃油舱', 300.00, 280.00, -15.00, 12.00, 3.00, true, 12.00, 6.00, 0.95, 0.6)
        ON CONFLICT DO NOTHING;
    END IF;
END $$;

-- 为现代散货船Panamax型初始化货舱
DO $$
DECLARE
    ship_id UUID;
BEGIN
    SELECT id INTO ship_id FROM ships WHERE name = '现代散货船-Panamax';
    IF ship_id IS NOT NULL THEN
        INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, center_gravity_x, center_gravity_y, center_gravity_z) VALUES
        (ship_id, 1, 'NO.1货舱', 4500.00, 3800.00, -80.00, 0.00, 7.50),
        (ship_id, 2, 'NO.2货舱', 5200.00, 4500.00, -55.00, 0.00, 7.50),
        (ship_id, 3, 'NO.3货舱', 5800.00, 5000.00, -30.00, 0.00, 7.50),
        (ship_id, 4, 'NO.4货舱', 6200.00, 5400.00, -5.00, 0.00, 7.50),
        (ship_id, 5, 'NO.5货舱', 6200.00, 5400.00, 20.00, 0.00, 7.50),
        (ship_id, 6, 'NO.6货舱', 5800.00, 5000.00, 45.00, 0.00, 7.50),
        (ship_id, 7, 'NO.7货舱', 5200.00, 4500.00, 70.00, 0.00, 7.50),
        (ship_id, 8, 'NO.8货舱', 4500.00, 3800.00, 95.00, 0.00, 7.50),
        (ship_id, 9, 'NO.9货舱', 4000.00, 3300.00, 95.00, 0.00, 7.50)
        ON CONFLICT DO NOTHING;
    END IF;
END $$;

-- 为现代散货船Capesize型初始化货舱
DO $$
DECLARE
    ship_id UUID;
BEGIN
    SELECT id INTO ship_id FROM ships WHERE name = '现代散货船-Capesize';
    IF ship_id IS NOT NULL THEN
        INSERT INTO cargo_holds (ship_id, hold_number, hold_name, capacity_cubic, max_weight, center_gravity_x, center_gravity_y, center_gravity_z) VALUES
        (ship_id, 1, 'NO.1货舱', 8000.00, 7000.00, -105.00, 0.00, 10.00),
        (ship_id, 2, 'NO.2货舱', 9500.00, 8500.00, -70.00, 0.00, 10.00),
        (ship_id, 3, 'NO.3货舱', 10500.00, 9500.00, -35.00, 0.00, 10.00),
        (ship_id, 4, 'NO.4货舱', 11000.00, 10000.00, 0.00, 0.00, 10.00),
        (ship_id, 5, 'NO.5货舱', 11000.00, 10000.00, 35.00, 0.00, 10.00),
        (ship_id, 6, 'NO.6货舱', 10500.00, 9500.00, 70.00, 0.00, 10.00),
        (ship_id, 7, 'NO.7货舱', 9500.00, 8500.00, 105.00, 0.00, 10.00),
        (ship_id, 8, 'NO.8货舱', 8000.00, 7000.00, 140.00, 0.00, 10.00),
        (ship_id, 9, 'NO.9货舱', 7000.00, 6000.00, 140.00, 0.00, 10.00)
        ON CONFLICT DO NOTHING;
    END IF;
END $$;

-- ============================================================
-- 新增：风暴模拟结果表（用于存储风暴倾覆模拟结果）
-- ============================================================
CREATE TABLE IF NOT EXISTS storm_simulations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ship_id UUID NOT NULL REFERENCES ships(id) ON DELETE CASCADE,
    simulation_name VARCHAR(200),
    storm_severity VARCHAR(50) NOT NULL,  -- TROPICAL_STORM, SEVERE_STORM, TYPHOON, HURRICANE
    wave_height NUMERIC(8, 2) NOT NULL,
    wind_speed NUMERIC(8, 2) NOT NULL,
    wave_period NUMERIC(8, 2) NOT NULL,
    simulation_duration_hours NUMERIC(8, 2) NOT NULL DEFAULT 24.0,
    monte_carlo_iterations INTEGER NOT NULL DEFAULT 10000,
    capsizing_probability NUMERIC(8, 6),  -- 倾覆概率 0~1
    max_roll_angle_experienced NUMERIC(8, 3),
    min_gm_experienced NUMERIC(8, 4),
    righting_arm_loss_percentage NUMERIC(8, 4),  -- 稳性臂损失百分比
    weather_helm_effect NUMERIC(8, 3),
    broaching_probability NUMERIC(8, 6),  -- 横甩概率
    parametric_roll_risk BOOLEAN DEFAULT false,  -- 参数横摇风险
    simulation_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    result_details JSONB,
    simulation_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_storm_sim_ship_time ON storm_simulations(ship_id, simulation_time DESC);
CREATE INDEX IF NOT EXISTS idx_storm_sim_capsize ON storm_simulations(ship_id, capsizing_probability DESC);

-- ============================================================
-- 新增：船型对比结果表（存储多船稳性对比分析结果）
-- ============================================================
CREATE TABLE IF NOT EXISTS ship_comparisons (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    comparison_name VARCHAR(200),
    ship_ids UUID[] NOT NULL,
    comparison_criteria VARCHAR(100)[] NOT NULL,  -- GM, GZ_MAX, ROLL_PERIOD, RANGE, etc.
    loading_condition VARCHAR(50) NOT NULL DEFAULT 'FULL_LOAD',  -- BALLAST, HALF_LOAD, FULL_LOAD
    reference_wave_height NUMERIC(8, 2) DEFAULT 3.0,
    results JSONB,
    ranking_summary TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ship_comparison_time ON ship_comparisons(created_at DESC);

-- ============================================================
-- 新增：虚拟装载会话表（存储用户交互式装载体验会话）
-- ============================================================
CREATE TABLE IF NOT EXISTS virtual_loading_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ship_id UUID NOT NULL REFERENCES ships(id) ON DELETE CASCADE,
    session_name VARCHAR(200),
    user_id VARCHAR(100),
    is_public BOOLEAN DEFAULT true,
    loading_config JSONB NOT NULL,  -- {hold_id: {cargo_type_id: weight}}
    current_gm NUMERIC(8, 4),
    stability_status VARCHAR(20),
    total_cargo_weight NUMERIC(12, 2),
    total_cargo_volume NUMERIC(12, 2),
    steps_taken INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_activity TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_virtual_loading_ship ON virtual_loading_sessions(ship_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_virtual_loading_public ON virtual_loading_sessions(is_public) WHERE is_public = true;
