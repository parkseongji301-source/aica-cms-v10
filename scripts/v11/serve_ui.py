"""Serve a verified React UI with the unchanged, relocated V11 runtime.

The transfer tools, JAR, receipt, database settings and normal STOP.ps1 stay intact.
The verified next-app directory and optional original-plus-auth-skin CSS take precedence.
"""
import argparse
import importlib.util
import os
from pathlib import Path
import socket
import sys
import time
import zipfile


def verify_auth_stylesheet(assets, runtime_jar):
    """Allow only the approved legacy CSS plus the released authentication skin."""
    overlay = assets / 'css/flow.css'
    if not overlay.exists():
        return
    with zipfile.ZipFile(runtime_jar) as jar:
        original = jar.read('BOOT-INF/classes/static/css/flow.css')
    skin = (assets / 'next-app/login-shell.css').read_bytes()
    if overlay.read_bytes() != original + b'\n' + skin:
        raise ValueError('Authentication stylesheet must preserve approved runtime CSS')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--workspace', type=Path, required=True)
    parser.add_argument('--ui-release', type=Path, required=True)
    parser.add_argument('--verify-only', action='store_true')
    args = parser.parse_args()
    workspace = args.workspace.resolve(strict=True)
    release = args.ui_release.resolve(strict=True)
    spec = importlib.util.spec_from_file_location('v11_transfer', workspace / 'tools/transfer_v11.py')
    transfer = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(transfer)
    c = transfer.c
    transfer.env()
    root, info = transfer.config(workspace)
    manifest = c.load(release / 'ui-manifest.json')
    assets = release / 'assets'
    actual = {}
    for path in assets.rglob('*'):
        if not path.is_file():
            continue
        relative = path.relative_to(assets).as_posix()
        c.require(path.resolve().is_relative_to(assets.resolve()), 'UI asset escapes release')
        c.require(relative.startswith('next-app/') or relative == 'css/flow.css', 'unsupported UI asset override')
        actual[relative] = c.digest(path)
    c.require(actual and actual == manifest['files'], 'UI release checksum mismatch')
    c.require('next-app/index.html' in actual, 'UI entry point missing')
    c.require(manifest['runtimeJarSha256'] == info['rcSha256'], 'UI targets another runtime')
    verify_auth_stylesheet(assets, Path(info['rc']))
    if args.verify_only:
        print('PASS unchanged V11 runtime and verified UI assets', flush=True)
        return

    run = root / 'run'
    java, rc, db = (Path(info[key]) for key in ('java', 'rc', 'db'))
    agent = run / 'shutdown-tool/graceful-stop.jar'
    c.exclusive(db)
    c.require((run / 'activation.json').exists(), 'activate the verified transfer before applying UI')
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', info['port']))
    evidence = run / ('serve-' + str(time.time_ns()))
    evidence.mkdir()
    c.save(evidence / 'ui-release.json', {
        'release': str(release), 'manifestSha256': c.digest(release / 'ui-manifest.json'),
        'launcherSha256': c.digest(Path(__file__)), 'files': actual,
    })
    # Validate the inherited environment first, then permit this verified UI override.
    static_key = 'SPRING_WEB_RESOURCES_STATIC_LOCATIONS'
    os.environ[static_key] = assets.as_uri() + '/,classpath:/META-INF/resources/,classpath:/resources/,classpath:/static/,classpath:/public/'
    try:
        server = c.start(java, rc, db, info['port'], evidence, 'normal-runtime', run / 'runtime-receipt.json', agent=agent)
    finally:
        os.environ.pop(static_key, None)
    try:
        kernel, handle, stamp = transfer.process_handle(server.pid)
        kernel.CloseHandle(handle)
        active = dict(status='RUNNING', pid=server.pid, creationTime=stamp, database=str(db),
                      jarSha256=info['rcSha256'], evidence=str(evidence), port=info['port'])
        c.save(run / 'active.json', active)
    except Exception:
        c.stop(java, agent, server, evidence, 'normal-runtime', db)
        raise
    print('V11 UI ready: http://127.0.0.1:' + str(info['port']) + '/admin-next/posts?view=structure', flush=True)
    try:
        c.require(server.wait() == 0, 'server exited abnormally')
    except KeyboardInterrupt:
        c.stop(java, agent, server, evidence, 'normal-runtime', db)
    finally:
        if server.poll() is not None:
            active.update(status='STOPPED', exitCode=server.returncode)
            c.save(run / 'active.json', active)


if __name__ == '__main__':
    main()
