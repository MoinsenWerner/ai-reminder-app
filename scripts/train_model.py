#!/usr/bin/env python3
import json, pathlib, random
root = pathlib.Path(__file__).resolve().parents[1]
data = [json.loads(l) for l in (root/'datasets/jarvis_training.jsonl').read_text().splitlines()]
labels = sorted({d['label'] for d in data})
keywords = {label:{} for label in labels}
for d in data:
    for token in d['text'].lower().replace(':',' ').replace('?',' ').split():
        keywords[d['label']][token] = keywords[d['label']].get(token,0)+1
correct = 0
for d in data:
    scores = {label: sum(keywords[label].get(tok,0) for tok in d['text'].lower().split()) for label in labels}
    correct += max(scores, key=scores.get) == d['label']
accuracy = correct / len(data)
out = {'format':'jarvis-keyword-net-v1','accuracy':accuracy,'labels':labels,'weights':keywords,'architecture':{'input':'lowercase token bag','hidden':'per-label sparse keyword weights','output':'intent + action policy'}}
assets = root/'app/src/main/assets'; assets.mkdir(parents=True, exist_ok=True)
(assets/'jarvis_model.json').write_text(json.dumps(out, indent=2, ensure_ascii=False))
print(f"accuracy={accuracy:.2%}; wrote {assets/'jarvis_model.json'}")
if accuracy < .8: raise SystemExit('accuracy below 80%')
