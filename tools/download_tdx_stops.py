import json, os, time
from pathlib import Path
import requests

out = Path(os.environ.get('TDX_OUTPUT', r'C:\Users\w1048\Desktop\公車LED\報站\轉換\GPS資料庫'))
out.mkdir(parents=True, exist_ok=True)
cities = ['Taipei','NewTaipei','Taoyuan','Taichung','Tainan','Kaohsiung','Keelung','Hsinchu','HsinchuCounty','MiaoliCounty','ChanghuaCounty','NantouCounty','YunlinCounty','Chiayi','ChiayiCounty','PingtungCounty','YilanCounty','HualienCounty','TaitungCounty','PenghuCounty','KinmenCounty','LienchiangCounty']
token_url = 'https://tdx.transportdata.tw/auth/realms/TDXConnect/protocol/openid-connect/token'
base = 'https://tdx.transportdata.tw/api/basic/v2/Bus'
r = requests.post(token_url, data={'grant_type':'client_credentials','client_id':os.environ['TDX_CLIENT_ID'],'client_secret':os.environ['TDX_CLIENT_SECRET']}, timeout=60)
r.raise_for_status(); token = r.json()['access_token']
headers = {'Authorization': f'Bearer {token}'}
for city in cities:
    for kind in ('Stop','Station'):
        target = out / f'{city}_{kind}.json'
        if target.exists() and target.stat().st_size > 100:
            print('skip', city, kind, flush=True); continue
        url = f'{base}/{kind}/City/{city}?$format=JSON'
        for attempt in range(6):
            res = requests.get(url, headers=headers, timeout=180)
            if res.status_code != 429: break
            time.sleep(10 * (attempt + 1))
        if res.status_code == 400:
            print('skip unavailable', city, kind, flush=True); continue
        res.raise_for_status()
        target.write_text(json.dumps(res.json(), ensure_ascii=False), encoding='utf-8')
        time.sleep(2)
        print(city, kind, len(res.content), flush=True)
for kind in ('Stop','Station'):
    target = out / f'InterCity_{kind}.json'
    if target.exists() and target.stat().st_size > 100:
        print('skip InterCity', kind, flush=True); continue
    url = f'{base}/{kind}/InterCity?$format=JSON'
    for attempt in range(6):
        res = requests.get(url, headers=headers, timeout=240)
        if res.status_code != 429: break
        time.sleep(10 * (attempt + 1))
    res.raise_for_status()
    target.write_text(json.dumps(res.json(), ensure_ascii=False), encoding='utf-8')
    print('InterCity', kind, len(res.content), flush=True)
