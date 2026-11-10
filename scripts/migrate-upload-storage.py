#!/usr/bin/env python3
"""이전 폴더의 업로드 파일을 public/images·public/files로 옮기는 일회성 이관 (docs/portfolio-files-api.md "기존 파일 이관").

- 프로필·배너 이미지
  이전 폴더의 {UUID}.{확장자}를 public/images/{profile|banner}_{회원 id}_{yyyyMMddHHmmss}.{확장자}로 복사하고,
  users.profile_image·banner_image의 이전 주소(.../api/v1/profile-images/{UUID}.{확장자})를 /images/{새 이름}으로 바꾼다.
  시각은 이전 파일의 수정 시각(한국 시각)이다. 파일이 없는 주소는 바꾸지 않고 목록만 보여 준다.
- 포트폴리오 파일
  이전 폴더의 {UUID}.bin·{UUID}.json을 public/files/{UUID}.{확장자}·{UUID}.json으로 복사한다.
  주소(/api/files/{UUID})와 DB 값은 바꾸지 않는다.

--apply가 없으면 확인만 하고 아무것도 바꾸지 않는다. 이전 파일은 지우지 않는다. 다시 실행해도 이미 옮긴 항목은 건너뛴다.

DB는 psql로 연결한다. --psql에 연결할 명령을 준다(기본값 psql, 접속 정보는 PGHOST·PGUSER·PGDATABASE·PGPASSWORD 등).
  운영 서버 예:  sudo python3 scripts/migrate-upload-storage.py \\
                   --psql "docker compose -f compose.prod.yaml exec -T postgres psql -U devmozip -d devmozip" \\
                   --profile-dir ./old-profile-images --portfolio-dir ./old-portfolio-files
  운영 public 폴더는 backend 이미지의 app 사용자 소유라 sudo로 실행한다. 복사한 파일은 다른 사용자도 읽을 수 있어
  backend(읽기·삭제)와 nginx(읽기)가 쓸 수 있다. 이전 파일이 컨테이너 안(/app/uploads)에 있으면 먼저 docker cp로 꺼낸다.
"""
import argparse
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
KST = timezone(timedelta(hours=9))
UUID = r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
OLD_PROFILE_URL = re.compile(r'^(?:https?://[^/]+)?/api/v1/profile-images/(' + UUID + r'\.(png|jpg|webp))$')
UUID_NAME = re.compile('^' + UUID + '$')
EXTENSIONS = {'image/png': 'png', 'image/jpeg': 'jpg', 'image/webp': 'webp', 'application/pdf': 'pdf'}
COLUMNS = {'profile_image': 'profile', 'banner_image': 'banner'}


def run_psql(base, sql, read):
    # -1(한 트랜잭션)은 -f와 함께 써야 적용되므로 SQL을 표준 입력 파일(-f -)로 넘긴다
    command = shlex.split(base) + ['-X', '-q', '-v', 'ON_ERROR_STOP=1', '-f', '-'] + (['-A', '-t', '-F', '\t'] if read else ['-1'])
    result = subprocess.run(command, input=sql, capture_output=True, text=True)
    if result.returncode:
        raise SystemExit('psql 실행에 실패했습니다:\n' + result.stderr.strip())
    return result.stdout


def literal(value):
    return "'" + value.replace("'", "''") + "'"


def profile_rows(psql):
    sql = ("SELECT id, coalesce(profile_image, ''), coalesce(banner_image, '') FROM users "
           "WHERE profile_image LIKE '%/api/v1/profile-images/%' OR banner_image LIKE '%/api/v1/profile-images/%' ORDER BY id;\n")
    rows = []
    for line in run_psql(psql, sql, True).splitlines():
        if line.strip():
            member_id, profile, banner = line.split('\t')
            rows.append((int(member_id), {'profile_image': profile, 'banner_image': banner}))
    return rows


def plan_profiles(rows, source_dir, images_dir):
    """(회원 id, 컬럼, 이전 주소, 원본 파일, 새 이름) 목록과 옮기지 못하는 항목을 만든다."""
    planned, missing, taken = [], [], set()
    for member_id, values in rows:
        for column, kind in COLUMNS.items():
            old = values[column]
            if '/api/v1/profile-images/' not in old:
                continue
            match = OLD_PROFILE_URL.match(old)
            source = source_dir / match.group(1) if match else None
            if not source or not source.is_file() or source.is_symlink():
                missing.append((member_id, column, old))
                continue
            time = datetime.fromtimestamp(source.stat().st_mtime, KST)
            while True:
                name = f'{kind}_{member_id}_{time:%Y%m%d%H%M%S}.{match.group(2)}'
                if name not in taken and not (images_dir / name).exists():
                    break
                time += timedelta(seconds=1)
            taken.add(name)
            planned.append((member_id, column, old, source, name))
    return planned, missing


