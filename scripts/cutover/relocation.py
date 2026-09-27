"""V10 relocation approval. No schema/data writes, migration, seed, or web bootstrap.

The approved Spring/React RC and all its runtime guards remain unchanged. A small
read-only inspector calls that RC's FileDatabaseSafety via PropertiesLauncher.
Approvals are local operator artifacts, not signatures against a privileged host user.
"""
import contextlib
import ctypes
import datetime as dt
import json
import os
from pathlib import Path
import zipfile

import cutover as c

KIND = 'V10_RELOCATION'
WORKAROUND = 'AUTO_COMPACT_FILL_RATE=0'
TOOL_FILES = ('cutover.py', 'relocation.py', 'RelocationInspect.java', 'GracefulStop.java', 'http_client.py')


def now():
    return dt.datetime.now(dt.timezone.utc)


def new_json(path, value):
    with Path(path).open('x', encoding='utf-8') as out:
        json.dump(value, out, ensure_ascii=False, indent=2)


def safe_env():
    c.require(os.environ.get('AICA_CUTOVER_ENABLED', '').lower() != 'true', 'cutover flag is forbidden for relocation')
    c.require(not os.environ.get('AICA_CUTOVER_APPROVAL_SHA256'), 'V3 cutover approval is not a relocation approval')
    return c.clean_env()


def pinned(path, expected):
    path = Path(path)
    c.require(path.is_absolute(), 'explicit absolute file path required: ' + str(path))
    path = path.resolve(strict=True)
    c.require(len(expected) == 64 and all(x in '0123456789abcdef' for x in expected.lower()), 'invalid pinned SHA-256')
    c.require(c.digest(path) == expected.lower(), 'pinned checksum mismatch: ' + str(path))
    return path


def tool_hashes():
    return {name: c.digest(c.HERE / name) for name in TOOL_FILES}


@contextlib.contextmanager
def read_lease(db):
    """Windows share-read handle allows H2 read-only inspection, denies writes/deletion."""
    c.exclusive(db)
    kernel = ctypes.WinDLL('kernel32', use_last_error=True)
    kernel.CreateFileW.restype = ctypes.c_void_p
    kernel.CreateFileW.argtypes = [ctypes.c_wchar_p, ctypes.c_uint32, ctypes.c_uint32, ctypes.c_void_p, ctypes.c_uint32, ctypes.c_uint32, ctypes.c_void_p]
    handle = kernel.CreateFileW(str(db), 0x80000000, 1, None, 3, 0, None)
    c.require(handle != ctypes.c_void_p(-1).value, 'cannot acquire read-only relocation lease')
    before = c.digest(db)
    try:
        yield before
    finally:
        unchanged = before == c.digest(db)
        kernel.CloseHandle.argtypes = [ctypes.c_void_p]
        kernel.CloseHandle(handle)
        c.require(unchanged, 'relocation changed database bytes')


def prepare_inspector(java, rc, folder):
    """Compile only the inspection tool; never rebuild/modify the approved RC or React."""
    folder.mkdir()
    classes = folder / 'rc-classes'
    libs = folder / 'rc-libs'
    classes.mkdir(); libs.mkdir()
    with zipfile.ZipFile(rc) as archive:
        for name in archive.namelist():
            if name.endswith('/'):
                continue
            if name.startswith('BOOT-INF/classes/'):
                relative = Path(name.removeprefix('BOOT-INF/classes/'))
                c.require(not relative.is_absolute() and '..' not in relative.parts, 'unsafe RC entry')
                target = classes / relative
            elif name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
                target = libs / Path(name).name
            else:
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(name))
    compiled = folder / 'helper-classes'; compiled.mkdir()
    cp = str(classes) + os.pathsep + str(libs / '*')
    c.run_cmd([java.parent/'javac.exe', '-encoding', 'UTF-8', '-cp', cp, '-d', compiled, c.HERE/'RelocationInspect.java'], folder/'compile.log', safe_env())
    helper = folder / 'relocation-inspect.jar'
    c.run_cmd([java.parent/'jar.exe', '--create', '--file', helper, '-C', compiled, '.'], folder/'jar.log', safe_env())
    with zipfile.ZipFile(helper) as archive:
        payload = [n for n in archive.namelist() if n.endswith('.class')]
        c.require(payload == ['egovframework/backoffice/mvp/operations/RelocationInspect.class'], 'inspector must not override RC classes')
    return helper


