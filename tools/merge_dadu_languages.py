import csv, re, shutil
from pathlib import Path
root=Path(r'C:\Users\w1048\Desktop\公車LED\報站\大都會巴士王\routedata')
out=Path(r'C:\Users\w1048\Desktop\公車LED\報站\轉換\基北北桃\大都會_四語整合')
out.mkdir(parents=True,exist_ok=True)
rows=list(csv.DictReader(open('batch_dadu_hinese.csv',encoding='utf-8-sig')))
by={r['path']:r for r in rows}
labels={'1':'中文','2':'台語','3':'客語','4':'英文'}
count=0
for voice in root.glob('voice*'):
  for n,label in labels.items():
    folder=root/voice.name/n
    if not folder.exists(): continue
    for p in folder.iterdir():
      if not p.is_file(): continue
      zh=by.get(str((root/voice.name/'1'/p.name).relative_to(root)))
      if not zh: continue
      name=re.sub(r'[\\/:*?"<>|]','',re.sub(r'[\r\n]+','',zh['text']).strip())[:80] or '未命名'
      dest=out/f'{name}(立皓)'; dest.mkdir(exist_ok=True)
      shutil.copy2(p,dest/f'{label}{p.suffix.lower()}'); count+=1
print('merged',count,'files into',len(list(out.iterdir())),'folders')
