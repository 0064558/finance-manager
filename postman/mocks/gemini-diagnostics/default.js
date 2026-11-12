// Synthetic responses for TransactionGeminiSimulationTest; no real data or keys.
const http = require('http');
const json = (res, status, body) => {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
};
http.createServer(async (req, res) => {
  const path = new URL(req.url, 'http://localhost').pathname;
  if (req.method === 'GET' && path.startsWith('/__requests/')) {
    const model = decodeURIComponent(path.slice('/__requests/'.length));
    return json(res, 200, { count: (await pm.state.get('gemini:' + model)) || 0 });
  }
  const match = path.match(/\/models\/([^/]+):generateContent$/);
  if (req.method !== 'POST' || !match) {
    return json(res, 501, { error: { code: 501, message: 'Outside this fixture contract' } });
  }
  const model = match[1];
  const key = 'gemini:' + model;
  const count = ((await pm.state.get(key)) || 0) + 1;
  await pm.state.set(key, count);
  if (model.startsWith('disconnect-') && count === 1) {
    req.socket.destroy();
    return;
  }
  const code = model.startsWith('unavailable-') || (model.startsWith('recover-') && count === 1) ? 503
    : model.startsWith('rate-limited-') ? 429 : model.startsWith('invalid-') ? 400 : 200;
  if (model.startsWith('slow-')) await new Promise(resolve => setTimeout(resolve, 1500));
  if (code !== 200) {
    return json(res, code, { error: { code, status: code === 503 ? 'UNAVAILABLE'
      : code === 429 ? 'RESOURCE_EXHAUSTED' : 'INVALID_ARGUMENT', message: 'Fictional provider failure' } });
  }
  return json(res, 200, {
    candidates: [{ content: { role: 'model', parts: [{ text: JSON.stringify({
      status: 'SUGESTAO', lancamentos: [{ descricao: 'Almoço', tipo: 'DESPESA', valor: 47.90 }]
    }) }] }, finishReason: 'STOP', index: 0 }],
    modelVersion: model,
    usageMetadata: { promptTokenCount: 10, candidatesTokenCount: 10, totalTokenCount: 20 }
  });
}).listen(process.env.PORT || 4500);
