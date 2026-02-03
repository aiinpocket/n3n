-- =====================================================
-- Add MongoDB Credential Type
-- =====================================================

INSERT INTO credential_types (name, display_name, description, icon, fields_schema)
VALUES (
    'mongodb',
    'MongoDB',
    'MongoDB database connection credentials',
    'database',
    '{
        "type": "object",
        "properties": {
            "connectionString": {
                "type": "string",
                "title": "Connection String",
                "description": "MongoDB connection string (mongodb:// or mongodb+srv://)"
            },
            "host": {
                "type": "string",
                "title": "Host",
                "default": "localhost"
            },
            "port": {
                "type": "string",
                "title": "Port",
                "default": "27017"
            },
            "database": {
                "type": "string",
                "title": "Database"
            },
            "username": {
                "type": "string",
                "title": "Username"
            },
            "password": {
                "type": "string",
                "format": "password",
                "title": "Password"
            },
            "authSource": {
                "type": "string",
                "title": "Auth Source",
                "default": "admin"
            },
            "replicaSet": {
                "type": "string",
                "title": "Replica Set"
            },
            "tls": {
                "type": "boolean",
                "title": "Use TLS",
                "default": false
            }
        },
        "required": ["database"]
    }'
)
ON CONFLICT (name) DO NOTHING;

-- Add Redis credential type as well for completeness
INSERT INTO credential_types (name, display_name, description, icon, fields_schema)
VALUES (
    'redis',
    'Redis',
    'Redis cache connection credentials',
    'database',
    '{
        "type": "object",
        "properties": {
            "host": {
                "type": "string",
                "title": "Host",
                "default": "localhost"
            },
            "port": {
                "type": "integer",
                "title": "Port",
                "default": 6379
            },
            "password": {
                "type": "string",
                "format": "password",
                "title": "Password"
            },
            "database": {
                "type": "integer",
                "title": "Database",
                "default": 0
            },
            "tls": {
                "type": "boolean",
                "title": "Use TLS",
                "default": false
            }
        }
    }'
)
ON CONFLICT (name) DO NOTHING;

-- Add PostgreSQL credential type
INSERT INTO credential_types (name, display_name, description, icon, fields_schema)
VALUES (
    'postgres',
    'PostgreSQL',
    'PostgreSQL database connection credentials',
    'database',
    '{
        "type": "object",
        "properties": {
            "host": {
                "type": "string",
                "title": "Host",
                "default": "localhost"
            },
            "port": {
                "type": "integer",
                "title": "Port",
                "default": 5432
            },
            "database": {
                "type": "string",
                "title": "Database"
            },
            "username": {
                "type": "string",
                "title": "Username"
            },
            "password": {
                "type": "string",
                "format": "password",
                "title": "Password"
            },
            "ssl": {
                "type": "boolean",
                "title": "Use SSL",
                "default": false
            }
        },
        "required": ["host", "database", "username", "password"]
    }'
)
ON CONFLICT (name) DO NOTHING;

-- Add MySQL credential type
INSERT INTO credential_types (name, display_name, description, icon, fields_schema)
VALUES (
    'mysql',
    'MySQL',
    'MySQL database connection credentials',
    'database',
    '{
        "type": "object",
        "properties": {
            "host": {
                "type": "string",
                "title": "Host",
                "default": "localhost"
            },
            "port": {
                "type": "integer",
                "title": "Port",
                "default": 3306
            },
            "database": {
                "type": "string",
                "title": "Database"
            },
            "username": {
                "type": "string",
                "title": "Username"
            },
            "password": {
                "type": "string",
                "format": "password",
                "title": "Password"
            },
            "ssl": {
                "type": "boolean",
                "title": "Use SSL",
                "default": false
            }
        },
        "required": ["host", "database", "username", "password"]
    }'
)
ON CONFLICT (name) DO NOTHING;
