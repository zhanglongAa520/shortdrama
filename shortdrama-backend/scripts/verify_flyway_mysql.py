"""Verify Flyway against isolated empty and legacy MySQL databases."""
from __future__ import annotations

import os
from pathlib import Path
import selectors
import subprocess
import tempfile
import time
import uuid

BACKEND = Path(__file__).resolve().parents[1]
MIGRATIONS = BACKEND / "src/main/resources/db/migration/mysql"


def load_env() -> dict[str, str]:
    from local_config import mysql_config
    return mysql_config()


class MysqlClient:
    def __init__(self, config: dict[str, str]) -> None:
        self.config = config
        self.temp = tempfile.NamedTemporaryFile(
            mode="w", prefix="yingke-flyway-", suffix=".cnf", delete=False
        )
        os.chmod(self.temp.name, 0o600)
        self.temp.write("[client]\n")
        for key, default, option in (
            ("MYSQL_HOST", "127.0.0.1", "host"),
            ("MYSQL_PORT", "3306", "port"),
            ("MYSQL_USER", "root", "user"),
            ("MYSQL_PASSWORD", "", "password"),
        ):
            value = config.get(key, default).replace("\\", "\\\\").replace('"', '\\"')
            self.temp.write(f'{option}="{value}"\n')
        self.temp.close()

    def close(self) -> None:
        Path(self.temp.name).unlink(missing_ok=True)

    def execute(self, sql: str, database: str | None = None) -> str:
        command = [
            "mysql",
            f"--defaults-extra-file={self.temp.name}",
            "--batch",
            "--skip-column-names",
            "--default-character-set=utf8mb4",
        ]
        if database:
            command.append(database)
        try:
            result = subprocess.run(
                command, input=sql, text=True, capture_output=True, check=True
            )
        except subprocess.CalledProcessError as exception:
            message = exception.stderr.strip() or "MySQL 命令执行失败"
            raise RuntimeError(message) from exception
        return result.stdout.strip()


