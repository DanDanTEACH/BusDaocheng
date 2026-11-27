import csv, json, os, sys
from pathlib import Path
from faster_whisper import WhisperModel

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(r'C:\Users\w1048\Desktop\公車LED\大南\黃機音檔\0511')
out = Path(sys.argv[2]) if len(sys.argv) > 2 else Path('baolu_transcripts.csv')
files = sorted(p for p in root.rglob('*') if p.suffix.lower() in {'.mp3','.wav','.m4a','.aac','.flac'})
model = WhisperModel(os.environ.get('BAOLU_WHISPER_MODEL','small'), device='cpu', compute_type='int8')
new = not out.exists()
with out.open('a', newline='', encoding='utf-8-sig') as fh:
    w = csv.writer(fh)
    if new: w.writerow(['path','language','text','segments'])
    done = set()
    if not new:
        with out.open('r', encoding='utf-8-sig', newline='') as old:
            done = {row['path'] for row in csv.DictReader(old)}
    for i, path in enumerate(files, 1):
        rel = str(path.relative_to(root))
        if rel in done: continue
        try:
            segs, info = model.transcribe(str(path), beam_size=3, vad_filter=True, task='transcribe')
            items = list(segs)
            text = ''.join(s.text for s in items).strip()
            w.writerow([rel, info.language, text, json.dumps([s.text for s in items], ensure_ascii=False)])
            fh.flush()
            print(f'[{i}/{len(files)}] {info.language} {rel}: {text}', flush=True)
        except Exception as e:
            w.writerow([rel, 'error', str(e), ''])
            fh.flush()
            print(f'[{i}/{len(files)}] ERROR {rel}: {e}', flush=True)
