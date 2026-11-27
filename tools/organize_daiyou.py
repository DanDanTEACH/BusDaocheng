import csv, re, shutil
from pathlib import Path
root = Path(r'C:\Users\w1048\Desktop\公車LED\報站\大有巴士王\routedata')
out = Path(r'C:\Users\w1048\Desktop\公車LED\報站\轉換\基北北桃\大有')
out.mkdir(parents=True, exist_ok=True)
n = 0
for row in csv.DictReader(open('batch_daiyou.csv', encoding='utf-8-sig')):
    text = re.sub(r'[\r\n]+', '', row['text']).strip()
    text = re.sub(r'[\\/:*?"<>|]', '', text)[:80] or '未命名'
    src = root / row['path']
    if not src.exists(): continue
    folder = out / f'{text}(立皓)'
    folder.mkdir(exist_ok=True)
    shutil.copy2(src, folder / f'{src.stem}_{src.parent.name}{src.suffix.lower()}')
    n += 1
print('copied', n, 'folders', len(list(out.iterdir())))
