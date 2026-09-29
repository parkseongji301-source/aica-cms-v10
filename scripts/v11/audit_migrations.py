"""Compare approved migration bytes and actual Flyway checksums before making an RC."""
import argparse, hashlib, json, os, subprocess, zipfile
from pathlib import Path

def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def require(ok,message):
    if not ok: raise RuntimeError('STOP: '+message)
def write(path,data):
    with Path(path).open('x',encoding='utf-8') as f: json.dump(data,f,ensure_ascii=False,indent=2)
def extract(jar,root):
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            if n.endswith('/'): continue
            if n.startswith('BOOT-INF/classes/'): target=root/'classes'/n.removeprefix('BOOT-INF/classes/')
            elif n.startswith('BOOT-INF/lib/') and n.endswith('.jar'): target=root/'lib'/Path(n).name
            else: continue
            require(target.resolve().is_relative_to(root.resolve()),'unsafe ZIP path')
            target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(z.read(n))
def resolve(java,root,output):
    cp=os.pathsep.join([str(root/'helper'),str(root/'classes'),str(root/'lib'/'*')])
    with Path(str(output)+'.log').open('x',encoding='utf-8') as log:
        subprocess.run([str(java),'-Dfile.encoding=UTF-8','-cp',cp,'MigrationManifest',str(output)],check=True,stdout=log,stderr=subprocess.STDOUT,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
    return json.loads(output.read_text(encoding='utf-8'))
def main():
    p=argparse.ArgumentParser();p.add_argument('--java',type=Path,required=True);p.add_argument('--approved-jar',type=Path,required=True);p.add_argument('--approved-sha',required=True)
    p.add_argument('--workspace',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--restore-line-endings',action='store_true');p.add_argument('--candidate',type=Path)
    a=p.parse_args();require(sha(a.approved_jar)==a.approved_sha.lower(),'approved JAR hash differs');a.output.mkdir(parents=True,exist_ok=False)
    root=a.output/'runtime';extract(a.approved_jar,root);(root/'helper').mkdir()
    cp=os.pathsep.join([str(root/'classes'),str(root/'lib'/'*')]);java=a.java.resolve()
    subprocess.run([str(java.parent/'javac.exe'),'-encoding','UTF-8','-cp',cp,'-d',str(root/'helper'),str(Path(__file__).with_name('MigrationManifest.java'))],check=True,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
    approved=resolve(java,root,a.output/'approved-manifest.json');require([x['version'] for x in approved]==[str(i) for i in range(1,11)],'approved baseline must be V1..V10')
    rows=[]
    with zipfile.ZipFile(a.approved_jar) as z:
        for n in z.namelist():
            if not n.startswith('BOOT-INF/classes/db/migration/h2/') or not n.endswith('.sql'): continue
            source=a.workspace/'src/main/resources'/n.removeprefix('BOOT-INF/classes/');old=z.read(n);current=source.read_bytes()
            require(old.replace(b'\r\n',b'\n')==current.replace(b'\r\n',b'\n'),'migration meaning/content differs: '+source.name)
            rows.append(dict(file=source.name,approvedSha256=hashlib.sha256(old).hexdigest(),beforeSha256=hashlib.sha256(current).hexdigest(),lineEndingsOnly=old!=current))
            (root/'classes'/n.removeprefix('BOOT-INF/classes/')).write_bytes(current)
        before=resolve(java,root,a.output/'workspace-before-manifest.json');require(before==approved,'existing Flyway checksum differs before normalization')
        for row in rows:
            name=row['file'];old=z.read('BOOT-INF/classes/db/migration/h2/'+name);source=a.workspace/'src/main/resources/db/migration/h2'/name
            if a.restore_line_endings and source.read_bytes()!=old: source.write_bytes(old)
            require(source.read_bytes()==old,'raw migration bytes differ; restore approved line endings first')
            (root/'classes/db/migration/h2'/name).write_bytes(source.read_bytes());row['afterSha256']=sha(source)
    after=resolve(java,root,a.output/'workspace-after-manifest.json');require(after==approved,'normalized checksums differ')
    result=dict(success=True,approvedJarSha256=sha(a.approved_jar),flywayChecksumsUnchangedBeforeAndAfter=True,files=rows,approvedMigrations=approved)
    if a.candidate:
        with zipfile.ZipFile(a.approved_jar) as old,zipfile.ZipFile(a.candidate) as new:
            names=[n for n in old.namelist() if n.startswith('BOOT-INF/classes/db/migration/h2/') and not n.endswith('/')]
            for n in names: require(old.read(n)==new.read(n),'candidate legacy migration bytes differ: '+n)
            require(new.read('BOOT-INF/classes/db/migration/h2/V11__post_trash.sql')==(a.workspace/'src/main/resources/db/migration/h2/V11__post_trash.sql').read_bytes(),'V11 source mismatch')
        candidate=a.output/'candidate';extract(a.candidate,candidate);(candidate/'helper').mkdir();(candidate/'helper/MigrationManifest.class').write_bytes((root/'helper/MigrationManifest.class').read_bytes())
        manifests=resolve(java,candidate,a.output/'candidate-manifest.json');require(manifests[:10]==approved and [x['version'] for x in manifests]==[str(i) for i in range(1,12)],'candidate migration sequence differs')
        result.update(candidateJarSha256=sha(a.candidate),legacyMigrationBytesIdentical=True,migrations=manifests)
    write(a.output/'result.json',result);print(json.dumps({k:v for k,v in result.items() if k not in ('files','approvedMigrations','migrations')},ensure_ascii=False))
if __name__=='__main__':main()
