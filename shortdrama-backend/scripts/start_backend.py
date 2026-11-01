"""Start the MySQL backend using its local YAML configuration."""
import os
from pathlib import Path

backend = Path(__file__).resolve().parents[1]
if not (backend / 'application-local.yml').is_file():
    raise SystemExit('请复制 application-local.example.yml 为 application-local.yml 并填写配置')
os.environ['SPRING_PROFILES_ACTIVE'] = 'mysql'
os.chdir(backend)
os.execvp('mvn', ['mvn', 'spring-boot:run', '-Dspring-boot.run.arguments=--server.address=127.0.0.1'])
