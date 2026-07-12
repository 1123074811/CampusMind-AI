# CampusMind MySQL

## Files

- `init/001_schema.sql`: Docker Compose initialization script. It is mounted into `/docker-entrypoint-initdb.d` and runs automatically when the MySQL volume is created for the first time.
- `init/002_admin_seed.sql`: Minimal development accounts and internal sources. It never assigns primary keys or inserts demo business records.
- `init/003_public_sources.sql`: Official public web source seed data for Xinjiang University and Xinjiang University Software College.
- `init/008_enterprise_constraints.sql`: Idempotently adds cross-table foreign keys after every table exists.
- `campusmind_schema.sql`: Standalone deployable schema export. Use this file when importing into an existing MySQL instance.
- `migrations/002_enterprise_persistence.sql`: One-time upgrade for databases created before 2026-07-12. It separates read/favorite timestamps, archives orphan states, creates subscriptions, and adds missing foreign keys.

## Default Database

- Database: `campusmind`
- User: `campusmind`
- Password: `campusmind`

## Import

```powershell
cmd /c "mysql --default-character-set=utf8mb4 -uroot -p < infra\mysql\campusmind_schema.sql"
cmd /c "mysql --default-character-set=utf8mb4 -uroot -p campusmind < infra\mysql\init\003_public_sources.sql"
```

Existing database upgrade (back up first, run exactly once):

```powershell
cmd /c "mysql --default-character-set=utf8mb4 -uroot -p campusmind < infra\mysql\migrations\002_enterprise_persistence.sql"
```

Historical orphan states are preserved in `user_information_state_orphan_20260712`. Existing high auto-increment values are intentionally not renumbered because primary keys are opaque identifiers; clean databases no longer jump to the 9000 range.

Docker Compose:

```powershell
docker compose -f infra/docker-compose.yml up -d mysql
```

If Docker Hub is slow, first try the mirror image configured in `.env.example`:

```powershell
$env:MYSQL_IMAGE="docker.1ms.run/mysql:8.4"
docker compose -f infra/docker-compose.yml up -d mysql
```

Other mirrors may be used by changing `MYSQL_IMAGE`, for example:

```powershell
$env:MYSQL_IMAGE="docker.m.daocloud.io/mysql:8.4"
docker compose -f infra/docker-compose.yml up -d mysql
```

Alibaba Cloud and Tsinghua are usually configured as Docker registry mirrors rather than stable direct image prefixes. If a direct pull path is unavailable, configure Docker Desktop registry mirrors and keep `MYSQL_IMAGE=mysql:8.4`.
