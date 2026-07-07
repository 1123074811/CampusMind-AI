# CampusMind MySQL

## Files

- `init/001_schema.sql`: Docker Compose initialization script. It is mounted into `/docker-entrypoint-initdb.d` and runs automatically when the MySQL volume is created for the first time.
- `init/003_public_sources.sql`: Official public web source seed data for Xinjiang University and Xinjiang University Software College.
- `campusmind_schema.sql`: Standalone deployable schema export. Use this file when importing into an existing MySQL instance.

## Default Database

- Database: `campusmind`
- User: `campusmind`
- Password: `ojc132598.`

## Import

```powershell
cmd /c "mysql --default-character-set=utf8mb4 -uroot -p < infra\mysql\campusmind_schema.sql"
cmd /c "mysql --default-character-set=utf8mb4 -uroot -p campusmind < infra\mysql\init\003_public_sources.sql"
```

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