def start_once(config: dict[str, str], database: str, asset_root: Path) -> None:
    jar = BACKEND / "target/shortdrama-backend-0.1.0.jar"
    environment = os.environ.copy()
    environment.update(config)
    environment.update(
        {
            "SPRING_PROFILES_ACTIVE": "mysql",
            "MYSQL_DATABASE": database,
            "SPRING_DATASOURCE_URL": (
                f"jdbc:mysql://{config['MYSQL_HOST']}:{config['MYSQL_PORT']}/{database}"
                "?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8"
                "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
            ),
            "SPRING_DATASOURCE_USERNAME": config["MYSQL_USER"],
            "SPRING_DATASOURCE_PASSWORD": config["MYSQL_PASSWORD"],
            "APP_ADMIN_PASSWORD": "",
            "APP_ASSET_ROOT": str(asset_root),
            "APP_SOCIAL_CACHE_ENABLED": "false",
            "YINGKE_ADMIN_PASSWORD": "",
            "YINGKE_ASSET_ROOT": str(asset_root),
        }
    )
    process = subprocess.Popen(
        [
            "java",
            "-jar",
            str(jar),
            "--server.port=0",
        ],
        cwd=BACKEND,
        env=environment,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    output: list[str] = []
    selector = selectors.DefaultSelector()
    assert process.stdout is not None
    selector.register(process.stdout, selectors.EVENT_READ)
    deadline = time.monotonic() + 60
    started = False
    try:
        while time.monotonic() < deadline:
            if process.poll() is not None:
                output.extend(process.stdout.read().splitlines())
                break
            for key, _ in selector.select(timeout=1):
                line = key.fileobj.readline()
                if line:
                    output.append(line.rstrip())
                    if "Started DramaApplication" in line:
                        started = True
                        break
            if started:
                break
    finally:
        selector.close()
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
    if not started:
        tail = "\n".join(output[-80:])
        raise RuntimeError(f"{database} 启动迁移失败：\n{tail}")


def main() -> None:
    config = load_env()
    suffix = uuid.uuid4().hex[:10]
    empty_database = f"yingke_flyway_empty_{suffix}"
    legacy_database = f"yingke_flyway_legacy_{suffix}"
    generated = (empty_database, legacy_database)
    mysql = MysqlClient(config)
    try:
        for database in generated:
            mysql.execute(
                f"CREATE DATABASE {database} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
            )

        mysql.execute((MIGRATIONS / "V1__legacy_schema.sql").read_text(encoding="utf-8"),
                      legacy_database)
        mysql.execute(
            """
            INSERT INTO drama(title, description, genre, cover_url, status)
            VALUES ('FLYWAY_SENTINEL', 'must survive baseline', 'test', '/sentinel', 'DRAFT');
            """,
            legacy_database,
        )

        subprocess.run(
            ["mvn", "-q", "-DskipTests", "package"],
            cwd=BACKEND,
            check=True,
        )

        with tempfile.TemporaryDirectory(prefix="yingke-flyway-assets-") as asset_directory:
            asset_root = Path(asset_directory)
            for database in generated:
                start_once(config, database, asset_root)
                start_once(config, database, asset_root)

        empty_versions = mysql.execute(
            "SELECT GROUP_CONCAT(version ORDER BY installed_rank) "
            "FROM flyway_schema_history WHERE success = 1",
            empty_database,
        )
        legacy_versions = mysql.execute(
            "SELECT GROUP_CONCAT(version ORDER BY installed_rank) "
            "FROM flyway_schema_history WHERE success = 1",
            legacy_database,
        )
        if empty_versions != "1,2,3,4,5,6,7,8,9,10":
            raise RuntimeError(f"空库迁移版本异常：{empty_versions}")
        if legacy_versions != "1,2,3,4,5,6,7,8,9,10":
            raise RuntimeError(f"旧库迁移版本异常：{legacy_versions}")
        sentinel = mysql.execute(
            "SELECT COUNT(*) FROM drama WHERE title = 'FLYWAY_SENTINEL'",
            legacy_database,
        )
        if sentinel != "1":
            raise RuntimeError("旧库哨兵数据未保留")
        comment = mysql.execute(
            "SELECT table_comment FROM information_schema.tables "
            "WHERE table_schema = DATABASE() AND table_name = 'drama'",
            legacy_database,
        )
        if comment != "短剧信息表":
            raise RuntimeError(f"旧库 V2 中文注释未生效：{comment}")
        integration_table = mysql.execute(
            "SELECT COUNT(*) FROM information_schema.tables "
            "WHERE table_schema = DATABASE() AND table_name = 'integration_config'",
            legacy_database,
        )
        if integration_table != "1":
            raise RuntimeError("旧库 V7 第三方配置表未创建")
        playback_table = mysql.execute(
            "SELECT COUNT(*) FROM information_schema.tables "
            "WHERE table_schema = DATABASE() AND table_name = 'playback_event'",
            legacy_database,
        )
        if playback_table != "1":
            raise RuntimeError("旧库 V8 播放事件表未创建")
        created_at = mysql.execute(
            "SELECT COUNT(*) FROM information_schema.columns "
            "WHERE table_schema = DATABASE() AND table_name = 'app_user' AND column_name = 'created_at'",
            legacy_database,
        )
        if created_at != "1":
            raise RuntimeError("旧库 V8 用户创建时间字段未创建")
        like_table = mysql.execute(
            "SELECT COUNT(*) FROM information_schema.tables "
            "WHERE table_schema = DATABASE() AND table_name = 'drama_like'",
            legacy_database,
        )
        if like_table != "1":
            raise RuntimeError("旧库 V9 点赞关系表未创建")
        like_constraints = mysql.execute(
            "SELECT COUNT(*) FROM information_schema.referential_constraints "
            "WHERE constraint_schema = DATABASE() AND table_name = 'drama_like' "
            "AND delete_rule = 'CASCADE'",
            legacy_database,
        )
        if like_constraints != "2":
            raise RuntimeError("旧库 V9 点赞关系外键级联规则异常")
        storage_columns = mysql.execute(
            "SELECT GROUP_CONCAT(CONCAT(column_name, ':', column_type, ':', is_nullable, ':', "
            "COALESCE(column_default, 'NULL')) ORDER BY ordinal_position SEPARATOR '|') "
            "FROM information_schema.columns WHERE table_schema = DATABASE() "
            "AND table_name = 'media_asset' AND column_name IN ('storage_backend', 'object_key')",
            legacy_database,
        )
        if storage_columns != (
            "storage_backend:enum('LOCAL','OSS'):NO:LOCAL|object_key:varchar(512):YES:NULL"
        ):
            raise RuntimeError(f"旧库 V10 OSS 存储字段异常：{storage_columns}")
        object_key_index = mysql.execute(
            "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() "
            "AND table_name = 'media_asset' AND index_name = 'uk_media_asset_object_key' "
            "AND non_unique = 0 AND column_name = 'object_key'",
            legacy_database,
        )
        if object_key_index != "1":
            raise RuntimeError("旧库 V10 OSS object key 唯一索引异常")
        mysql.execute(
            "INSERT INTO media_asset(type, original_filename, storage_filename, content_type, "
            "size_bytes, duration_seconds, created_at) VALUES "
            "('COVER', 'legacy.png', '123e4567-e89b-12d3-a456-426614174000.png', "
            "'image/png', 1, NULL, UTC_TIMESTAMP(6))",
            legacy_database,
        )
        legacy_storage = mysql.execute(
            "SELECT CONCAT(storage_backend, ':', COALESCE(object_key, 'NULL')) "
            "FROM media_asset WHERE original_filename = 'legacy.png'",
            legacy_database,
        )
        if legacy_storage != "LOCAL:NULL":
            raise RuntimeError(f"旧素材未正确兼容 LOCAL 后端：{legacy_storage}")
        print("Flyway 隔离验证通过：空库 V1-V10，旧库 baseline V1 后 V2-V10，"
              "中文注释已更新，重复启动无新增迁移。")
    finally:
        for database in generated:
            try:
                mysql.execute(f"DROP DATABASE IF EXISTS {database}")
            except (subprocess.CalledProcessError, RuntimeError):
                pass
        mysql.close()


if __name__ == "__main__":
    main()