def inspect_v10(java, rc, db, output, helper):
    env = safe_env()
    args = [java, '-Dfile.encoding=UTF-8', '-Dloader.path=' + helper.as_uri(),
            '-Dloader.main=egovframework.backoffice.mvp.operations.RelocationInspect', '-cp', rc,
            'org.springframework.boot.loader.launch.PropertiesLauncher', db, output, c.digest(rc)]
    c.run_cmd(args, str(output)+'.log', env)
    result = c.load(output)
    c.require(result['validated'] and result['pending'] == 0 and result['migrationsExecuted'] == 0 and result['bytesUnchanged'], 'incomplete V10 inspection')
    c.require(result['writerUrl'] == c.jdbc(db), 'writer URL differs from normal serve')
    return result


def references(p):
    rc = pinned(p['rc'], p['rcSha256'])
    v3 = pinned(p['v3'], p['v3Sha256'])
    sources = {}
    for name, ref in p['references'].items():
        sources[name] = c.load(pinned(ref['path'], ref['sha256']))
    cold = sources['inspection']; receipt = sources['receipt']; manifest = sources['releaseManifest']
    c.require(receipt.get('status') == 'MIGRATED_V10' and receipt.get('workaround') == WORKAROUND, 'invalid source receipt')
    c.require(receipt.get('approvalKind') in (None, KIND), 'unknown source approval kind')
    c.require(receipt.get('jarSha256') == p['rcSha256'] == cold.get('jarSha256'), 'source RC mismatch')
    c.require(manifest.get('v10-rc.jar') == p['rcSha256'] and manifest.get('v3-runtime.jar') == p['v3Sha256'], 'release manifest mismatch')
    c.require([x.get('version') for x in cold.get('history', [])] == [str(x) for x in range(1, 11)], 'source must be exactly V10')
    c.require(cold['history'] == receipt.get('migrations'), 'source receipt/inspection history mismatch')
    c.require(cold.get('fingerprints') and cold.get('columns') and len(cold.get('sha256', '')) == 64, 'incomplete source cold inspection')
    return rc, v3, cold


def same_source(actual, source):
    for key in ('sha256', 'history', 'columns', 'fingerprints'):
        c.require(actual[key] == source[key], 'transferred DB differs from source ' + key)


def plan(a):
    safe_env()
    output = Path(a.output).resolve()
    c.require(not output.exists(), 'relocation plan output already exists')
    c.require(Path(a.db).is_absolute(), 'absolute relocation DB path required')
    db = Path(a.db).resolve(strict=True)
    java = Path(a.java).resolve(strict=True)
    p = dict(format=1, approvalKind=KIND, databasePath=str(db), rc=str(Path(a.rc).resolve()),
             rcSha256=a.rc_sha256.lower(), v3=str(Path(a.v3_runtime).resolve()), v3Sha256=a.v3_sha256.lower(),
             createdAt=now().isoformat(), expiresAt=(now()+dt.timedelta(hours=24)).isoformat(),
             workaround=WORKAROUND, tools=tool_hashes(), references={})
    for name, path, sha in [('inspection', a.source_inspection, a.source_inspection_sha256),
                            ('receipt', a.source_receipt, a.source_receipt_sha256),
                            ('releaseManifest', a.release_manifest, a.release_manifest_sha256)]:
        f = pinned(path, sha); p['references'][name] = dict(path=str(f), sha256=sha.lower())
    rc, _, cold = references(p)
    helper = prepare_inspector(java, rc, Path(str(output)+'.tools'))
    with read_lease(db):
        actual = inspect_v10(java, rc, db, Path(str(output)+'.inspection.json'), helper)
        same_source(actual, cold)
        p.update(database=actual, migrations=actual['migrations'], jdbcUrl=actual['writerUrl'])
        new_json(output, p)
    print('READ-ONLY V10 relocation plan SHA-256:', c.digest(output), flush=True)


def checked_plan(file, expected):
    p = c.load(pinned(file, expected))
    c.require(p.get('format') == 1 and p.get('approvalKind') == KIND, 'not a V10 relocation plan')
    c.require(dt.datetime.fromisoformat(p['expiresAt']) > now(), 'relocation plan expired')
    c.require(p['tools'] == tool_hashes(), 'relocation tools changed; prepare a new plan')
    db = Path(p['databasePath']).resolve(strict=True)
    c.require(str(db) == p['databasePath'] and p['jdbcUrl'] == c.jdbc(db) and p['workaround'] == WORKAROUND, 'noncanonical relocation target/options')
    return p, db


