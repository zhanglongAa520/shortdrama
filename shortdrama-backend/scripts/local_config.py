"""Read MySQL connection settings from the backend's private YAML file."""
from pathlib import Path
from urllib.parse import urlparse, unquote

try:
    import yaml
except ImportError:
    raise SystemExit('请先运行 python3 -m pip install -r shortdrama-backend/scripts/requirements.txt')


def mysql_config() -> dict[str, str]:
    path = Path(__file__).resolve().parents[1] / 'application-local.yml'
    if not path.is_file():
        raise SystemExit('请先配置 shortdrama-backend/application-local.yml')
    config = yaml.safe_load(path.read_text()) or {}
    datasource = config.get('spring', {}).get('datasource', {})
    url = str(datasource.get('url', ''))
    if not url.startswith('jdbc:mysql://'):
        raise SystemExit('application-local.yml 中需填写完整的 MySQL JDBC URL')
    parsed = urlparse(url.removeprefix('jdbc:'))
    return {
        'MYSQL_HOST': parsed.hostname or '127.0.0.1',
        'MYSQL_PORT': str(parsed.port or 3306),
        'MYSQL_DATABASE': unquote(parsed.path.lstrip('/')),
        'MYSQL_USER': str(datasource.get('username', 'root')),
        'MYSQL_PASSWORD': str(datasource.get('password', '')),
    }
