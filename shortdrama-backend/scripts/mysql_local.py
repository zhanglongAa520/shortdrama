"""Run SQL on the local project database using an ignored configuration file."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

from local_config import mysql_config

config = mysql_config()

parser = argparse.ArgumentParser()
parser.add_argument('--query')
parser.add_argument('--file', type=Path)
parser.add_argument('--database', action='store_true')
args = parser.parse_args()
def quote(value):
    return '"' + value.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n') + '"'

with tempfile.NamedTemporaryFile(mode='w', prefix='yingke-mysql-', suffix='.cnf') as handle:
    os.chmod(handle.name, 0o600)
    handle.write('[client]\n')
    for key, default, option in [('MYSQL_HOST', '127.0.0.1', 'host'), ('MYSQL_PORT', '3306', 'port'),
                                 ('MYSQL_USER', 'root', 'user'), ('MYSQL_PASSWORD', '', 'password')]:
        handle.write(f'{option}={quote(config.get(key, default))}\n')
    handle.flush()
    command = ['mysql', f'--defaults-extra-file={handle.name}', '--default-character-set=utf8mb4']
    if args.database:
        command.append(config.get('MYSQL_DATABASE', 'yingke_drama'))
    sql = args.file.read_text() if args.file else args.query
    if not sql:
        parser.error('Provide --query or --file')
    subprocess.run(command, input=sql, text=True, check=True)
