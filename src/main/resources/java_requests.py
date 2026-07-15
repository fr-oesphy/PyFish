import java
import json as _json

JavaHttpClient = java.type("fr.oesphy.pyfish.http.JavaHttpClient")
HashMap = java.type("java.util.HashMap")

class Response:
    def __init__(self, java_resp):
        self._raw = java_resp
        self.status_code = java_resp.status_code
        self.text = java_resp.getBody()
        self.headers = {}
        # requests exposes one combined value per key. The Java client keeps a list, so use
        # the first wire value until multi-value headers are supported by this shim.
        j_map = java_resp.headers
        if j_map:
            for k in j_map.keySet():
               val_list = j_map.get(k)
               if val_list and not val_list.isEmpty():
                   self.headers[str(k)] = str(val_list.get(0))
        self._json_cache = None

    def json(self):
        if not self._json_cache:
            self._json_cache = _json.loads(self.text)
        return self._json_cache

    def __repr__(self):
        return f"<Response [{self.status_code}]>"

def get(url, headers=None, **kwargs):
    h = _to_java_map(headers)
    resp = JavaHttpClient.get(url, h)
    return Response(resp)

def post(url, data=None, json=None, headers=None, **kwargs):
    h = _to_java_map(headers)
    body_str = None
    if json:
        body_str = _json.dumps(json)
        h.put("Content-Type", "application/json")
    elif data:
        if isinstance(data, dict):
            # Kept for requests compatibility; this subset does not yet percent-encode form
            # keys or values.
            pairs = []
            for k,v in data.items():
                pairs.append(f"{k}={v}")
            body_str = "&".join(pairs)
            h.put("Content-Type", "application/x-www-form-urlencoded")
        else:
            body_str = str(data)
            
    resp = JavaHttpClient.post(url, body_str, h)
    return Response(resp)
    
def put(url, data=None, json=None, headers=None, **kwargs):
    h = _to_java_map(headers)
    body_str = None
    if json:
        body_str = _json.dumps(json)
        h.put("Content-Type", "application/json")
    elif data:
       body_str = str(data)
       
    resp = JavaHttpClient.put(url, body_str, h)
    return Response(resp)

def delete(url, headers=None, **kwargs):
    h = _to_java_map(headers)
    resp = JavaHttpClient.delete(url, h)
    return Response(resp)

class FutureResponse:
    def __init__(self, future):
        self._future = future
        
    def is_done(self):
        return self._future.isDone()
        
    def result(self):
        """Blocking wait for the result"""
        return Response(self._future.get())

def get_async(url, headers=None, **kwargs):
    h = _to_java_map(headers)
    future = JavaHttpClient.getAsync(url, h)
    return FutureResponse(future)

def post_async(url, data=None, json=None, headers=None, **kwargs):
    h = _to_java_map(headers)
    body_str = None
    if json:
        body_str = _json.dumps(json)
        h.put("Content-Type", "application/json")
    elif data:
        if isinstance(data, dict):
             parts = []
             for k,v in data.items():
                 parts.append(f"{k}={v}")
             body_str = "&".join(parts)
             h.put("Content-Type", "application/x-www-form-urlencoded")
        else:
             body_str = str(data)
             
    future = JavaHttpClient.postAsync(url, body_str, h)
    return FutureResponse(future)

def put_async(url, data=None, json=None, headers=None, **kwargs):
    h = _to_java_map(headers)
    body_str = None
    if json:
        body_str = _json.dumps(json)
        h.put("Content-Type", "application/json")
    elif data:
       body_str = str(data)
       
    future = JavaHttpClient.putAsync(url, body_str, h)
    return FutureResponse(future)

def delete_async(url, headers=None, **kwargs):
    h = _to_java_map(headers)
    future = JavaHttpClient.deleteAsync(url, h)
    return FutureResponse(future)

def _to_java_map(headers):
    j_map = HashMap()
    if not headers:
        return j_map

    for key, value in headers.items():
        j_map.put(str(key), str(value))
    return j_map