def approve(a):
    safe_env()
    c.require(a.authorize_relocation, 'explicit V10 relocation authorization required')
    p, db = checked_plan(a.plan, a.approved_plan_sha)
    rc, v3, cold = references(p)
    java = Path(a.java).resolve(strict=True)
    root = Path(a.run_dir).resolve()
    c.require(not root.exists(), 'relocation run directory must be new')
    # Consumed plans cannot be reused, including after partial artifact-generation failure.
    with Path(str(Path(a.plan).resolve())+'.spent').open('x', encoding='utf-8') as spent:
        spent.write('kind='+KIND+'\nplanSha256='+a.approved_plan_sha.lower()+'\nstarted='+now().isoformat())
    root.mkdir(parents=True)
    helper = prepare_inspector(java, rc, root/'inspection-tool')
    agent = c.prepare_agent(java, root/'shutdown-tool')
    with read_lease(db) as before:
        actual = inspect_v10(java, rc, db, root/'approval-inspection.json', helper)
        same_source(actual, cold); same_source(actual, p['database'])
        c.require(actual['migrations'] == p['migrations'], 'migration manifest changed after plan')
        new_json(root/'relocation-plan.json', p)
        plan_sha = c.digest(root/'relocation-plan.json')
        at = now().isoformat()
        receipt = dict(status='MIGRATED_V10', approvalKind=KIND, approvedAt=at,
                       databasePath=str(db), jarSha256=c.digest(rc), workaround=WORKAROUND,
                       approvalSha256=a.approved_plan_sha.lower(), migrations=actual['migrations'], after=actual,
                       migrationPerformed=False, migrationsExecuted=0, baselineCreated=False,
                       initialDatabaseSha256=before, references=p['references'])
        # status is the unchanged RC's legacy schema-state contract, not a claim of new migration.
        new_json(root/'runtime-receipt.json', receipt)
        info = dict(kind=KIND, target=str(db), rc=str(rc), rcSha256=c.digest(rc), v3=str(v3), v3Sha256=c.digest(v3),
                    original=False, relocationPlanSha256=plan_sha,
                    receiptSha256=c.digest(root/'runtime-receipt.json'), inspectionSha256=c.digest(root/'approval-inspection.json'),
                    shutdownSha256=c.digest(agent), tools=tool_hashes())
        new_json(root/'input.json', info)
        new_json(root/'result.json', dict(kind=KIND, success=True, rollback=False, approvalOnly=True,
                    inputSha256=c.digest(root/'input.json'), approvedAt=at, migrationsExecuted=0,
                    baselineCreated=False, businessWrites=False, databaseBytesUnchanged=before == c.digest(db),
                    actualWebsiteE2E=False))
    c.exclusive(db)
    print('PASS V10 relocation approved; DB bytes unchanged; no migration or business writes:', root, flush=True)


def runtime_check(root, info):
    """Additional approval integrity/first-use checks; does not replace any RC guard."""
    safe_env()
    result = c.load(root/'result.json')
    c.require(result.get('kind') == KIND and result.get('success') and result.get('approvalOnly'), 'incomplete relocation approval')
    c.require(result['inputSha256'] == c.digest(root/'input.json'), 'relocation input changed')
    c.require(info['tools'] == tool_hashes(), 'approved relocation tool set changed')
    for name, key in [('runtime-receipt.json','receiptSha256'), ('approval-inspection.json','inspectionSha256'),
                       ('relocation-plan.json','relocationPlanSha256'), ('shutdown-tool/graceful-stop.jar','shutdownSha256')]:
        c.require(c.digest(root/name) == info[key], 'relocation artifact changed: '+name)
    receipt = c.load(root/'runtime-receipt.json'); approved = c.load(root/'approval-inspection.json')
    c.require(receipt['approvalKind'] == KIND and receipt['databasePath'] == info['target'] and receipt['jarSha256'] == info['rcSha256'], 'relocation receipt target/runtime mismatch')
    db = Path(info['target']).resolve(strict=True)
    c.require(str(db) == info['target'] and approved['path'] == str(db), 'relocation target path changed')
    c.exclusive(db)
    activation = root/'activation.json'
    if activation.exists():
        a = c.load(activation)
        c.require(a.get('kind') == KIND and a.get('inputSha256') == result['inputSha256'], 'invalid relocation activation record')
    else:
        c.require(c.digest(db) == approved['sha256'], 'DB changed before first serve; re-inspect and reapprove')
    return result


def activated(root, pid):
    path = root/'activation.json'
    if not path.exists():
        new_json(path, dict(kind=KIND, inputSha256=c.digest(root/'input.json'), activatedAt=now().isoformat(), pid=pid))


def add_commands(subparsers):
    p = subparsers.add_parser('relocate-plan', help='read-only V10 relocation plan; never migrates')
    for arg in ('java','rc','rc-sha256','v3-runtime','v3-sha256','db','source-inspection','source-inspection-sha256',
                'source-receipt','source-receipt-sha256','release-manifest','release-manifest-sha256','output'):
        p.add_argument('--'+arg, required=True)
    p = subparsers.add_parser('relocate-approve', help='one-shot approval for an inspected V10 file')
    for arg in ('java','plan','approved-plan-sha','run-dir'):
        p.add_argument('--'+arg, required=True)
    p.add_argument('--authorize-relocation', action='store_true')