def plan_portfolio(source_dir, files_dir):
    """(원본 데이터, 원본 메타데이터, 새 데이터 이름, 메타데이터 이름) 목록과 건너뛰는 항목을 만든다."""
    planned, skipped = [], []
    if not source_dir.is_dir():
        return planned, skipped
    for meta in sorted(source_dir.glob('*.json')):
        file_id = meta.stem
        if not UUID_NAME.match(file_id):
            continue
        try:
            value = json.loads(meta.read_text(encoding='utf-8'))
            extension = EXTENSIONS[value['contentType']]
            if value['id'] != file_id or int(value['ownerId']) <= 0:
                raise ValueError
        except (OSError, ValueError, KeyError, TypeError):
            skipped.append((meta.name, '메타데이터 형식이 맞지 않음'))
            continue
        data = source_dir / f'{file_id}.bin'
        if not data.is_file() or data.is_symlink() or data.stat().st_size != value.get('size'):
            skipped.append((meta.name, '데이터 파일이 없거나 크기가 다름'))
            continue
        target_data, target_meta = files_dir / f'{file_id}.{extension}', files_dir / meta.name
        if target_data.exists() or target_meta.exists():
            same = (target_data.is_file() and target_meta.is_file() and target_data.stat().st_size == data.stat().st_size)
            skipped.append((meta.name, '이미 옮김' if same else '대상에 다른 파일이 있음'))
            continue
        planned.append((data, meta, target_data, target_meta))
    return planned, skipped


def copy_new(source, target):
    # 같은 이름이 생겼으면 덮어쓰지 않고 실패한다
    with open(source, 'rb') as reader, open(target, 'xb') as writer:
        shutil.copyfileobj(reader, writer)
    shutil.copystat(source, target)
    target.chmod(0o644)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--psql', default='psql', help='DB에 연결할 psql 명령 (기본값: psql)')
    parser.add_argument('--profile-dir', type=Path, default=ROOT / 'backend/uploads/profile-images', help='이전 프로필 이미지 폴더')
    parser.add_argument('--portfolio-dir', type=Path, default=ROOT / 'backend/uploads/portfolio-files', help='이전 포트폴리오 파일 폴더')
    parser.add_argument('--images-dir', type=Path, default=ROOT / 'public/images', help='새 이미지 폴더 (IMAGE_STORAGE)')
    parser.add_argument('--files-dir', type=Path, default=ROOT / 'public/files', help='새 파일 폴더 (FILE_STORAGE)')
    parser.add_argument('--apply', action='store_true', help='실제로 복사하고 DB를 바꾼다. 없으면 확인만 한다')
    args = parser.parse_args()

    profiles, missing = plan_profiles(profile_rows(args.psql), args.profile_dir, args.images_dir)
    portfolio, skipped = plan_portfolio(args.portfolio_dir, args.files_dir)

    print(f'[프로필·배너] 옮길 주소 {len(profiles)}개, 파일이 없어 그대로 두는 주소 {len(missing)}개')
    for member_id, column, old, _, name in profiles:
        print(f'  회원 {member_id} {column}: {old} -> /images/{name}')
    for member_id, column, old in missing:
        print(f'  (파일 없음) 회원 {member_id} {column}: {old}')
    print(f'[포트폴리오] 옮길 파일 {len(portfolio)}개, 건너뜀 {len(skipped)}개')
    for data, _, target_data, _ in portfolio:
        print(f'  {data.name} -> {target_data}')
    for name, reason in skipped:
        print(f'  (건너뜀: {reason}) {name}')

    if not args.apply:
        print('확인만 했습니다. 실제로 옮기려면 --apply를 붙여 다시 실행하세요.')
        return

    for path in (args.images_dir, args.files_dir):
        path.mkdir(parents=True, exist_ok=True)
    for data, meta, target_data, target_meta in portfolio:
        copy_new(data, target_data)
        copy_new(meta, target_meta)
    copied = []
    try:
        for *_, source, name in profiles:
            copy_new(source, args.images_dir / name)
            copied.append(args.images_dir / name)
        if profiles:
            # 이전 주소가 그대로일 때만 바꾼다. 실행 중에 회원이 이미지를 바꿨으면 그 행은 건드리지 않는다
            statements = ''.join(
                f'UPDATE users SET {column} = {literal("/images/" + name)} WHERE id = {member_id} AND {column} = {literal(old)};\n'
                for member_id, column, old, _, name in profiles)
            run_psql(args.psql, statements, False)
    except BaseException:
        for path in copied:
            path.unlink(missing_ok=True)
        print('프로필 이미지 이관을 되돌렸습니다(복사한 이미지 삭제, DB 변경 없음). 포트폴리오 파일 복사는 유지합니다.', file=sys.stderr)
        raise
    print('이관을 마쳤습니다. 화면에서 확인한 뒤 이전 폴더를 지우세요.')


if __name__ == '__main__':
    main()
